#include <cassert>
#include <functional>
#include <random>
#include <span>
#include <utility>
#include <vector>
#include <cstdio>
#include "video_core/renderer_vulkan/buffer_barrier_scope.h"
VULKAN_HPP_DEFAULT_DISPATCH_LOADER_DYNAMIC_STORAGE
using u32=uint32_t;
enum chip {A6XX=6,A7XX=7,A8XX=8};
#define BITMASK_ENUM(type) unsigned
// TURNIP_REFERENCE

static std::vector<vk::MemoryBarrier2> observed;
static std::vector<vk::ImageMemoryBarrier2> observed_images;
static VKAPI_ATTR void VKAPI_CALL Barrier(VkCommandBuffer,const VkDependencyInfo* dep) {
    for(unsigned i=0;i<dep->memoryBarrierCount;++i) {
        vk::MemoryBarrier2 b{};b=dep->pMemoryBarriers[i];observed.push_back(b);
    }
    for(unsigned i=0;i<dep->imageMemoryBarrierCount;++i) {
        vk::ImageMemoryBarrier2 b{};b=dep->pImageMemoryBarriers[i];observed_images.push_back(b);
    }
}
namespace Vulkan {
struct Scheduler {
    bool host_read_barrier=false;
    vk::PipelineStageFlags2 host_read_src_stages{};vk::AccessFlags2 host_read_src_access{};
    vk::CommandBuffer current_cmdbuf{reinterpret_cast<VkCommandBuffer>(1)};
    unsigned ended=0;std::vector<std::function<void(vk::CommandBuffer)>> queued;
    void EndRendering(){++ended;}
    template<class T>auto RecordData(std::span<const T> s){return std::vector<T>(s.begin(),s.end());}
    template<class F>void Record(F f){queued.emplace_back(std::move(f));}
    void Run(){for(auto& f:queued)f(current_cmdbuf);queued.clear();}
    // PRODUCTION_REQUIRE
    void FlushHostReadBarrier();
};
// PRODUCTION_HOST_FLUSH
struct Runtime {
    Scheduler scheduler;
    vk::MemoryBarrier2 memory_barrier{
        .dstStageMask=vk::PipelineStageFlagBits2::eAllCommands,
        .dstAccessMask=vk::AccessFlagBits2::eMemoryRead|vk::AccessFlagBits2::eMemoryWrite};
    std::vector<vk::ImageMemoryBarrier2> image_barriers;
    struct Tracker {unsigned clears=0;void Clear(){++clears;}}barrier_tracker;
    unsigned joins=0;u32 access_epoch=1;
    void BeforeImageAccess(){++joins;}
    void FlushBarriers();
};
// PRODUCTION_FLUSH
}

