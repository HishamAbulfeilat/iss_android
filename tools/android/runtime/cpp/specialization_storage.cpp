// SPDX-License-Identifier: GPL-2.0-or-later
#include <algorithm>
#include <array>
#include <atomic>
#include <bitset>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <new>
#include <optional>
#include <random>
#include <thread>
#include <unordered_map>
#include <vector>
using u8=uint8_t;using s8=int8_t;using u32=uint32_t;using u64=uint64_t;
static std::atomic<size_t> allocations{};
void* operator new(size_t n){++allocations;if(auto* p=std::malloc(n?n:1))return p;throw std::bad_alloc{};}
void operator delete(void* p)noexcept{std::free(p);}
void operator delete(void* p,size_t)noexcept{std::free(p);}
// Resource mocks exercise the production constructor's branches. Container aliasing
// here is only for semantic tests; allocation assertions target std::vector in fetch.
namespace boost::container {template<class T,size_t>using small_vector=std::vector<T>;}
namespace Serialization {struct Archive;}
namespace AmdGpu {
enum class NumberClass{Float,Int};enum class NumberConversion{None,Scaled};
enum class NumberFormat{Float,Int,Srgb};enum class ImageType{Color2D,Cube};
using CompMapping=u32;
inline NumberClass GetNumberClass(NumberFormat f){return f==NumberFormat::Int?NumberClass::Int:NumberClass::Float;}
inline bool IsInteger(NumberFormat f){return f==NumberFormat::Int;}
struct Buffer {
    bool valid=true,swizzle_enable{};u32 stride{},data_format{},index_stride{},element_size{},mapping{};
    NumberFormat format{};NumberConversion conversion{};
    explicit operator bool()const{return valid;}
    u32 GetStride()const{return stride;}u32 GetDataFmt()const{return data_format;}
    NumberFormat GetNumberFmt()const{return format;}u32 DstSelect()const{return mapping;}
    NumberConversion GetNumberConversion()const{return conversion;}
};
struct Image {
    bool valid=true,cube{};u32 width{},height{},mapping{};NumberFormat format{};
    NumberConversion conversion{};
    explicit operator bool()const{return valid;}
    ImageType GetHostViewType(bool array)const{return cube||array?ImageType::Cube:ImageType::Color2D;}
    NumberFormat GetNumberFmt()const{return format;}bool IsCube()const{return cube;}
    u32 DstSelect()const{return mapping;}NumberConversion GetNumberConversion()const{return conversion;}
};
struct Sampler {bool valid=true;u8 force_unnormalized{},force_degamma{};explicit operator bool()const{return valid;}};
}
namespace Shader {
enum class SwStage{Fragment,TessellationControl,TessellationEval,Vertex,Geometry,Compute};
struct Profile{};
struct TessellationDataConstantBuffer{u32 value{};};
struct RuntimeInfo {
    struct {struct{u32 step_rate_0{},step_rate_1{};}vs;}sw;
    u32 value{},tess{};
    bool operator==(const RuntimeInfo& o)const{return sw.vs.step_rate_0==o.sw.vs.step_rate_0&&sw.vs.step_rate_1==o.sw.vs.step_rate_1&&value==o.value&&tess==o.tess;}
    void InitFromTessConstants(TessellationDataConstantBuffer c){tess=c.value;}
};
namespace Backend {struct Bindings{u32 unified{},buffer{},user_data{};bool operator==(const Bindings&)const=default;};}
struct Info;
namespace Gcn {
struct VertexAttribute {
    enum InstanceIdType{None,OverStepRate0,OverStepRate1,Plain};
    u8 semantic{},dest_vgpr{},num_elements{},sgpr_base{},dword_offset{},instance_data{},inst_offset{},data_format{},num_format{};
    InstanceIdType GetStepRate()const{return InstanceIdType(instance_data);}
    AmdGpu::Buffer GetSharp(const Info&)const;
    bool operator==(const VertexAttribute&)const=default;
};
struct FetchShaderData {
    u32 size{};std::vector<VertexAttribute> attributes;s8 vertex_offset_sgpr=-1,instance_offset_sgpr=-1;
    bool operator==(const FetchShaderData&)const=default;
};
struct FetchShaderSnapshot {const Info* info{};std::optional<FetchShaderData> fetch;};
static thread_local const FetchShaderSnapshot* compiler_snapshot{};
void ParseFetchShader(const Info&,std::optional<FetchShaderData>&);
std::optional<FetchShaderData> ParseFetchShader(const Info&);
enum class Opcode{S_SETPC_B64,V_ADD_I32,TBUFFER_LOAD_FORMAT_X,TBUFFER_LOAD_FORMAT_XY,TBUFFER_LOAD_FORMAT_XYZ,TBUFFER_LOAD_FORMAT_XYZW,Other};
enum class InstClass{ScalarMemRd,VectorMemBufFmt,Other};
struct GcnInst {
    Opcode opcode{};InstClass inst_class{};u32 length=1;
    struct Operand{u32 code{};};std::array<Operand,3> src{},dst{};
    struct {struct{u32 offset{};}smrd;struct{u32 count{};}mubuf;struct{u32 offset{},dfmt{},nfmt{};}mtbuf;}control;
};
struct GcnCodeSlice{const u32 *p,*end;GcnCodeSlice(const u32* a,const u32* b):p(a),end(b){}bool atEnd()const{return p==end;}const u32* position()const{return p;}};
// Mock instruction decoder: changes in code still go through the real parser's
// byte validation, decode loop, attribute construction and cache replacement.
struct GcnDecodeContext {
    GcnInst decodeInstruction(GcnCodeSlice& s){
        u32 v=*s.p++;GcnInst i{};
        if(v==0){i.opcode=Opcode::S_SETPC_B64;return i;}
        i.opcode=Opcode::TBUFFER_LOAD_FORMAT_XYZW;i.inst_class=InstClass::VectorMemBufFmt;
        i.src[1].code=v;i.src[0].code=v%4;i.control.mubuf.count=4;i.control.mtbuf.dfmt=v%7;
        return i;
    }
};
}
template<class T>struct Resource {
    T sharp{};bool is_formatted{},is_array{},is_written{};u32 bindings=1;
    T GetSharp(const Info&)const{return sharp;}u32 NumBindings(const Info&)const{return bindings;}
};
struct Info {
    SwStage sw_stage=SwStage::Vertex;bool has_fetch_shader=true;u32 fetch_shader_sgpr_base{},tess{};
    std::array<u32,17> code{1,2,3,4,5,6,7,8,0};
    std::vector<Resource<AmdGpu::Buffer>> buffers;
    std::vector<Resource<AmdGpu::Image>> images,fmasks;
    std::vector<Resource<AmdGpu::Sampler>> samplers;
    std::array<AmdGpu::Buffer,32> vertex{};
    void ReadTessConstantBuffer(TessellationDataConstantBuffer& c)const{c.value=tess;}
};
// This fixture compares storage reuse with dynamic stride disabled; its
// descriptor policy is exercised separately against the production predicate.
inline bool UsesDynamicStride(const Info&, const Resource<AmdGpu::Buffer>&){return false;}
namespace Gcn {
AmdGpu::Buffer VertexAttribute::GetSharp(const Info& i)const{return i.vertex[semantic];}
const u32* GetFetchShaderCode(const Info& i,u32){return i.code.data();}
}
}
namespace TlgToggle {enum Toggle{FetchShaderCache};static thread_local bool disabled{};bool Disabled(Toggle){return disabled;}}
#define UNREACHABLE() std::abort()
namespace Shader::Gcn {
// PRODUCTION_FETCH
}
// PRODUCTION_SPECIALIZATION
// REFERENCE_SPECIALIZATION

