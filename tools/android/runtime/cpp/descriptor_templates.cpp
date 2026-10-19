// The production methods are compiled with actual Vulkan-Hpp types and controlled driver calls.
#include <cassert>
#include <atomic>
#include <array>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include "video_core/renderer_vulkan/vk_common.h"
#include "video_core/renderer_vulkan/descriptor_template_data.h"
#include "video_core/renderer_vulkan/graphics_descriptor_state.h"
VULKAN_HPP_DEFAULT_DISPATCH_LOADER_DYNAMIC_STORAGE
using u32=uint32_t;using u64=uint64_t;
namespace boost::container {template<class T,size_t N> using small_vector=std::vector<T>;}
namespace Shader {struct PushData {std::array<u32,32> words{};};}
namespace IssBionicPerf {
std::atomic<u64> descriptor_template_calls{},descriptor_template_bytes{},descriptor_legacy_calls{},descriptor_graphics_reused{},descriptor_graphics_missed{},descriptor_graphics_raw_bypassed{};
bool Enabled(){return true;}
}
#define LOG_WARNING(...) ((void)0)
template<class T> T H(uintptr_t x) {
    if constexpr(std::is_pointer_v<T>) return reinterpret_cast<T>(x);
    else return static_cast<T>(x);
}
static unsigned pushed,legacy,constants,created,destroyed,sets;
static bool fail_create;
static bool expect_compute,cache_mode;
static std::vector<u32> constant_values;
static u64 observed;
static std::vector<VkDescriptorUpdateTemplateEntry> entries;
static VKAPI_ATTR VkResult VKAPI_CALL CreateLayout(VkDevice,const VkPipelineLayoutCreateInfo*,
    const VkAllocationCallbacks*,VkPipelineLayout* out){*out=H<VkPipelineLayout>(1);return VK_SUCCESS;}
static VKAPI_ATTR void VKAPI_CALL DestroyLayout(VkDevice,VkPipelineLayout,const VkAllocationCallbacks*){}
static VKAPI_ATTR VkResult VKAPI_CALL CreateSet(VkDevice,const VkDescriptorSetLayoutCreateInfo*,
    const VkAllocationCallbacks*,VkDescriptorSetLayout* out){*out=H<VkDescriptorSetLayout>(2);return VK_SUCCESS;}
static VKAPI_ATTR void VKAPI_CALL DestroySet(VkDevice,VkDescriptorSetLayout,const VkAllocationCallbacks*){}
static VKAPI_ATTR VkResult VKAPI_CALL CreateTemplate(VkDevice,const VkDescriptorUpdateTemplateCreateInfo* ci,
    const VkAllocationCallbacks*,VkDescriptorUpdateTemplate* out) {
    ++created;
    assert(ci->templateType==VK_DESCRIPTOR_UPDATE_TEMPLATE_TYPE_PUSH_DESCRIPTORS_KHR);
    assert(ci->pipelineLayout==H<VkPipelineLayout>(1) && ci->set==0);
    assert(ci->pipelineBindPoint==(expect_compute?VK_PIPELINE_BIND_POINT_COMPUTE:VK_PIPELINE_BIND_POINT_GRAPHICS));
    entries.assign(ci->pDescriptorUpdateEntries,ci->pDescriptorUpdateEntries+ci->descriptorUpdateEntryCount);
    if(fail_create) return VK_ERROR_OUT_OF_HOST_MEMORY;
    *out=H<VkDescriptorUpdateTemplate>(3);return VK_SUCCESS;
}
static VKAPI_ATTR void VKAPI_CALL DestroyTemplate(VkDevice,VkDescriptorUpdateTemplate,const VkAllocationCallbacks*){++destroyed;}
static VKAPI_ATTR void VKAPI_CALL PushTemplate(VkCommandBuffer,VkDescriptorUpdateTemplate update,
    VkPipelineLayout layout,uint32_t set,const void* payload) {
    ++pushed;assert(update==H<VkDescriptorUpdateTemplate>(3) && layout==H<VkPipelineLayout>(1) && set==0);
    assert(reinterpret_cast<uintptr_t>(payload)%8==0);
    for(const auto& e:entries) {
        const auto* data=static_cast<const std::byte*>(payload)+e.offset;
        if(e.descriptorType==VK_DESCRIPTOR_TYPE_STORAGE_BUFFER) {
            VkDescriptorBufferInfo info{};std::memcpy(&info,data,sizeof(info));
            observed=info.offset;assert(info.buffer==H<VkBuffer>(101) && info.range==512);
        } else {
            for(unsigned i=0;i<e.descriptorCount;++i) {
                VkDescriptorImageInfo info{};std::memcpy(&info,data+i*e.stride,sizeof(info));
                if(!cache_mode)assert(info.imageView==H<VkImageView>(201+i));
            }
        }
    }
}
static VKAPI_ATTR void VKAPI_CALL PushConstants(VkCommandBuffer,VkPipelineLayout,VkShaderStageFlags,
    uint32_t,uint32_t size,const void* data){++constants;assert(size==128 && *static_cast<const u32*>(data)==37);constant_values.push_back(static_cast<const u32*>(data)[1]);}
