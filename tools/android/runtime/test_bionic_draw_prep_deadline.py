"""Run the production preparation worker against controlled submission lifetimes."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]
CXX = shutil.which("c++")

PREAMBLE = r'''
#include <array>
#include <atomic>
#include <cassert>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <deque>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
using u64=uint64_t;using u32=uint32_t;
namespace Common {void SetCurrentThreadName(const char*) {}}
namespace TlgThreads {void MakeBackground() {}}
namespace Shader {enum class SwStage {Vertex};}
namespace AmdGpu {
struct Regs {int tag=0;};
struct PM4Header {struct {int opcode=1;} type3;int tag=0;};
struct Liverpool {
    static void ApplyGraphicsRegisterPacket(Regs& regs,const PM4Header* packet,u64& checksum) {
        regs.tag=packet->tag;++checksum;
    }
};
}
bool IsDirectDraw(int opcode) {return opcode==1;}
template<class F> void ForEachPacket(const std::vector<AmdGpu::PM4Header>& packets,F&& f) {
    for(const auto& packet:packets) f(&packet);
}
struct StageInput {
    int program=0;u64 hash=0;int hw_stage=0;u64 pgm_base=0;
    std::vector<u32>* flat=nullptr;
};
struct PrepWorker {std::vector<StageInput> stages;bool failed=false;std::map<int,int> infos;};
struct PipelineSelection {
    AmdGpu::Regs* regs=nullptr;PrepWorker* worker=nullptr;int draw_indirect_params=0,graphics_key=0;
    std::array<int*,1> infos{};int* fetch_shader=nullptr;
};
struct StageOutput {int program;u64 hash;int hw_stage;u64 pgm_base;const u32* flat;u32 flat_size;};
struct PreparedDraw {
    enum {Ready=1,Unavailable=2};
    u64 reg_checksum=0;int key=0;u32 num_stages=0;
    std::array<StageOutput,1> stages{};
    struct {bool valid=false;} vertex;
    std::atomic<int> state{0};
};
struct FlatArena {
    std::vector<std::unique_ptr<u32[]>> chunks;
    const u32* Append(const std::vector<u32>&) {return nullptr;}
};
template<class A,class B,class C> void PrepareResources(A&&,B&&,C&&) {}
template<class A,class B,class C,class D,class E>
void PrepareVertexInputs(A&&,B&&,C&&,D&&,E&&) {}
struct Cache {
    std::mutex mutex;std::condition_variable cv;
    unsigned calls=0, finish_after=0;std::vector<int> tags;
    std::atomic<bool>* finish=nullptr;
    bool PrepareGraphicsPipeline(PipelineSelection& sel) {
        std::scoped_lock lock{mutex};
        tags.push_back(sel.regs->tag);
        if(++calls==finish_after) finish->store(true,std::memory_order_release);
        cv.notify_all();
        return false; // cache miss is allowed; the normal rendering path remains responsible
    }
};
struct DrawPreparation {
    struct Submission {
        u64 seq=0,start_checksum=0;u32 num_draws=0;
        std::vector<AmdGpu::PM4Header> commands;
        std::unique_ptr<PreparedDraw[]> draws;
        std::vector<std::unique_ptr<u32[]>> flat_chunks;
        struct {void Apply(AmdGpu::Regs&) const {}} delta;
        std::atomic<bool> scanned{true},claimed{false},gpu_done{false};
    };
    Cache pipeline_cache;
    std::atomic<u64> gpu_seq{0};
    u64 tail_seq=0;bool baseline_ready=true;
    std::mutex mutex;std::condition_variable_any cv;
    std::unique_ptr<AmdGpu::Regs> tail_regs=std::make_unique<AmdGpu::Regs>();
    std::deque<std::shared_ptr<Submission>> submissions;
    void WorkerLoop(std::stop_token,u32);
};
'''

MAIN = r'''
std::shared_ptr<DrawPreparation::Submission> Submission(u64 seq,u32 count,int tag,bool done) {
    auto s=std::make_shared<DrawPreparation::Submission>();
    s->seq=seq;s->num_draws=count;s->gpu_done=done;
    s->draws=std::make_unique<PreparedDraw[]>(count);
    for(u32 i=0;i<count;++i) {AmdGpu::PM4Header p;p.tag=tag;s->commands.push_back(p);}
    return s;
}
void RunWorker(DrawPreparation& prep,unsigned required) {
    std::jthread worker([&](std::stop_token stop) {prep.WorkerLoop(stop,0);});
    {
        std::unique_lock lock{prep.pipeline_cache.mutex};
        assert(prep.pipeline_cache.cv.wait_for(lock,std::chrono::seconds(3),[&] {
            return prep.pipeline_cache.calls>=required;
        }));
    }
    worker.request_stop();prep.cv.notify_all();worker.join();
}
int main() {
    DrawPreparation late;
    auto expired=Submission(0,3,1,true), useful=Submission(1,2,2,false), empty=Submission(2,0,3,false);
    late.submissions={expired,useful,empty};
    late.pipeline_cache.finish=&useful->gpu_done;late.pipeline_cache.finish_after=2;
    RunWorker(late,2);
    assert(!expired->claimed && useful->claimed && !empty->claimed);
    assert(late.pipeline_cache.calls==2);
    for(int tag:late.pipeline_cache.tags) assert(tag==2);

    // The command processor finishes a claimed submission during its first prepared draw.
    // The worker must keep register replay order but skip the remaining expensive lookups.
    DrawPreparation overtaken;
    auto target=Submission(0,64,4,false);
    overtaken.submissions={target};overtaken.pipeline_cache.finish=&target->gpu_done;
    overtaken.pipeline_cache.finish_after=1;
    RunWorker(overtaken,1);
    assert(overtaken.pipeline_cache.calls==1);
    assert(target->draws[0].state==PreparedDraw::Unavailable);
    for(unsigned i=1;i<64;++i) assert(target->draws[i].state==0);
}
'''


@unittest.skipUnless(CXX, "C++ compiler unavailable")
class DrawPreparationDeadlineTests(unittest.TestCase):
    def test_worker_skips_finished_submissions_and_stops_preparing_when_overtaken(self):
        source = (ROOT / "port/gpu/shadps4/video_core/renderer_vulkan/vk_draw_prep.cpp").read_text()
        code = PREAMBLE + method(source, "void DrawPreparation::WorkerLoop(") + MAIN
        with tempfile.TemporaryDirectory() as directory:
            cpp = Path(directory) / "prep.cpp"
            binary = Path(directory) / "prep"
            cpp.write_text(code)
            result = subprocess.run([CXX, "-std=c++20", "-O2", "-Wall", "-Wextra", "-pthread",
                                     str(cpp), "-o", str(binary)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            subprocess.run([str(binary)], check=True, timeout=10)


if __name__ == "__main__":
    unittest.main()
