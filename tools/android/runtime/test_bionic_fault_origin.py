"""Run the production slow-fault profiler with inaccessible guest stacks and PCs."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]

@unittest.skipUnless(shutil.which('c++'), 'C++ compiler required')
class FaultOrigins(unittest.TestCase):
    def test_profiler_classifies_without_dereferencing_guest_stack(self):
        source = (ROOT / 'port/gpu/shadps4/video_core/page_manager.cpp').read_text()
        record = method(source, '    struct FaultSite {') + ';'
        body = method(source, '    static void ProfileFault(')
        # Only replace the clock provider, making the production five-second window deterministic.
        body = body.replace('std::chrono::steady_clock', 'Clock')
        fixture = r'''
#include <algorithm>
#include <array>
#include <cassert>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <mutex>
#include <tuple>
#include <vector>
#include <pthread.h>
#include <sys/uio.h>
#include <unistd.h>
using u64=uint64_t;using u32=uint32_t;using VAddr=u64;
struct Clock {inline static unsigned now_s=0;
 static auto now(){return std::chrono::steady_clock::time_point{std::chrono::seconds(now_s)};}};
struct GuestRegs {uint64_t gpr[16];uint64_t rip;};
enum {GUEST_CPU_IN_GUEST=1,GUEST_CPU_IN_HOST_CALL=2};
struct Context {int kind;u64 pc,host;};
int guest_cpu_signal_regs(void* p,GuestRegs* r){auto& c=*static_cast<Context*>(p);r->rip=c.pc;
 r->gpr[4]=1;return c.kind;} // invalid SP: the old profiler would recursively SIGSEGV
uintptr_t guest_cpu_host_pc(void* p){return static_cast<Context*>(p)->host;}
const void* readable_pc=nullptr;
ssize_t ReadCode(pid_t,const iovec* local,unsigned long,const iovec* remote,unsigned long,unsigned long){
 if(remote->iov_base!=readable_pc){errno=EFAULT;return -1;}
 std::memcpy(local->iov_base,remote->iov_base,local->iov_len);return local->iov_len;
}
#define process_vm_readv ReadCode
struct Profile {
// RECORD
// BODY
};
int main(int argc,char** argv){
 assert(argc==2);setenv("TLG_FAULT_PROFILE",argv[1],1);
 pthread_setname_np(pthread_self(),"JobWorker-test");
 u32 code=0x3d800020;readable_pc=&code;Context guest{GUEST_CPU_IN_GUEST,0x800123456,reinterpret_cast<u64>(&code)};
 Context host{GUEST_CPU_IN_HOST_CALL,0x800123456,1}; // unreadable PC must fail without a fault
 // Common low-cost write faults do not read guest registers or populate the profile.
 Profile::ProfileFault(nullptr,0,true,499999);
 Profile::ProfileFault(&guest,0x1048000020,false,1000000);
 Profile::ProfileFault(&guest,0x1048000030,true,2000000);
 Profile::ProfileFault(&host,0x1048000040,false,3000000);
 Clock::now_s=6;errno=EDOM;Profile::ProfileFault(&guest,0x1048000050,false,4000000);assert(errno==EDOM);
 errno=EDOM;Profile::ProfileFault(&host,0x1048000050,false,4000000);assert(errno==EDOM);
}
'''.replace('// RECORD',record).replace('// BODY',body)
        with tempfile.TemporaryDirectory() as temp:
            src=Path(temp)/'profile.cpp';exe=Path(temp)/'profile';src.write_text(fixture)
            compiled=subprocess.run(['c++','-std=c++20','-O1','-pthread','-DARCH_ARM64',
                                     '-fsanitize=undefined','-fno-sanitize-recover=all',str(src),'-o',str(exe)],
                                    capture_output=True,text=True)
            self.assertEqual(compiled.returncode,0,compiled.stderr)
            enabled=subprocess.run([str(exe),'1'],capture_output=True,text=True,timeout=10)
            self.assertEqual(enabled.returncode,0,enabled.stderr)
            out=enabled.stdout
            self.assertIn('R guest pc=0x123456',out)
            self.assertIn('W guest pc=0x123456',out)
            self.assertIn('R host-call pc=0x123456',out)
            self.assertIn('arm=3d800020',out)
            self.assertIn('arm=unavailable:00000000',out)
            self.assertIn('thread=JobWorker-test',out)
            self.assertIn('calls=2 time=5.0 ms',out)
            disabled=subprocess.run([str(exe),'0'],capture_output=True,text=True,timeout=10)
            self.assertEqual(disabled.returncode,0,disabled.stderr)
            self.assertEqual(disabled.stdout,'')

    def test_wrapper_preserves_explicit_opt_out(self):
        source=(ROOT/'tools/android/bionic/run-bionic.sh').read_text()
        line=next(x for x in source.splitlines() if x.startswith('export TLG_FAULT_PROFILE='))
        for base,explicit,expected in [('1',None,'1'),('0',None,'0'),('1','0','0')]:
            import os
            env=dict(os.environ,ISS_COMPARE_STATS=base)
            env.pop('TLG_FAULT_PROFILE',None)
            if explicit is not None:env['TLG_FAULT_PROFILE']=explicit
            result=subprocess.check_output(['sh','-c',line+'\nprintf "%s" "$TLG_FAULT_PROFILE"'],env=env,text=True)
            self.assertEqual(result,expected)

if __name__=='__main__':unittest.main()
