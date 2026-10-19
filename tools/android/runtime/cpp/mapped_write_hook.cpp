#include <array>
#include <cassert>
#include <vector>
#include <utility>
#include "video_core/renderer_vulkan/vk_common.h"
#include "video_core/buffer_cache/write_tick_tracker.h"
VULKAN_HPP_DEFAULT_DISPATCH_LOADER_DYNAMIC_STORAGE
using u64=uint64_t;using u32=uint32_t;using VAddr=u64;
namespace TlgToggle {
enum {CoarseReadTracking,AccessMemo};
bool Disabled(int){return false;}
}
namespace VideoCore {
enum class MemoryType {Sparse,DeviceLocal};
struct Buffer {MemoryType mem_type=MemoryType::Sparse;VAddr cpu_addr=0x10000000;};
}
namespace Vulkan {
enum class Access {Read,Write};
struct AddressRange {u64 resource,range_start,range_end;};
struct BarrierTracker {unsigned inserts=0;void InsertRange(const AddressRange&,Access){++inserts;}};
struct Scheduler {
    u64 tick=7;bool host_read_barrier=false;
    u64 CurrentTick()const{return tick;}
    // PRODUCTION_REQUIRE_BARRIER
};
struct Runtime {
    Scheduler scheduler;
    void (*buffer_write_hook)(void*,VAddr,u64,u64)=nullptr;
    void* buffer_write_context=nullptr;
    BarrierTracker barrier_tracker;
    vk::MemoryBarrier2 memory_barrier;
    struct AccessMemo {u64 resource,start,end;u32 epoch,access;};
    std::array<AccessMemo,512> access_memo{};u32 access_epoch=1;
    // PRODUCTION_HOOK_SETTER
    void AccessBuffer(const VideoCore::Buffer*,u64,u64,vk::PipelineStageFlags2,vk::AccessFlags2);
};
// PRODUCTION_ACCESS_BUFFER
}
int main() {
    Vulkan::Runtime runtime;
    struct Observed {u64 address,size,tick,calls=0;VideoCore::WriteTickTracker writes;};
    Observed observed;
    runtime.SetBufferWriteHook([](void* data,VAddr a,u64 n,u64 tick){
        auto& o=*static_cast<Observed*>(data);o.address=a;o.size=n;o.tick=tick;++o.calls;
        o.writes.Write(a,n,tick);
    },&observed);
    VideoCore::Buffer arena;
    for(auto access:{vk::AccessFlagBits2::eShaderWrite,vk::AccessFlagBits2::eTransferWrite,
                     vk::AccessFlagBits2::eMemoryWrite,vk::AccessFlagBits2::eTransformFeedbackWriteEXT}) {
        runtime.scheduler.host_read_barrier=false;
        runtime.AccessBuffer(&arena,64,128,vk::PipelineStageFlagBits2::eAllCommands,access);
        assert(runtime.scheduler.host_read_barrier);
        assert(observed.address==arena.cpu_addr+64 && observed.size==128);
        assert(observed.tick==runtime.scheduler.tick);
        ++runtime.scheduler.tick; // same memoized barrier range must still get a new write tick
    }
    assert(observed.calls==4);
    assert(observed.writes.Latest(arena.cpu_addr,4096)==10);
    runtime.scheduler.host_read_barrier=false;
    runtime.AccessBuffer(&arena,64,128,vk::PipelineStageFlagBits2::eAllGraphics,vk::AccessFlagBits2::eShaderRead);
    assert(!runtime.scheduler.host_read_barrier && observed.calls==4);
    VideoCore::Buffer staging{VideoCore::MemoryType::DeviceLocal,0};
    runtime.AccessBuffer(&staging,64,128,vk::PipelineStageFlagBits2::eCopy,vk::AccessFlagBits2::eTransferWrite);
    assert(!runtime.scheduler.host_read_barrier && observed.calls==4);
    runtime.SetBufferWriteHook(nullptr,nullptr);
    runtime.AccessBuffer(&arena,64,128,vk::PipelineStageFlagBits2::eComputeShader,vk::AccessFlagBits2::eShaderWrite);
    assert(!runtime.scheduler.host_read_barrier && observed.calls==4);
    assert(runtime.barrier_tracker.inserts>0); // ordinary device barrier tracking remains active
}