static VKAPI_ATTR void VKAPI_CALL PushLegacy(VkCommandBuffer,VkPipelineBindPoint,VkPipelineLayout,
    uint32_t,uint32_t count,const VkWriteDescriptorSet* writes){++legacy;assert(count && writes[0].pBufferInfo);observed=writes[0].pBufferInfo->offset;}
static VKAPI_ATTR void VKAPI_CALL UpdateSets(VkDevice,uint32_t count,const VkWriteDescriptorSet*,uint32_t,const VkCopyDescriptorSet*){assert(count);}
static VKAPI_ATTR void VKAPI_CALL BindSets(VkCommandBuffer,VkPipelineBindPoint,VkPipelineLayout,uint32_t,
    uint32_t count,const VkDescriptorSet*,uint32_t,const uint32_t*){assert(count==1);++sets;}

static VKAPI_ATTR VkResult VKAPI_CALL Begin(VkCommandBuffer,const VkCommandBufferBeginInfo*){return VK_SUCCESS;}
void Check(vk::Result r){assert(r==vk::Result::eSuccess);}
namespace Vulkan {
constexpr auto AllGraphicsStageBits=vk::ShaderStageFlagBits::eVertex|vk::ShaderStageFlagBits::eFragment;
struct Instance {vk::Device GetDevice()const{return vk::Device{H<VkDevice>(1)};}};
struct Scheduler {
    bool deferred=true,recording_threaded=true,direct_mode=false,owner_invalidates=false;
    unsigned owner_checks=0;
    std::atomic<unsigned> direct_recordings{};
    GraphicsDescriptorState graphics_descriptor_state;
    bool graphics_descriptor_reuse_allowed=true;
    std::thread::id owner=std::this_thread::get_id();
    struct Vertex {void Invalidate(){}} vertex_input_state;
    struct Dynamic {void Invalidate(){}} dynamic_state;
    vk::CommandBuffer current_cmdbuf{H<VkCommandBuffer>(1)};
    struct Pool{vk::CommandBuffer Commit(){return vk::CommandBuffer{H<VkCommandBuffer>(1)};}} command_pool;
    void CheckOwner(void*) {
        assert(std::this_thread::get_id()==owner);++owner_checks;
        if(owner_invalidates){
            owner_invalidates=false;
            // Simulate an owner hook inserting a helper binding before this command.
            if(IsRecordingDeferred())commands.emplace_back([this](vk::CommandBuffer){InvalidateGraphicsDescriptors();});
            else InvalidateGraphicsDescriptors();
        }
    }
    void SyncRecording(){Drain();}
    static void TraceDirectRecording(void*){}
    bool ReuseGraphicsDescriptors(u64,std::span<const u64>);
    void AllocateWorkerCommandBuffers();
    // PRODUCTION_INLINE_METHODS
    bool switch_direct=false;
    std::vector<std::unique_ptr<std::byte[]>> storage;
    std::vector<std::function<void(vk::CommandBuffer)>> commands;
    bool IsRecordingDeferred()const{return deferred&&!direct_mode;}
    void ReserveRecordData(size_t){}
    template<class T> std::span<const T> RecordData(std::span<const T> data) {
        if(switch_direct)deferred=false; // ReserveRecordData may switch an oversized chunk to direct
        if(!IsRecordingDeferred()) return data;
        auto memory=std::make_unique<std::byte[]>(data.size_bytes());
        std::memcpy(memory.get(),data.data(),data.size_bytes());
        auto* ptr=reinterpret_cast<const T*>(memory.get());storage.push_back(std::move(memory));
        return {ptr,data.size()};
    }
    template<class F> void Record(F f) {
        CheckOwner(nullptr);
        if(IsRecordingDeferred()) commands.emplace_back(std::move(f));else f(vk::CommandBuffer{H<VkCommandBuffer>(1)});
    }
    void Drain(){for(auto& f:commands)f(vk::CommandBuffer{H<VkCommandBuffer>(1)});commands.clear();storage.clear();}
};
struct Heap {vk::DescriptorSet Commit(vk::DescriptorSetLayout)const{return vk::DescriptorSet{H<VkDescriptorSet>(7)};}};
struct Pipeline {
    Instance instance;Scheduler& scheduler;Heap desc_heap;
    vk::UniquePipelineLayout pipeline_layout;
    vk::UniqueDescriptorSetLayout desc_layout;
    DescriptorTemplateData descriptor_template_data;
    vk::UniqueDescriptorUpdateTemplate descriptor_update_template;
    PFN_vkCmdPushDescriptorSetWithTemplateKHR push_descriptor_template{};
    u64 descriptor_template_key=0;
    bool uses_push_descriptors=true,compute=false;
    using DescriptorWrites=std::vector<vk::WriteDescriptorSet>;
    explicit Pipeline(Scheduler& s):scheduler(s) {
        pipeline_layout=std::move(instance.GetDevice().createPipelineLayoutUnique({}).value);
        desc_layout=std::move(instance.GetDevice().createDescriptorSetLayoutUnique({}).value);
    }
    bool IsCompute()const{return compute;}
    void BuildDescriptorTemplate(std::span<const vk::DescriptorSetLayoutBinding>);
    void BindResources(DescriptorWrites&,const Shader::PushData&)const;
};
// PRODUCTION_SCHEDULER
// PRODUCTION_METHODS
}