static void equal(const Shader::StageSpecialization& a,const Shader::ReferenceSpecialization& b){
    assert(a.info==b.info && a.runtime_info==b.runtime_info && a.start==b.start && a.bitset==b.bitset);
    assert(a.fetch_shader_data==b.fetch_shader_data && a.vs_attribs==b.vs_attribs);
    assert(a.buffers==b.buffers && a.images==b.images && a.fmasks==b.fmasks && a.samplers==b.samplers);
    assert(a==b && b==a);
}
static void semantics(){
    using namespace Shader;
    Info info;Profile profile;RuntimeInfo runtime;Backend::Bindings binding;
    StageSpecialization retained;
    std::mt19937 rng(10619);
    for(unsigned n=0;n<10000;++n){
        info.sw_stage=SwStage(rng()%6);info.has_fetch_shader=rng()%4!=0;
        info.tess=rng();runtime.value=rng();runtime.sw.vs.step_rate_0=rng();runtime.sw.vs.step_rate_1=rng();
        binding={rng()%32,rng()%32,rng()%16};
        info.buffers.resize(rng()%24);info.images.resize(rng()%24);info.fmasks.resize(rng()%8);info.samplers.resize(rng()%24);
        for(auto& r:info.buffers){r.sharp.valid=rng()%2;r.is_formatted=rng()%2;r.sharp.swizzle_enable=rng()%2;r.sharp.stride=rng()%100;r.sharp.data_format=rng()%16;r.sharp.format=AmdGpu::NumberFormat(rng()%3);r.sharp.mapping=rng();r.sharp.conversion=AmdGpu::NumberConversion(rng()%2);r.sharp.index_stride=rng()%4;r.sharp.element_size=rng()%4;}
        for(auto* list:{&info.images,&info.fmasks})for(auto& r:*list){r.sharp.valid=rng()%2;r.is_array=rng()%2;r.is_written=rng()%2;r.sharp.cube=rng()%2;r.sharp.width=rng();r.sharp.height=rng();r.sharp.mapping=rng();r.sharp.format=AmdGpu::NumberFormat(rng()%3);r.sharp.conversion=AmdGpu::NumberConversion(rng()%2);r.bindings=rng()%12;}
        for(auto& r:info.samplers){r.sharp.valid=rng()%2;r.sharp.force_unnormalized=rng()%2;r.sharp.force_degamma=rng()%2;}
        for(auto& v:info.vertex){v.valid=rng()%2;v.format=AmdGpu::NumberFormat(rng()%3);v.mapping=rng();}
        // Changing bytecode, vector sizes, resource validity and stage tests reset safety.
        if(n%31==0)info.code[0]=1+rng()%200;
        ReferenceSpecialization fresh(info,runtime,profile,binding);
        retained.Refresh(info,runtime,profile,binding);equal(retained,fresh);
        if(n%97==0){auto moved=std::move(retained);retained.Refresh(info,runtime,profile,binding);equal(retained,fresh);}
    }
    // Compiler snapshots remain authoritative; no guest parse when one is active.
    Gcn::FetchShaderSnapshot snap{&info,Gcn::FetchShaderData{}};
    snap.fetch->attributes.resize(3);snap.fetch->attributes[0].dest_vgpr=199;
    Gcn::compiler_snapshot=&snap;
    retained.Refresh(info,runtime,profile,binding);
    assert(retained.fetch_shader_data==snap.fetch);
    Gcn::compiler_snapshot=nullptr;
}
int main(){
    semantics();std::thread(semantics).join();
    Shader::Info info;std::optional<Shader::Gcn::FetchShaderData> retained;
    Shader::Gcn::ParseFetchShader(info,retained);assert(retained && retained->attributes.size()==8);
    const size_t start=allocations.load();u64 checksum=0;
    for(unsigned n=0;n<100000;++n){auto copy=Shader::Gcn::ParseFetchShader(info);checksum+=copy->attributes.size();}
    const size_t fresh=allocations.load()-start;const auto old_start=allocations.load();
    for(unsigned n=0;n<100000;++n){Shader::Gcn::ParseFetchShader(info,retained);checksum+=retained->attributes.size();}
    const size_t reused=allocations.load()-old_start;
    assert(checksum==1600000 && fresh==100000 && reused==0);
    // A changed instruction must invalidate and decode; opt-out still decodes every call.
    info.code[0]=57;Shader::Gcn::ParseFetchShader(info,retained);assert(retained->attributes[0].dest_vgpr==57);
    TlgToggle::disabled=true;info.code[0]=59;Shader::Gcn::ParseFetchShader(info,retained);assert(retained->attributes[0].dest_vgpr==59);
    TlgToggle::disabled=false;
    info.code[2]=0;Shader::Gcn::ParseFetchShader(info,retained);assert(retained->attributes.size()==2);
    info.code[2]=3;Shader::Gcn::ParseFetchShader(info,retained);assert(retained->attributes.size()==8);
    info.has_fetch_shader=false;Shader::Gcn::ParseFetchShader(info,retained);assert(!retained);
    info.has_fetch_shader=true;Shader::Gcn::ParseFetchShader(info,retained);assert(retained->attributes.size()==8);
    std::printf("fetch queries=100000 fresh_allocations=%zu retained_allocations=%zu\n",fresh,reused);
}
