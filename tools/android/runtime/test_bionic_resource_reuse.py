"""Run the production C++ pool and image-expansion methods with deterministic driver mocks.

The extracted method bodies are compiled unchanged; the mocks control timeline
completion and CPU texture writes without needing an Android Vulkan device.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[3]
CXX = shutil.which("c++")

def method(source, signature):
    start = source.index(signature)
    first = source.index("{", start)
    depth = 1
    pos = first + 1
    while depth:
        if source[pos] == "{":
            depth += 1
        elif source[pos] == "}":
            depth -= 1
        pos += 1
    return source[start:pos]

@unittest.skipUnless(CXX, "C++ compiler unavailable")
class ResourceReuseTests(unittest.TestCase):
    def run_cpp(self, code):
        with tempfile.TemporaryDirectory() as directory:
            src = Path(directory) / "test.cpp"
            binary = Path(directory) / "test"
            src.write_text(code)
            subprocess.run([CXX, "-std=c++20", "-O2", "-Wall", "-Wextra", str(src), "-o", str(binary)],
                           check=True, capture_output=True, text=True)
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)

    def test_pool_reuses_refreshed_ticks_without_reusing_inflight_resources(self):
        source = (ROOT / "port/gpu/shadps4/video_core/renderer_vulkan/vk_resource_pool.cpp").read_text()
        body = method(source, "std::size_t ResourcePool::CommitResource()")
        self.run_cpp(r'''
#include <cassert>
#include <cstdint>
#include <cstddef>
#include <optional>
#include <vector>
using u64 = uint64_t;
struct Semaphore {
    u64 known=0, completed=0, current=20;
    unsigned queries=0;
    u64 KnownGpuTick() const { return known; }
    u64 CurrentTick() const { return current; }
    void Refresh() { ++queries; known=completed; }
};
struct ResourcePool {
    Semaphore* work_semaphore;
    std::size_t hint_iterator=0;
    std::vector<u64> ticks;
    unsigned allocations=0;
    std::size_t CommitResource();
    std::size_t ManageOverflow() { auto old=ticks.size();ticks.resize(old+4);++allocations;return old; }
};
''' + body + r'''
int main() {
    Semaphore timeline{9,11,20};
    ResourcePool pool{&timeline,0,{10,11}};
    assert(pool.CommitResource()==0); // both became free after Refresh; no pool growth
    assert(timeline.queries==1 && pool.allocations==0 && pool.ticks.size()==2);
    assert(pool.ticks[0]==20);
    assert(pool.CommitResource()==1 && pool.ticks[1]==20);
    assert(timeline.queries==1); // the known completion already frees the second resource
    assert(pool.CommitResource()==2); // neither tick 20 has completed: growth is required
    assert(pool.allocations==1 && timeline.queries==2 && pool.ticks[2]==20);

    Semaphore wrapped{7,9,20};
    ResourcePool wrap{&wrapped,2,{3,8,10}};
    assert(wrap.CommitResource()==0); // free resource before the hint, without a query
    assert(wrapped.queries==0 && wrap.allocations==0);

    Semaphore late{0,5,20};
    ResourcePool boundary{&late,1,{5,10}};
    assert(boundary.CommitResource()==0); // only wrapped half becomes free after refresh
    assert(late.queries==1 && boundary.allocations==0 && boundary.ticks[1]==10);

    Semaphore empty_tick{};
    ResourcePool empty{&empty_tick,0,{}};
    assert(empty.CommitResource()==0 && empty.allocations==1);
    assert(empty_tick.queries==0);
}
''')

    def test_expansion_protects_guest_writes_before_refreshing_and_copying(self):
        source = (ROOT / "port/gpu/shadps4/video_core/texture_cache/texture_cache.cpp").read_text()
        body = method(source, "ImageId TextureCache::ExpandImage(")
        self.run_cpp(r'''
#include <cassert>
#include <array>
#include <algorithm>
using ImageId = unsigned;
struct ImageInfo { std::array<int,2> memory{2,3}; };
struct Image {
    ImageInfo info;
    std::array<int,2> pixels{0,0};
    struct { bool is_bound=false,is_target=false,needs_rebind=false; } binding;
    bool tracked=false,dirty=true;
};
struct Runtime;
struct Slots {
    std::array<Image,2> entries;
    ImageId insert(int, Runtime&, int, const ImageInfo& info) { entries[1].info=info;return 1; }
    Image& operator[](ImageId id) { return entries[id]; }
};
struct Runtime {
    void CopyImage(Image* src,Image* dst) { dst->pixels[0]=src->pixels[0]; }
};
struct TextureCache {
    int instance=0,slot_image_views=0;
    Runtime runtime;
    Slots slot_images;
    ImageId ExpandImage(const ImageInfo&,ImageId);
    void RegisterImage(ImageId) {}
    void TrackImage(ImageId id) { slot_images[id].tracked=true; }
    void RefreshImage(Image& image) {
        // Guest writes can race the uploads: the enlarged range must already be watched.
        assert(slot_images[1].tracked);
        if(image.dirty) { image.pixels=image.info.memory;image.dirty=false; }
    }
    void FreeImage(ImageId id) { slot_images[id].tracked=false; }
};
''' + body + r'''
int main() {
    TextureCache cpu;
    cpu.slot_images[0].pixels={1,0}; // stale source, guest has since written 2
    ImageInfo info;
    assert(cpu.ExpandImage(info,0)==1);
    assert((cpu.slot_images[1].pixels==std::array<int,2>{2,3}));
    assert(cpu.slot_images[1].tracked);

    TextureCache gpu;
    gpu.slot_images[0].pixels={7,0}; // current GPU content must survive expansion
    gpu.slot_images[0].dirty=false;
    gpu.ExpandImage(info,0);
    assert((gpu.slot_images[1].pixels==std::array<int,2>{7,3}));
}
''')

if __name__ == "__main__":
    unittest.main()
