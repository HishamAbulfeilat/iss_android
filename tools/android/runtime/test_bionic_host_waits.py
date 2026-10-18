"""Exercise production mutex fallback and opt-in host timing without Android/Vulkan."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
CXX = shutil.which("c++")


@unittest.skipUnless(CXX, "C++ compiler unavailable")
class BionicHostWaitTests(unittest.TestCase):
    def run_cpp(self, code, *flags):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "test.cpp"
            binary = Path(directory) / "test"
            source.write_text(code)
            subprocess.run([CXX, "-std=c++20", "-O2", "-Wall", "-Wextra", "-Werror",
                            "-pthread", "-I", str(ROOT / "port/gpu/shadps4"),
                            "-I", str(ROOT / "port/gpu/shim"), *flags,
                            str(source), "-o", str(binary)],
                           check=True, capture_output=True, text=True)
            subprocess.run([str(binary)], check=True, capture_output=True, text=True,
                           timeout=20)

    def test_android_mutex_without_glibc_initializer(self):
        self.run_cpp(r'''
#include <pthread.h>
#include <atomic>
#include <cassert>
#include <mutex>
#include <thread>
#include <vector>
#undef PTHREAD_ADAPTIVE_MUTEX_INITIALIZER_NP
#define __ANDROID__ 1
#include "common/adaptive_mutex.h"
int main() {
    Common::AdaptiveMutex mutex;
    assert(mutex.try_lock());
    std::atomic<bool> tried{false};
    std::thread waiter([&] {
        assert(!mutex.try_lock());
        tried.store(true, std::memory_order_release);
        std::lock_guard lock{mutex};
    });
    while (!tried.load(std::memory_order_acquire)) std::this_thread::yield();
    mutex.unlock();
    waiter.join();
    unsigned count=0;
    std::vector<std::thread> workers;
    for (unsigned i=0;i<4;++i) workers.emplace_back([&] {
        for (unsigned j=0;j<20000;++j) {
            std::lock_guard lock{mutex};
            ++count;
        }
    });
    for (auto& worker: workers) worker.join();
    assert(count==80000);
}
''')

    def test_timers_enabled_and_disabled(self):
        code = r'''
#include <cassert>
#include <thread>
#include <vector>
#include "iss_bionic_perf.h"
int main() {
    using namespace IssBionicPerf;
    assert(Enabled()==EXPECTED_ENABLED);
    std::vector<std::thread> workers;
    for (unsigned i=0;i<4;++i) workers.emplace_back([] {
        for (unsigned j=0;j<1000;++j) {
            Scope timer{Stage::Submission};
        }
    });
    for (auto& worker: workers) worker.join();
    auto sample=Take(Stage::Submission);
    assert(sample.calls==(EXPECTED_ENABLED ? 4000u : 0u));
    assert(Take(Stage::Submission).calls==0);
    assert(Take(Stage::Submission).ns==0);
    assert(Take(Stage::AhbAcquire).calls==0);
    OnPresent(0,0);
}
'''
        # The launcher enables collection, but other runtimes must remain unaffected.
        code = code.replace('using namespace IssBionicPerf;',
                            'setenv("ISS_COMPARE_STATS", "1", 1);\n    using namespace IssBionicPerf;')
        self.run_cpp(code, "-DISS_ANDROID_BIONIC=1", "-DEXPECTED_ENABLED=1")
        self.run_cpp(code, "-DEXPECTED_ENABLED=0")


if __name__ == "__main__":
    unittest.main()
