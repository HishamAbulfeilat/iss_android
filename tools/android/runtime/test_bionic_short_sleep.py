"""Compile production short-sleep policy and sleep loop; control prctl and EINTR."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from test_bionic_resource_reuse import method

ROOT = Path(__file__).resolve().parents[3]
CC = shutil.which("cc")

PREAMBLE = r'''
#define _GNU_SOURCE
#include <assert.h>
#include <errno.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <time.h>
static atomic_uint sets,gets,naps;
static _Thread_local unsigned long slack=50000;
static int denied,retry,nap_error;
static unsigned long observed[3];
static int mock_prctl(int command,...) {
    va_list args;va_start(args,command);
    unsigned long value=va_arg(args,unsigned long);
    for(int i=0;i<3;++i) assert(va_arg(args,unsigned long)==0UL);
    va_end(args);errno=EIO;
    if(command==PR_GET_TIMERSLACK) {++gets;return (int)slack;}
    assert(command==PR_SET_TIMERSLACK && value==1UL);++sets;
    if(denied) {errno=EPERM;return -1;}
    slack=value;return 0;
}
static int mock_nanosleep(const struct timespec *request,struct timespec *remaining) {
    unsigned index=atomic_fetch_add(&naps,1);assert(index<3);
    observed[index]=(unsigned long)request->tv_sec*1000000000UL+request->tv_nsec;
    if(retry && index==0) {*remaining=(struct timespec){0,700};errno=EINTR;return -1;}
    if(nap_error) {errno=nap_error;return -1;}
    return 0;
}
static void runtime_wait_begin(const char *why,const void *a,const void *b,
                               const void *c,uintptr_t frame) {
    assert(!strcmp(why,"sleep"));(void)a;(void)b;(void)c;assert(frame==42);
}
static void runtime_wait_end(void) {}
#define prctl mock_prctl
#define nanosleep mock_nanosleep
'''

MAIN = r'''
static void *worker(void *unused) {
    (void)unused;errno=EDOM;
    for(unsigned i=0;i<2000;++i) runtime_prepare_short_sleep(1000);
    assert(errno==EDOM && slack==1);return NULL;
}
int main(int argc,char **argv) {
    assert(argc==2);
    unsetenv("TLG_SHORT_SLEEP_SLACK");
    if(!strcmp(argv[1],"off")) setenv("TLG_SHORT_SLEEP_SLACK","0",1);
    if(!strcmp(argv[1],"denied")) denied=1;
    errno=EDOM;
    runtime_prepare_short_sleep(1000000);
    runtime_prepare_short_sleep(UINT64_MAX);
    assert(sets==0 && gets==0 && errno==EDOM);
    runtime_prepare_short_sleep(!strcmp(argv[1],"zero") ? 0 : 1);
    runtime_prepare_short_sleep(999999);
    assert(errno==EDOM);
    int active=EXPECTED_ENABLED && strcmp(argv[1],"off");
    assert(sets==(unsigned)active);
    assert(gets==(unsigned)(active ? (denied ? 1 : 2) : 0));
    assert(slack==(unsigned long)(active && !denied ? 1 : 50000));
    if(!strcmp(argv[1],"threads")) {
        pthread_t threads[8];
        for(unsigned i=0;i<8;++i) assert(!pthread_create(&threads[i],NULL,worker,NULL));
        for(unsigned i=0;i<8;++i) assert(!pthread_join(threads[i],NULL));
        assert(sets==9 && gets==18 && slack==1);
    }
    retry=1;assert(sleep_ns(1000,42)==0);
    assert(naps==2 && observed[0]==1000 && observed[1]==700);
    nap_error=EINVAL;assert(sleep_ns(1000000,42)==EINVAL);
    assert(naps==3 && observed[2]==1000000);
    return 0;
}
'''


@unittest.skipUnless(CC, "C compiler unavailable")
class ShortSleepTests(unittest.TestCase):
    def check(self, native, scenarios):
        source = (ROOT / "port/src/runtime_kernel.c").read_text()
        policy = method(source, "void runtime_prepare_short_sleep(uint64_t ns)")
        loop = method(source, "static int sleep_ns(uint64_t ns,uintptr_t frame)")
        defines = "#define __ANDROID__ 1\n#define __BIONIC__ 1\n" if native else ""
        code = PREAMBLE + defines + policy + "\n" + loop + MAIN
        with tempfile.TemporaryDirectory() as directory:
            cfile = Path(directory) / "sleep.c"
            binary = Path(directory) / "sleep"
            cfile.write_text(code)
            subprocess.run([CC, "-std=gnu11", "-O2", "-Wall", "-Wextra", "-pthread",
                            f"-DEXPECTED_ENABLED={int(native)}", str(cfile), "-o", str(binary)],
                           check=True, capture_output=True, text=True)
            for scenario in scenarios:
                result = subprocess.run([str(binary), scenario], capture_output=True, text=True,
                                        timeout=10)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_native_boundaries_once_per_thread_failure_and_override(self):
        self.check(True, ["normal", "zero", "threads", "denied", "off"])

    def test_non_bionic_keeps_host_sleep_policy(self):
        self.check(False, ["normal", "off", "denied"])


if __name__ == "__main__":
    unittest.main()
