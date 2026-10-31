"""Exercise production constant-ring allocation/retirement with a controlled timeline."""
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
#include <cstdint>
#include <optional>
using u64=uint64_t;
struct Semaphore {
    u64 known=0, completed=0, current=10;
    unsigned queries=0;
    bool IsFree(u64 tick) const {return known>=tick;}
    u64 CurrentTick() const {return current;}
    void Refresh() {++queries;known=completed;}
};
struct Scheduler {
    Semaphore timeline;
    Semaphore* GetWorkSemaphore() {return &timeline;}
};
struct ConstantRing {
    static constexpr u64 Capacity=32ull<<20;
    Scheduler scheduler;
    u64 position=Capacity,retired=0;
    struct StampEntry {u64 end,tick;};
    std::array<StampEntry,1024> stamps{};
    std::atomic<u64> stamps_head{0},stamps_tail{0};
'''

MAIN = r'''
};
int main() {
    ConstantRing empty;
    for(unsigned i=0;i<10000;++i) assert(!empty.Allocate(16,16));
    assert(empty.scheduler.timeline.queries==0 && empty.position==empty.Capacity);

    ConstantRing recording;
    recording.stamps[0]={64,10}; // current tick is not submitted: a query cannot retire it
    recording.stamps_head=1;
    for(unsigned i=0;i<10000;++i) assert(!recording.Allocate(16,16));
    assert(recording.scheduler.timeline.queries==0 && recording.stamps_tail==0);

    ConstantRing cached;
    cached.stamps[0]={64,8};cached.stamps[1]={128,9};cached.stamps_head=2;
    cached.scheduler.timeline.known=8;
    assert(cached.Allocate(16,16)==0);
    assert(cached.scheduler.timeline.queries==0 && cached.retired==64);
    assert(cached.stamps_tail==1); // enough space: do not query the unfinished next stamp

    ConstantRing refresh;
    refresh.stamps[0]={64,8};refresh.stamps_head=1;
    refresh.scheduler.timeline.completed=8;
    assert(refresh.Allocate(16,16)==0);
    assert(refresh.scheduler.timeline.queries==1 && refresh.retired==64);

    ConstantRing partial;
    partial.stamps[0]={8,7};partial.stamps[1]={32,8};partial.stamps_head=2;
    partial.scheduler.timeline.known=7;partial.scheduler.timeline.completed=7;
    assert(!partial.Allocate(16,16)); // eight completed bytes cannot satisfy sixteen
    assert(partial.position==partial.Capacity && partial.retired==8 && partial.stamps_tail==1);
    assert(partial.scheduler.timeline.queries==1);
    partial.scheduler.timeline.completed=8;
    assert(partial.Allocate(16,16)==0 && partial.retired==32);

    ConstantRing wrap;
    wrap.position=2*wrap.Capacity-8;wrap.retired=wrap.Capacity-8;
    wrap.stamps_tail=1023;wrap.stamps_head=1025;
    wrap.stamps[1023]={wrap.Capacity,7};wrap.stamps[0]={wrap.Capacity+64,8};
    wrap.scheduler.timeline.known=7;wrap.scheduler.timeline.completed=8;
    assert(wrap.Allocate(16,16)==0); // wrap discards final eight bytes and respects alignment
    assert(wrap.position==2*wrap.Capacity+16 && wrap.stamps_tail==1025);
    assert(wrap.scheduler.timeline.queries==1);
}
'''


@unittest.skipUnless(CXX, "C++ compiler unavailable")
class ConstantRingTests(unittest.TestCase):
    def test_retire_only_queries_when_submitted_completion_can_free_needed_bytes(self):
        header = (ROOT / "port/gpu/shadps4/video_core/renderer_vulkan/vk_constant_ring.h").read_text()
        code = PREAMBLE + method(header, "std::optional<u64> Allocate(") + method(
            header, "void Retire(") + MAIN
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "ring.cpp"
            binary = Path(directory) / "ring"
            source.write_text(code)
            compile_result = subprocess.run([CXX, "-std=c++20", "-O2", "-Wall", "-Wextra",
                                             str(source), "-o", str(binary)],
                                            capture_output=True, text=True)
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)
            subprocess.run([str(binary)], check=True, timeout=10)


if __name__ == "__main__":
    unittest.main()
