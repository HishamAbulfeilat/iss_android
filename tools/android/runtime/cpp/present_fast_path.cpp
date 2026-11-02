// Compile the real presenter helpers; control Vulkan calls without a device.
#include <array>
#include <cassert>
#include <cstdlib>
#include <type_traits>
#include "video_core/renderer_vulkan/vk_common.h"
VULKAN_HPP_DEFAULT_DISPATCH_LOADER_DYNAMIC_STORAGE
using u32=uint32_t;using s32=int32_t;
template<class T> T H(uintptr_t n){if constexpr(std::is_pointer_v<T>)return reinterpret_cast<T>(n);else return T(n);}
static unsigned clears,barriers,blits;
static VkFilter filter;
static VkImageBlit observed;
static VKAPI_ATTR void VKAPI_CALL Clear(VkCommandBuffer,VkImage,VkImageLayout,
    const VkClearColorValue*,uint32_t count,const VkImageSubresourceRange*){assert(count==1);++clears;}
static VKAPI_ATTR void VKAPI_CALL Barrier(VkCommandBuffer,VkPipelineStageFlags,VkPipelineStageFlags,
    VkDependencyFlags,uint32_t count,const VkMemoryBarrier* memory,uint32_t,const VkBufferMemoryBarrier*,
    uint32_t,const VkImageMemoryBarrier*){
    assert(count==1 && memory->srcAccessMask==VK_ACCESS_TRANSFER_WRITE_BIT &&
           memory->dstAccessMask==VK_ACCESS_TRANSFER_WRITE_BIT);++barriers;
}
static VKAPI_ATTR void VKAPI_CALL Blit(VkCommandBuffer,VkImage,VkImageLayout,VkImage,VkImageLayout,
    uint32_t count,const VkImageBlit* region,VkFilter mode){assert(count==1);++blits;observed=*region;filter=mode;}
// PRODUCTION_METHODS
int main(int argc,char**){
    const bool fallback=argc>1;
    if(fallback)setenv("TLG_PRESENT_SKIP_CLEAR","0",1);else unsetenv("TLG_PRESENT_SKIP_CLEAR");
    auto& d=VULKAN_HPP_DEFAULT_DISPATCHER;
    d.vkCmdClearColorImage=Clear;d.vkCmdPipelineBarrier=Barrier;d.vkCmdBlitImage=Blit;
    vk::CommandBuffer command{H<VkCommandBuffer>(1)};
    vk::Image src{H<VkImage>(2)},dst{H<VkImage>(3)};
    const vk::ImageSubresourceRange range{vk::ImageAspectFlagBits::eColor,0,1,0,1};
    auto run=[&](s32 w,s32 h,u32 outw,u32 outh,bool expected_clear,VkFilter expected_filter){
        clears=barriers=blits=0;
        BlitPresentedFrame(command,src,dst,w,h,{outw,outh},range);
        assert(blits==1 && clears==unsigned(expected_clear) && barriers==clears && filter==expected_filter);
        assert(observed.srcOffsets[1].x==w && observed.srcOffsets[1].y==h);
    };
    run(1920,1080,1920,1080,fallback,fallback?VK_FILTER_LINEAR:VK_FILTER_NEAREST);
    run(1280,720,1920,1080,fallback,VK_FILTER_LINEAR);
    run(1920,1080,1280,720,fallback,VK_FILTER_LINEAR);
    run(1920,1080,2400,1080,true,fallback?VK_FILTER_LINEAR:VK_FILTER_NEAREST);
    assert(observed.dstOffsets[0].x==240 && observed.dstOffsets[1].x==2160);
    run(1920,1080,1920,1200,true,fallback?VK_FILTER_LINEAR:VK_FILTER_NEAREST);
    assert(observed.dstOffsets[0].y==60 && observed.dstOffsets[1].y==1140);
    run(1920,1080,1280,1024,true,VK_FILTER_LINEAR);
    assert(observed.dstOffsets[0].y==152 && observed.dstOffsets[1].y==872);
}