int main(int argc,char** argv) {
    std::string mode=argc>1?argv[1]:"deferred";
    cache_mode=mode.starts_with("reuse");
    if(mode=="reuseoff")setenv("TLG_DESCRIPTOR_REUSE","0",1);
    unsetenv("TLG_DESCRIPTOR_TEMPLATES");if(mode=="off"||mode=="offdirect")setenv("TLG_DESCRIPTOR_TEMPLATES","0",1);
    auto& d=VULKAN_HPP_DEFAULT_DISPATCHER;
    d.vkCreatePipelineLayout=CreateLayout;d.vkDestroyPipelineLayout=DestroyLayout;
    d.vkCreateDescriptorSetLayout=CreateSet;d.vkDestroyDescriptorSetLayout=DestroySet;
    d.vkCreateDescriptorUpdateTemplate=CreateTemplate;d.vkDestroyDescriptorUpdateTemplate=DestroyTemplate;
    d.vkCmdPushDescriptorSetWithTemplateKHR=mode=="missing"?nullptr:PushTemplate;
    d.vkCmdPushConstants=PushConstants;d.vkCmdPushDescriptorSetKHR=PushLegacy;
    d.vkBeginCommandBuffer=Begin;
    d.vkUpdateDescriptorSets=UpdateSets;d.vkCmdBindDescriptorSets=BindSets;
    fail_create=mode=="failure";
    expect_compute=mode=="compute" || mode=="reusecompute";
    Vulkan::Scheduler scheduler;scheduler.deferred=mode!="direct"&&mode!="offdirect"&&mode!="reusedirect";
    scheduler.switch_direct=mode=="switchdirect"||mode=="reusechunk";
    {
        Vulkan::Pipeline pipeline{scheduler};pipeline.uses_push_descriptors=mode!="ordinary";
        pipeline.compute=expect_compute;
        std::array<vk::DescriptorSetLayoutBinding,2> bindings{{
            {.binding=0,.descriptorType=vk::DescriptorType::eStorageBuffer,.descriptorCount=1},
            {.binding=3,.descriptorType=vk::DescriptorType::eSampledImage,.descriptorCount=2}}};
        if(mode=="unsupported")bindings[1].descriptorType=vk::DescriptorType::eUniformBufferDynamic;
        pipeline.BuildDescriptorTemplate(mode=="empty"?std::span<const vk::DescriptorSetLayoutBinding>{}:std::span<const vk::DescriptorSetLayoutBinding>{bindings});
        vk::DescriptorBufferInfo buffer{.buffer=vk::Buffer{H<VkBuffer>(101)},.offset=123,.range=512};
        std::array<vk::DescriptorImageInfo,2> images{{
            {.imageView=vk::ImageView{H<VkImageView>(201)},.imageLayout=vk::ImageLayout::eGeneral},
            {.imageView=vk::ImageView{H<VkImageView>(202)},.imageLayout=vk::ImageLayout::eGeneral}}};
        Vulkan::Pipeline::DescriptorWrites writes{
            {.dstBinding=0,.descriptorCount=1,.descriptorType=vk::DescriptorType::eStorageBuffer,.pBufferInfo=&buffer},
            {.dstBinding=3,.descriptorCount=2,.descriptorType=vk::DescriptorType::eSampledImage,.pImageInfo=images.data()}};
        if(mode=="mismatch")writes.pop_back();
        if(mode=="empty")writes.clear();
        Shader::PushData push;push.words[0]=37;
        if(cache_mode) {
            const bool cached=mode!="reusecompute" && mode!="reuseoff";
            unsigned expected_pushed=0,expected_constants=0;
            auto emit=[&](Vulkan::Pipeline& p,bool hit=false){
                push.words[1]=++expected_constants;p.BindResources(writes,push);
                if(!hit)++expected_pushed;
            };
            auto check=[&]{
                if(mode=="reuseworker" && scheduler.IsRecordingDeferred())std::thread([&]{scheduler.Drain();}).join();
                else scheduler.Drain();
                assert(pushed==expected_pushed && constants==expected_constants);
                for(unsigned i=0;i<constant_values.size();++i)assert(constant_values[i]==i+1);};
            emit(pipeline);emit(pipeline,cached);
            buffer.offset=124;emit(pipeline);
            images[0].imageView=vk::ImageView{H<VkImageView>(301)};emit(pipeline);
            images[0].imageLayout=vk::ImageLayout::eShaderReadOnlyOptimal;emit(pipeline);
            emit(pipeline,cached);check();
            // Cached payload ownership outlives the recording chunk's storage.
            emit(pipeline,cached);check();
            if(cached) {
                const auto old_key=pipeline.descriptor_template_key;
                {Vulkan::Pipeline other{scheduler};other.BuildDescriptorTemplate(bindings);
                 assert(other.descriptor_template_key!=old_key);
                 emit(other);emit(pipeline);check();}
                {Vulkan::Pipeline replacement{scheduler};replacement.BuildDescriptorTemplate(bindings);
                 assert(replacement.descriptor_template_key!=old_key);
                 emit(replacement);emit(pipeline);check();}
                // Compute descriptors occupy an independent bind point.
                {Vulkan::Pipeline compute{scheduler};compute.compute=true;expect_compute=true;
                 compute.BuildDescriptorTemplate(bindings);expect_compute=false;
                 emit(compute);emit(pipeline,true);check();}
                scheduler.InvalidateVertexInput();
                scheduler.Record([&](vk::CommandBuffer){scheduler.InvalidateGraphicsDescriptors();});
                emit(pipeline);check();
                scheduler.CommandBuffer();emit(pipeline);emit(pipeline);check();
                assert(IssBionicPerf::descriptor_graphics_raw_bypassed==2);
                scheduler.AllocateWorkerCommandBuffers();emit(pipeline);emit(pipeline,true);check();
                scheduler.owner_invalidates=true;emit(pipeline);check();
                // A failed pack uses legacy descriptors and invalidates graphics state.
                auto full=writes;writes.pop_back();push.words[1]=++expected_constants;
                pipeline.BindResources(writes,push);writes=full;emit(pipeline);check();assert(legacy==1);
                writes.clear();push.words[1]=++expected_constants;pipeline.BindResources(writes,push);
                writes=full;emit(pipeline,true);check();
                Vulkan::Pipeline ordinary{scheduler};ordinary.uses_push_descriptors=false;
                ordinary.BuildDescriptorTemplate(bindings);push.words[1]=++expected_constants;
                ordinary.BindResources(writes,push);emit(pipeline);check();assert(sets==1);
                assert(IssBionicPerf::descriptor_graphics_reused==6);
            }else assert(IssBionicPerf::descriptor_graphics_reused==0);
            assert(IssBionicPerf::descriptor_template_calls==expected_pushed);
            if(mode!="reusecompute")assert(scheduler.owner_checks>0);return 0;
        }
        pipeline.BindResources(writes,push);
        bool templated=mode=="deferred"||mode=="direct"||mode=="compute"||mode=="switchdirect";
        if(scheduler.deferred) {
            assert(pushed==0 && constants==0);
            assert(scheduler.commands.size()==(templated||mode=="empty"?1:2));
        }
        buffer.offset=999;images[0].imageView=vk::ImageView{H<VkImageView>(999)};push.words[0]=0;
        scheduler.Drain();
        assert(constants==1);
        assert(pushed==unsigned(templated));
        assert(legacy==unsigned(!templated && mode!="ordinary" && mode!="empty"));
        assert(sets==unsigned(mode=="ordinary"));
        if(mode!="ordinary" && mode!="empty")assert(observed==123);
        assert(IssBionicPerf::descriptor_template_calls==unsigned(templated));
        assert(IssBionicPerf::descriptor_legacy_calls==unsigned(!templated && mode!="empty"));
    }
    assert(destroyed==unsigned(created && !fail_create));
}
