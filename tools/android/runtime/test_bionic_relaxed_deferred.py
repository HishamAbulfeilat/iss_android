"""Run production readback dispatch/publication against a controlled GPU and command queue."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "port/gpu/shadps4/video_core/buffer_cache/buffer_cache.cpp"
CXX = shutil.which("c++")

PREAMBLE = r'''
#include <algorithm>
#include <array>
#include <atomic>
#include <cassert>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <thread>
#include <tuple>
#include <vector>
#include "video_core/buffer_cache/readback_copy_pages.h"
using u8=uint8_t;using u64=uint64_t;using s64=int64_t;using VAddr=u64;
using namespace std::chrono_literals;
using VideoCore::ForEachReadbackCopyPageGroup;
enum class GpuReadbacksMode {Disabled,Relaxed,Precise};
struct {GpuReadbacksMode mode=GpuReadbacksMode::Relaxed;
    auto GetReadbacksMode() {return mode;}} EmulatorSettings;
namespace TlgToggle {enum {DeferredReadback}; bool Disabled(int) {return false;}}
bool ReadbackStatsEnabled() {return false;} s64 NowNs() {return 0;}
struct Counters {std::atomic<u64> count{},queue_ns{},flush_ns{},stale{},deferred_ns{},applied{};};
Counters& Readbacks() {static Counters c;return c;} void ReportReadbacks() {}
struct Queue {
    std::mutex mutex;std::condition_variable cv;
    std::deque<std::function<void()>> jobs;bool stop=false;
    std::thread::id owner;
    std::thread worker;
    Queue() {
        std::promise<void> ready;auto f=ready.get_future();
        worker=std::thread([this,&ready] {
            owner=std::this_thread::get_id();ready.set_value();
            std::unique_lock lock(mutex);
            for(;;) {
                cv.wait(lock,[&]{return stop||!jobs.empty();});
                if(stop&&jobs.empty()) return;
                auto job=std::move(jobs.front());jobs.pop_front();
                lock.unlock();job();lock.lock();
            }
        }); f.get();
    }
    ~Queue() {{std::lock_guard lock(mutex);stop=true;} cv.notify_one();worker.join();}
    void CheckOwner() {assert(std::this_thread::get_id()==owner);}
    template<bool wait,bool ordered,class F> void SendCommand(F&& func) {
        static_assert(wait && ordered);
        assert(std::this_thread::get_id()!=owner);
        auto job=std::make_shared<std::packaged_task<void()>>(std::forward<F>(func));
        auto done=job->get_future();
        {std::lock_guard lock(mutex);jobs.emplace_back([job]{(*job)();});}
        cv.notify_one();assert(done.wait_for(3s)==std::future_status::ready);done.get();
    }
};
struct Semaphore {
    std::mutex mutex;std::condition_variable cv;bool complete=false;unsigned waiters=0;
    Queue* queue;
    void Wait(u64 tick) {
        assert(tick==1);assert(std::this_thread::get_id()!=queue->owner);
        std::unique_lock lock(mutex);++waiters;cv.notify_all();
        assert(cv.wait_for(lock,3s,[&]{return complete;}));
    }
    void WaitForGuests(unsigned count) {
        std::unique_lock lock(mutex);
        assert(cv.wait_for(lock,2s,[&]{return waiters>=count;}));
    }
    void Complete() {std::lock_guard lock(mutex);complete=true;cv.notify_all();}
};
struct Scheduler {Semaphore timeline;auto GetWorkSemaphore() {return &timeline;}};
struct Tracker {
    Queue* queue;std::mutex mutex;bool gpu=true,cpu=false;unsigned marks=0;
    void MarkRegionAsCpuModified(VAddr,u64) {queue->CheckOwner();cpu=true;++marks;}
    void MarkRegionAsCpuModifiedUnlessGpu(VAddr,u64) {
        // The fixture exercises owner-thread publication in Relaxed.
        if(EmulatorSettings.mode==GpuReadbacksMode::Relaxed) queue->CheckOwner();
        std::lock_guard lock(mutex);if(!gpu) {cpu=true;++marks;}
    }
    template<class P,class W> bool ApplyGpuCopyIf(VAddr start,u64 size,P&& pred,W&& write) {
        if(EmulatorSettings.mode==GpuReadbacksMode::Relaxed) queue->CheckOwner();
        std::lock_guard lock(mutex);
        if(!pred()) return false;
        if(gpu) write(start,start+size);
        gpu=false;return true;
    }
};
struct Memory {
    std::array<u8,16> bytes{};unsigned publications=0;
    void TryWriteBacking(void* dst,const void* src,u64 size) {
        assert(reinterpret_cast<uintptr_t>(dst)==0x1000 && size==16);
        std::memcpy(bytes.data(),src,size);++publications;
    }
};
struct Buffer {};
struct BufferCache {
    struct Copy {u64 srcOffset=0,dstOffset=0,size=16;};
    struct DeferredReadback {
        std::array<u8,16> snapshot;
        struct Staging {u8* mapped;u64 offset=0;void Invalidate() {}} staging;
        VAddr arena_base=0x1000,start=0x1000,end=0x1010;u64 tick=1;
        std::atomic<bool> applied{},recovered{},stale{};std::mutex mutex;
        std::vector<Copy> copies{Copy{}};
        DeferredReadback():staging{snapshot.data()} {snapshot.fill(7);}
    };
    Queue* liverpool;Scheduler scheduler;Tracker* memory_tracker;Memory* memory;
    Buffer arena;std::shared_ptr<DeferredReadback> pending=std::make_shared<DeferredReadback>();
    bool empty=false;unsigned downloads=0,starts=0;
    BufferCache(Queue* q,Tracker* t,Memory* m):liverpool(q),memory_tracker(t),memory(m) {
        scheduler.timeline.queue=q;
    }
    auto GetReadbackWindow(VAddr,u64) {liverpool->CheckOwner();return std::tuple{&arena,0x1000ull,0x2000ull};}
    void DownloadMemory(const Buffer*,VAddr,u64) {
        liverpool->CheckOwner();++downloads;memory->bytes.fill(9);memory_tracker->gpu=false;
    }
    auto StartDeferredReadback(VAddr,u64) {
        liverpool->CheckOwner();++starts;
        return empty ? std::shared_ptr<DeferredReadback>{} : pending;
    }
    static bool DeferredReadbackEnabled();
    bool FinishDeferredReadback(DeferredReadback&,bool);
    void ReadMemory(VAddr,u64,bool,bool);
};
'''

MAIN = r'''
int main(int argc,char** argv) {
    assert(argc==2);std::string scenario=argv[1];
    setenv("TLG_DEFERRED_READBACK","1",1);unsetenv("TLG_HOT_PAGES");
    if(scenario=="disabled") EmulatorSettings.mode=GpuReadbacksMode::Disabled;
    if(scenario=="precise") EmulatorSettings.mode=GpuReadbacksMode::Precise;
    if(scenario=="off") setenv("TLG_DEFERRED_READBACK","0",1);
    if(scenario=="hot") setenv("TLG_HOT_PAGES","1",1);
    bool expect=EmulatorSettings.mode==GpuReadbacksMode::Precise ||
        (NATIVE_BIONIC && scenario!="disabled" && scenario!="off" && scenario!="hot");
    assert(BufferCache::DeferredReadbackEnabled()==expect);
    Queue queue;Tracker tracker{&queue};Memory memory;BufferCache cache{&queue,&tracker,&memory};
    const bool write=scenario!="read";
    if(scenario=="inline") {
        queue.SendCommand<true,true>([&]{cache.ReadMemory(0x1000,8,true,true);});
        assert(cache.starts==0 && cache.downloads==1 && tracker.cpu);return 0;
    }
    if(!expect) {
        cache.ReadMemory(0x1000,8,true,false);
        assert(cache.starts==0 && cache.downloads==1 && tracker.cpu);return 0;
    }
    if(scenario=="empty") {
        cache.empty=true;cache.ReadMemory(0x1000,8,true,false);
        assert(cache.starts==1 && cache.downloads==0 && tracker.cpu);return 0;
    }
    auto fault=std::async(std::launch::async,[&]{cache.ReadMemory(0x1000,8,write,false);});
    cache.scheduler.timeline.WaitForGuests(1);
    std::future<void> second;
    if(scenario=="join") {
        second=std::async(std::launch::async,[&]{cache.ReadMemory(0x1008,8,true,false);});
        cache.scheduler.timeline.WaitForGuests(2);
    }
    // Crucial regression: command recording progresses while the faulting guest waits.
    bool progressed=false;
    queue.SendCommand<true,true>([&] {
        progressed=true;
        if(scenario=="stale") cache.pending->stale.store(true);
    });
    assert(progressed && fault.wait_for(0s)!=std::future_status::ready);
    if(scenario=="race") {
        // Delay the publication request behind a GPU write that invalidates the snapshot
        // *after* the guest's semaphore completes. The real Finish must recheck staleness.
        queue.SendCommand<true,true>([&] {
            cache.scheduler.timeline.Complete();
            cache.pending->stale.store(true);
        });
    } else cache.scheduler.timeline.Complete();
    assert(fault.wait_for(3s)==std::future_status::ready);fault.get();
    if(second.valid()) {assert(second.wait_for(3s)==std::future_status::ready);second.get();}
    bool stale=scenario=="stale"||scenario=="race";
    assert(cache.downloads==unsigned(stale));
    assert(memory.publications==unsigned(!stale));
    assert(!tracker.gpu && tracker.cpu==write);
    for(u8 b:memory.bytes) assert(b==(stale?9:7));
}
'''


@unittest.skipUnless(CXX, "host C++ compiler unavailable")
class RelaxedDeferredTests(unittest.TestCase):
    def run_config(self, bionic):
        source = SOURCE.read_text()
        bodies = "\n".join(method(source, signature) for signature in (
            "bool BufferCache::DeferredReadbackEnabled()",
            "bool BufferCache::FinishDeferredReadback(DeferredReadback& readback, bool wait)",
            "void BufferCache::ReadMemory(VAddr device_addr, u64 size, bool is_write, bool assume_locks)"))
        with tempfile.TemporaryDirectory() as directory:
            src = Path(directory) / "readback.cpp"
            binary = Path(directory) / "readback"
            src.write_text(PREAMBLE + bodies + MAIN)
            flags = ["-D__ANDROID__=1", "-DISS_ANDROID_BIONIC=1"] if bionic else []
            result = subprocess.run([CXX, "-std=c++20", "-O2", "-pthread", *flags,
                                     f"-DNATIVE_BIONIC={int(bionic)}", "-I",
                                     str(ROOT / "port/gpu/shadps4"), str(src), "-o", str(binary)],
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            for case in ("success", "read", "join", "stale", "race", "inline", "empty",
                         "disabled", "off", "hot", "precise"):
                with self.subTest(bionic=bionic, case=case):
                    result = subprocess.run([str(binary), case], capture_output=True,
                                            text=True, timeout=10)
                    self.assertEqual(result.returncode, 0, result.stderr)

    def test_bionic_relaxed_does_not_block_gpu_recording_and_revalidates_copies(self):
        self.run_config(True)

    def test_other_platforms_keep_precise_only_policy(self):
        self.run_config(False)


if __name__ == "__main__":
    unittest.main()