static auto Access(vk::AccessFlags2 a,vk::PipelineStageFlags2 stages,bool gmem,bool sparse) {
    return vk2tu_access(VkAccessFlags2(a),0,VkPipelineStageFlags2(stages),false,gmem,sparse);
}
static tu_cache_state Apply(vk::MemoryBarrier2 b,unsigned pending,bool gmem,bool sparse) {
    tu_cache_state c{pending,0,0};
    tu_flush_for_access<A8XX>(&c,Access(b.srcAccessMask,b.srcStageMask,gmem,sparse),
                               Access(b.dstAccessMask,b.dstStageMask,gmem,sparse));
    return c;
}
static vk::MemoryBarrier2 Broad(vk::AccessFlags2 src) {
    return {.srcStageMask=vk::PipelineStageFlagBits2::eAllCommands,.srcAccessMask=src,
            .dstStageMask=vk::PipelineStageFlagBits2::eAllCommands,
            .dstAccessMask=vk::AccessFlagBits2::eMemoryRead|vk::AccessFlagBits2::eMemoryWrite};
}
int main() {
    using namespace Vulkan;
    VULKAN_HPP_DEFAULT_DISPATCHER.vkCmdPipelineBarrier2=Barrier;
    Runtime r;
    r.FlushBarriers();assert(r.scheduler.queued.empty() && r.access_epoch==1);
    r.memory_barrier.srcStageMask=vk::PipelineStageFlagBits2::eComputeShader;
    r.memory_barrier.srcAccessMask=vk::AccessFlagBits2::eShaderWrite;
    const vk::ImageMemoryBarrier2 image{
        .srcStageMask=vk::PipelineStageFlagBits2::eLateFragmentTests,
        .srcAccessMask=vk::AccessFlagBits2::eDepthStencilAttachmentWrite,
        .dstStageMask=vk::PipelineStageFlagBits2::eFragmentShader,
        .dstAccessMask=vk::AccessFlagBits2::eShaderRead,
        .oldLayout=vk::ImageLayout::eDepthStencilAttachmentOptimal,
        .newLayout=vk::ImageLayout::eShaderReadOnlyOptimal,
        .image=vk::Image{reinterpret_cast<VkImage>(3)},
        .subresourceRange={vk::ImageAspectFlagBits::eDepth,0,1,0,1}};
    r.image_barriers.push_back(image);r.FlushBarriers();
    assert(observed.empty() && r.scheduler.queued.size()==1);
    assert(r.barrier_tracker.clears==1 && r.access_epoch==2 && r.image_barriers.empty());
    assert(!r.memory_barrier.srcStageMask && !r.memory_barrier.srcAccessMask);
    // The callback must own the snapshot even after the runtime changes again.
    r.memory_barrier.srcAccessMask=vk::AccessFlagBits2::eTransferWrite;
    r.image_barriers.push_back({});r.scheduler.Run();
    assert(observed.size()==1 && observed_images.size()==1 && observed_images[0]==image);
    assert(observed[0].srcAccessMask==vk::AccessFlagBits2::eShaderWrite);
#if defined(__ANDROID__) && defined(ISS_ANDROID_BIONIC)
    assert(observed[0].dstAccessMask==BufferAccessScope);
#else
    assert(observed[0].dstAccessMask==(vk::AccessFlagBits2::eMemoryRead|vk::AccessFlagBits2::eMemoryWrite));
#endif
    for(auto src:{vk::AccessFlagBits2::eMemoryWrite,vk::AccessFlagBits2::eColorAttachmentWrite,
                  vk::AccessFlagBits2::eDepthStencilAttachmentWrite,vk::AccessFlagBits2::eTransformFeedbackWriteEXT}) {
        auto b=Broad(src);assert(ScopeBufferBarrier(b)==b);
    }
    observed.clear();
    Scheduler s;s.FlushHostReadBarrier();assert(observed.empty());
    s.RequireHostReadBarrier(vk::PipelineStageFlagBits2::eComputeShader,vk::AccessFlagBits2::eShaderWrite);
    s.RequireHostReadBarrier(vk::PipelineStageFlagBits2::eCopy,vk::AccessFlagBits2::eTransferWrite);
    s.FlushHostReadBarrier();s.FlushHostReadBarrier();assert(observed.size()==1);
    assert(observed[0].srcStageMask==(vk::PipelineStageFlagBits2::eComputeShader|vk::PipelineStageFlagBits2::eCopy));
    assert(observed[0].srcAccessMask==(vk::AccessFlagBits2::eShaderWrite|vk::AccessFlagBits2::eTransferWrite));
    assert(observed[0].dstStageMask==vk::PipelineStageFlagBits2::eHost && observed[0].dstAccessMask==vk::AccessFlagBits2::eHostRead);
    assert(!s.host_read_barrier && !s.host_read_src_stages && !s.host_read_src_access);
    s.RequireHostReadBarrier();s.FlushHostReadBarrier();
    assert(observed.back().srcAccessMask==vk::AccessFlagBits2::eMemoryWrite);
    assert(observed.back().srcStageMask==vk::PipelineStageFlagBits2::eAllCommands);
    s.RequireHostReadBarrier(vk::PipelineStageFlagBits2::eComputeShader,vk::AccessFlagBits2::eShaderWrite);
    s.FlushHostReadBarrier();assert(observed.size()==3); // previous submission did not leak
    assert(observed.back().srcAccessMask==vk::AccessFlagBits2::eShaderWrite);

    std::mt19937 random(22102026);
    const vk::AccessFlags2 sources[]={vk::AccessFlagBits2::eShaderWrite,vk::AccessFlagBits2::eTransferWrite,
        vk::AccessFlagBits2::eShaderWrite|vk::AccessFlagBits2::eTransferWrite};
    const vk::AccessFlags2 consumers[]={vk::AccessFlagBits2::eIndirectCommandRead,vk::AccessFlagBits2::eIndexRead,
        vk::AccessFlagBits2::eVertexAttributeRead,vk::AccessFlagBits2::eUniformRead,
        vk::AccessFlagBits2::eShaderRead,vk::AccessFlagBits2::eShaderWrite,
        vk::AccessFlagBits2::eTransferRead,vk::AccessFlagBits2::eTransferWrite};
    for(bool gmem:{false,true})for(bool sparse:{false,true})for(auto src:sources) {
        auto old=Broad(src),narrow=ScopeBufferBarrier(old);
        auto a=Apply(old,0,gmem,sparse),b=Apply(narrow,0,gmem,sparse);
#if defined(__ANDROID__) && defined(ISS_ANDROID_BIONIC)
        if(!gmem) {
            assert(a.flush_bits&TU_CMD_FLAG_CCU_INVALIDATE_DEPTH);
            assert(!(b.flush_bits&(TU_CMD_FLAG_CCU_INVALIDATE_DEPTH|TU_CMD_FLAG_CCU_CLEAN_DEPTH)));
        }
        // A later indirect/copy/vertex consumer is covered even after the tracker clears.
        for(unsigned iteration=0;iteration<1000;++iteration) {
            const unsigned pending=random()&0xffff;
            const auto batch=Apply(narrow,pending,gmem,sparse);
            for(auto consumer:consumers) {
                auto precise=old;precise.dstAccessMask=consumer;
                const auto required=Apply(precise,pending,gmem,sparse);
                assert(!(required.flush_bits&~batch.flush_bits));
            }
            assert(!(batch.flush_bits&~Apply(old,pending,gmem,sparse).flush_bits));
        }
#else
        assert(old==narrow && a.flush_bits==b.flush_bits);
#endif
    }
    auto host_old=observed[1],host_new=observed[2];
    for(bool gmem:{false,true})for(bool sparse:{false,true}) {
        auto old=Apply(host_old,0,gmem,sparse),typed=Apply(host_new,0,gmem,sparse);
        assert(typed.flush_bits&TU_CMD_FLAG_CACHE_CLEAN);
        assert(!(typed.flush_bits&(TU_CMD_FLAG_CCU_CLEAN_DEPTH|TU_CMD_FLAG_CCU_CLEAN_COLOR|TU_CMD_FLAG_WAIT_MEM_WRITES)));
        assert(old.flush_bits&TU_CMD_FLAG_WAIT_MEM_WRITES);
        if(!gmem)assert((old.flush_bits&(TU_CMD_FLAG_CCU_CLEAN_DEPTH|TU_CMD_FLAG_CCU_CLEAN_COLOR))==
            (TU_CMD_FLAG_CCU_CLEAN_DEPTH|TU_CMD_FLAG_CCU_CLEAN_COLOR));
        // Real earlier dirty attachment work must still be cleaned for the host.
        assert((Apply(host_new,TU_CMD_FLAG_ALL_CLEAN,gmem,sparse).flush_bits&TU_CMD_FLAG_ALL_CLEAN)==TU_CMD_FLAG_ALL_CLEAN);
        auto transfer=host_new;transfer.srcStageMask=vk::PipelineStageFlagBits2::eCopy;
        transfer.srcAccessMask=vk::AccessFlagBits2::eTransferWrite;
        if(!gmem)assert(Apply(transfer,0,gmem,sparse).flush_bits&TU_CMD_FLAG_CCU_CLEAN_COLOR);
        auto mixed=Apply(observed[0],0,gmem,sparse);
        assert(mixed.flush_bits&TU_CMD_FLAG_CACHE_CLEAN);
        if(!gmem)assert(mixed.flush_bits&TU_CMD_FLAG_CCU_CLEAN_COLOR);
    }
}
