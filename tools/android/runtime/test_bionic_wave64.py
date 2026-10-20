"""Run production subgroup policy and ballot-address lowering with controlled inputs."""
from pathlib import Path
import unittest
import test_bionic_resource_reuse as reuse
from test_bionic_resource_reuse import method
ROOT = Path(__file__).resolve().parents[3]
GPU = ROOT / 'port/gpu/shadps4'

class Wave64Tests(unittest.TestCase):
    def test_profile_matches_required_size_only_for_supported_stages(self):
        source = (GPU / 'video_core/renderer_vulkan/vk_instance.h').read_text()
        bodies = '\n'.join(method(source, signature) for signature in (
            '    bool IsSubgroupSize64Supported()',
            '    bool IsRequiredSubgroupSize64ValidFor(', '    u32 ComputeSubgroupSize()'))
        reuse.ResourceReuseTests().run_cpp(r'''
#include <cassert>
#include <cstdint>
using u32=uint32_t;
namespace vk {enum class ShaderStageFlagBits:u32 {eVertex=1,eFragment=16,eCompute=32};
u32 operator&(u32 a,ShaderStageFlagBits b){return a&u32(b);}}
struct Instance {
    struct {bool subgroupSizeControl=true;} vk13_features;
    struct {u32 minSubgroupSize=32,maxSubgroupSize=64,requiredSubgroupSizeStages=32;} vk13_props;
    struct {u32 subgroupSize=32;} vk11_props;
''' + bodies + r'''
};
int main(){
    using S=vk::ShaderStageFlagBits;
    Instance i;
    assert(i.IsRequiredSubgroupSize64ValidFor(S::eCompute));
    assert(!i.IsRequiredSubgroupSize64ValidFor(S::eFragment));
    assert(!i.IsRequiredSubgroupSize64ValidFor(S::eVertex));
    assert(i.ComputeSubgroupSize()==64); // default-32 device must lower compute as required-64
    i.vk13_props.requiredSubgroupSizeStages=16;
    assert(i.ComputeSubgroupSize()==32 && i.IsRequiredSubgroupSize64ValidFor(S::eFragment));
    i.vk13_props.requiredSubgroupSizeStages=0;assert(i.ComputeSubgroupSize()==32);
    i.vk13_props.requiredSubgroupSizeStages=49;
    assert(i.IsRequiredSubgroupSize64ValidFor(S::eVertex));
    i.vk13_features.subgroupSizeControl=false;assert(i.ComputeSubgroupSize()==32);
    assert(!i.IsRequiredSubgroupSize64ValidFor(S::eCompute));
    i.vk13_features.subgroupSizeControl=true;i.vk13_props.maxSubgroupSize=32;
    assert(!i.IsRequiredSubgroupSize64ValidFor(S::eCompute) && i.ComputeSubgroupSize()==32);
    i.vk13_props.minSubgroupSize=128;i.vk13_props.maxSubgroupSize=128;
    assert(!i.IsRequiredSubgroupSize64ValidFor(S::eCompute));
    i.vk13_props.minSubgroupSize=64;i.vk13_props.maxSubgroupSize=64;i.vk11_props.subgroupSize=64;
    assert(i.ComputeSubgroupSize()==64);
}
''')

    def test_ballot_shared_loads_use_their_own_aligned_wave_pair(self):
        source = (GPU / 'shader_recompiler/ir/passes/lower_wave64_pass.cpp').read_text()
        start = source.index('            const IR::U32 offset =', source.index('if (inst->GetOpcode() == IR::Opcode::Ballot)'))
        end = source.index('        } else if (inst->GetOpcode() == IR::Opcode::ReadLane)',start)
        body = source[start:end]
        reuse.ResourceReuseTests().run_cpp(r'''
#include <array>
#include <cassert>
#include <cstdint>
#include <cstring>
using u32=uint32_t;using u64=uint64_t;
namespace IR {using U32=u32;using U64=u64;}
struct Emitter {
    std::array<u32,32> slots{};
    unsigned barriers=0;u32 loaded=0;
    u32 Imm32(u32 v){return v;}u32 IAdd(u32 a,u32 b){return a+b;}
    u32 ShiftLeftLogical(u32 a,u32 b){return a<<b;}u32 BitwiseAnd(u32 a,u32 b){return a&b;}
    void WriteShared(u32 bits,u32 value,u32 offset){assert(bits==32);slots.at(offset/4)=value;}
    void Barrier(){++barriers;}
    u64 LoadShared(u32 bits,bool sign,u32 offset){
        assert(bits==64 && !sign && offset%8==0 && offset+8<=slots.size()*4);
        loaded=offset;return u64(slots.at(offset/4))|(u64(slots.at(offset/4+1))<<32);
    }
};
struct Inst {u64 mask{};void ReplaceUsesWithAndRemove(u64 value){mask=value;}};
void lower(Emitter& ir,Inst* inst,u32 scratch_base,u32 subgroup_id,u32 mask_low){
''' + body + r'''
}
int main(){
    for(u32 base:{0u,8u,40u})for(u32 halves:{2u,4u,6u}){
        Emitter ir;
        for(u32 i=0;i<halves;++i)ir.slots.at(base/4+i)=0x11111111u*(i+1);
        for(u32 i=0;i<halves;++i){
            Inst result;lower(ir,&result,base,i,0x11111111u*(i+1));
            u32 even=i&~1u;
            u64 expected=u64(ir.slots.at(base/4+even))|(u64(ir.slots.at(base/4+even+1))<<32);
            assert(result.mask==expected && ir.loaded==base+even*4);
            assert(ir.loaded>=base && ir.loaded+8<=base+halves*4);
        }
        assert(ir.barriers==2*halves);
    }
}
''')

if __name__=='__main__':unittest.main()
