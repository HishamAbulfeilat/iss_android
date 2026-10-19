"""Validate the observed STR Q decoder, suspended FEX context and safe page overwrite."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method
from test_bionic_relaxed_deferred import PREAMBLE

ROOT=Path(__file__).resolve().parents[3]

@unittest.skipUnless(shutil.which('c++'),'C++ compiler required')
class NativeStore(unittest.TestCase):
    def run_cpp(self,source,modes=('default',),bionic=True):
        with tempfile.TemporaryDirectory() as temp:
            src=Path(temp)/'test.cpp';exe=Path(temp)/'test';src.write_text(source)
            flags=['-D__ANDROID__','-DISS_ANDROID_BIONIC'] if bionic else []
            result=subprocess.run(['c++','-std=c++20','-O1','-pthread','-fsanitize=undefined',
                '-fno-sanitize-recover=all','-I',str(ROOT),*flags,str(src),'-o',str(exe)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            for mode in modes:
                with self.subTest(mode=mode):
                    result=subprocess.run([str(exe),mode],capture_output=True,text=True,timeout=10)
                    self.assertEqual(result.returncode,0,result.stdout+result.stderr)

    def test_observed_decoder_and_rejections(self):
        self.run_cpp(r'''
#include <cassert>
#include "cpu/aarch64_store.h"
int main(int,char**){
 uint64_t regs[31]{};regs[20]=0x1000;
 uint32_t observed[]={0x3d800299,0x3d800690,0x3d800a95,0x3d800e91};
 unsigned rt[]={25,16,21,17};
 for(unsigned i=0;i<4;++i){auto d=IssCpu::DecodeVectorStore(observed[i],regs,0,0x1000+i*16);
  assert(d&&d->address==0x1000+i*16&&d->vector==rt[i]);}
 for(unsigned n=0;n<32;++n)for(unsigned t=0;t<32;++t)for(unsigned imm:{0,1,31,255,4095}){
  for(auto& r:regs)r=0x1000;
  uint32_t op=0x3d800000u|(imm<<10)|(n<<5)|t;auto a=0x1000+imm*16;
  auto d=IssCpu::DecodeVectorStore(op,regs,0x1000,a+15);assert(d&&d->address==a&&d->vector==t);
  assert(!IssCpu::DecodeVectorStore(op,regs,0x1000,a-1));
  assert(!IssCpu::DecodeVectorStore(op,regs,0x1000,a+16));
 }
 for(unsigned b=22;b<32;++b)assert(!IssCpu::DecodeVectorStore(0x3d800299^(1u<<b),regs,0,0x1000));
 for(auto op:{0xbc2de9d4u,0x4d008294u,0x3c9f00f3u,0xa9032c6au,0xf8008507u,0xd904029fu})
  assert(!IssCpu::DecodeVectorStore(op,regs,0,0x1000));
 regs[20]=0x1001;assert(!IssCpu::DecodeVectorStore(observed[0],regs,0,0x1001));
 regs[20]=UINT64_MAX-15;assert(!IssCpu::DecodeVectorStore(observed[0],regs,0,UINT64_MAX-15));
 regs[20]=(1ull<<40)-16;assert(IssCpu::DecodeVectorStore(observed[0],regs,0,regs[20]));
 assert(!IssCpu::DecodeVectorStore(observed[1],regs,0,regs[20]));
}
''')

    def test_production_fex_context(self):
        source=(ROOT/'cpu/guest_cpu_fex.cpp').read_text()
        bodies='\n'.join([method(source,'struct FpsimdRecord {')+';',
            'constexpr uint32_t FpsimdMagic=0x46508001;',method(source,'FpsimdRecord* Fpsimd('),
            method(source,'int guest_cpu_try_store_fault(')])
        fixture=r'''
#include <array>
#include <cassert>
#include <cstdint>
#include <cstring>
#include <string>
#include "cpu/aarch64_store.h"
struct ucontext_t {struct {uint64_t regs[31],sp,pc;alignas(16) unsigned char __reserved[4096];}uc_mcontext;};
using GuestStoreWriter=int(*)(uintptr_t,const void*,size_t,void*);
struct GuestThread {void* thread;};GuestThread thread;GuestThread* self=&thread;
struct Context {bool valid=true;bool IsAddressInCodeBuffer(void*,uint64_t){return valid;}} context;Context* ctx=&context;
// BODIES
struct Output {unsigned calls=0;bool accept=true;std::array<unsigned char,16> data;
 static int Write(uintptr_t at,const void* p,size_t n,void* opaque){auto& o=*static_cast<Output*>(opaque);
  ++o.calls;assert(at==0x1040&&n==16);if(!o.accept)return 0;std::memcpy(o.data.data(),p,n);return 1;}};
int main(int,char** argv){
 const std::string mode=argv[1];ucontext_t uc{};uint32_t code=0x3d800299;auto& m=uc.uc_mcontext;
 m.pc=reinterpret_cast<uintptr_t>(&code);m.regs[20]=0x1040;m.sp=0x2000;
 auto* fp=reinterpret_cast<FpsimdRecord*>(m.__reserved);fp->magic=FpsimdMagic;fp->size=sizeof(*fp);
 std::memset(&fp->v[25],0x55,16);Output output;
 if(mode=="failure")output.accept=false;
 if(mode=="foreign")context.valid=false;
 if(mode=="no-thread")self=nullptr;
 if(mode=="missing")fp->magic=0;
 if(mode=="short")fp->size=16;
 if(mode=="huge")fp->size=8192;
 if(mode=="bad-record"){fp->magic=123;fp->size=9;}
 if(mode=="chain"){std::memmove(m.__reserved+16,m.__reserved,sizeof(FpsimdRecord));
  uint32_t header[2]={123,16};std::memcpy(m.__reserved,header,sizeof(header));}
 if(mode=="wrong-instruction")code=0x3dc00299; // LDR Q
 if(mode=="sp"){code=0x3d8003f9;m.sp=0x1040;}
 auto before=uc;
 const bool expected=mode=="default"||mode=="sp"||mode=="chain";
 assert(guest_cpu_try_store_fault(&uc,0x104f,Output::Write,&output)==expected);
 if(expected){assert(m.pc==before.uc_mcontext.pc+4);m.pc=before.uc_mcontext.pc;
  assert(!std::memcmp(&uc,&before,sizeof(uc)));for(auto b:output.data)assert(b==0x55);assert(output.calls==1);
 }else{assert(!std::memcmp(&uc,&before,sizeof(uc)));assert(output.calls==(mode=="failure"?1:0));}
}
'''.replace('// BODIES',bodies)
        self.run_cpp(fixture,('default','sp','chain','failure','foreign','no-thread','missing','short','huge','bad-record','wrong-instruction'))

    def test_full_page_overwrite_includes_pending_ranges(self):
        body=method((ROOT/'port/gpu/shadps4/video_core/buffer_cache/buffer_cache.cpp').read_text(),
                    'bool BufferCache::CpuStoreOverwrite(')
        fixture=(ROOT/'tools/android/runtime/cpp/native_store_overwrite.cpp').read_text()
        queue=PREAMBLE[PREAMBLE.index('struct Queue {'):PREAMBLE.index('struct Semaphore {')]
        fixture=fixture.replace('// QUEUE',queue).replace('// METHOD',body)
        self.run_cpp(fixture,('exact','inside','neighbor','left','right','pending','pending-neighbor',
            'stale-pending','done-pending','eager','failure','dma','precise','off','globaloff','misaligned','non-gpu'))
        self.run_cpp(fixture,('nonbionic',),bionic=False)

    def test_fault_dispatch_and_resume_profile(self):
        source=(ROOT/'port/gpu/shadps4/video_core/page_manager.cpp').read_text()
        bodies=method(source,'    static bool HandleGuestFault(')+'\n'+method(source,'    static bool GuestFaultSignalHandler(')
        fixture=r'''
#define ARCH_ARM64 1
#include <atomic>
#include <cassert>
#include <chrono>
#include <cstdint>
#include <cstdlib>
#include <string>
#include "port/src/guest_cpu.h"
using u64=uint64_t;using VAddr=u64;
enum class GpuReadbacksMode{Relaxed,Precise};
struct {GpuReadbacksMode mode=GpuReadbacksMode::Relaxed;bool dma=false;
 auto GetReadbacksMode(){return mode;}bool IsDirectMemoryAccessEnabled(){return dma;}}EmulatorSettings;
struct Context {uintptr_t pc=0x100;bool write=true;};
namespace Common {bool IsWriteError(void* c){return static_cast<Context*>(c)->write;}
 void* GetRip(void* c){return reinterpret_cast<void*>(static_cast<Context*>(c)->pc);}}
namespace TlgStats {std::atomic<unsigned> gpu_signal_faults{},read_faults{};int t_write_faults,t_read_faults;
 struct Timer{Timer(int){}};}
struct Raster {bool owner=false,dirty=true,accept=true;unsigned writes=0,reads=0,merges=0;
 bool IsGpuSideThread(){return owner;}Raster& GetBufferCache(){return *this;}
 bool IsRegionGpuModified(uintptr_t,size_t){return dirty;}
 bool OnWriteFault(VAddr,bool){++writes;return true;}bool ReadMemory(VAddr,u64,bool){++reads;return true;}};
Raster gpu;unsigned decoded=0;
int tlggpu_cpu_store(void*,const void*,uint64_t){++gpu.merges;return gpu.accept;}
int guest_cpu_try_store_fault(void* c,uintptr_t a,GuestStoreWriter w,void* opaque){
 ++decoded;unsigned char bytes[16]{};if(!w(a,bytes,16,opaque))return 0;
 static_cast<Context*>(c)->pc+=4;return 1;}
struct Profile {inline static Raster* rasterizer=&gpu;inline static unsigned profiles=0;
 static void ProfileFault(void*,VAddr,bool,u64){++profiles;}
// BODIES
};
int main(int,char** argv){const std::string mode=argv[1];Context c;
 if(mode=="read")c.write=false;
 if(mode=="owner")gpu.owner=true;
 if(mode=="cpuonly")gpu.dirty=false;
 if(mode=="reject")gpu.accept=false;
 if(mode=="precise")EmulatorSettings.mode=GpuReadbacksMode::Precise;
 if(mode=="dma")EmulatorSettings.dma=true;
 if(mode=="off")setenv("TLG_NATIVE_STORE_MERGE","0",1);
 const bool success=mode=="success";
 assert(Profile::GuestFaultSignalHandler(&c,reinterpret_cast<void*>(0x1040)));
 assert(c.pc==(success?0x104:0x100));
 assert(Profile::profiles==(success?0:1));
 assert(gpu.writes==((success||mode=="read")?0:1));assert(gpu.reads==(mode=="read"?1:0));
 assert(decoded==((success||mode=="reject")?1:0));assert(gpu.merges==decoded);
}
'''.replace('// BODIES',bodies)
        self.run_cpp(fixture,('success','reject','read','owner','cpuonly','precise','dma','off'))

    def test_native_entrypoint_rejects_owner_and_locked_callers(self):
        body=method((ROOT/'port/gpu/shim/tlggpu.cpp').read_text(),'extern "C" int tlggpu_cpu_store(')
        fixture=r'''
#include <cassert>
#include <cstdint>
using VAddr=uintptr_t;
enum class GpuReadbacksMode{Relaxed,Precise};
struct {GpuReadbacksMode mode=GpuReadbacksMode::Relaxed;bool dma=false;
 auto GetReadbacksMode(){return mode;}bool IsDirectMemoryAccessEnabled(){return dma;}}EmulatorSettings;
bool can_queue=true;int runtime_memory_cpu_write_can_queue(){return can_queue;}
struct Raster {bool owner=false,dirty=true;unsigned stores=0,owner_checks=0;
 Raster& GetBufferCache(){return *this;}bool IsGpuSideThread(){++owner_checks;return owner;}
 bool IsRegionGpuModified(VAddr,uint64_t){return dirty;}
 bool CpuStoreOverwrite(VAddr,const void*,uint64_t){++stores;return true;}};
namespace Core {struct Memory{Raster* raster=nullptr;static Memory* Instance(){static Memory m;return &m;}
 Raster* GetRasterizer(){return raster;}};}
// BODY
int main(int,char**){
 unsigned char bytes[16]{};auto* dst=reinterpret_cast<void*>(0x1040);Raster r;
 assert(!tlggpu_cpu_store(dst,bytes,16));Core::Memory::Instance()->raster=&r;
 assert(!tlggpu_cpu_store(dst,bytes,8));assert(!tlggpu_cpu_store(reinterpret_cast<void*>(0x1041),bytes,16));
 assert(!tlggpu_cpu_store(reinterpret_cast<void*>(1ull<<40),bytes,16));
 r.dirty=false;assert(!tlggpu_cpu_store(dst,bytes,16));assert(!r.owner_checks);r.dirty=true;
 r.owner=true;assert(!tlggpu_cpu_store(dst,bytes,16));r.owner=false;
 can_queue=false;assert(!tlggpu_cpu_store(dst,bytes,16));can_queue=true;
 EmulatorSettings.dma=true;assert(!tlggpu_cpu_store(dst,bytes,16));EmulatorSettings.dma=false;
 EmulatorSettings.mode=GpuReadbacksMode::Precise;assert(!tlggpu_cpu_store(dst,bytes,16));
 EmulatorSettings.mode=GpuReadbacksMode::Relaxed;assert(!r.stores);
 assert(tlggpu_cpu_store(dst,bytes,16));assert(r.stores==1);
}
'''.replace('// BODY',body)
        self.run_cpp(fixture)

if __name__=='__main__':unittest.main()
