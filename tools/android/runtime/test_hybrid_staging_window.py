"""Compile and exercise demand-driven 512 KiB GPU staging page selection."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[3]
CXX=shutil.which("c++")
@unittest.skipUnless(CXX,"C++ compiler unavailable")
class HybridWindowTests(unittest.TestCase):
    def test_requested_pages_and_recent_neighbors_are_complete(self):
        cpp=r'''
#include <cassert>
#include <cstdint>
#include <utility>
#include <vector>
#include "video_core/buffer_cache/hybrid_staging_window.h"
int main() {
    VideoCore::HybridStagingWindow history;
    constexpr uint64_t base=0x10000000, window=512*1024;
    std::vector<std::pair<uint64_t,uint64_t>> ranges;
    auto select=[&](uint64_t offset,uint64_t size) {
        ranges.clear();
        return history.ForEachSelected(base,base+window,base+offset,base+offset+size,
            [&](auto start,auto length){ranges.emplace_back(start-base,length);});
    };
    assert(select(0x304,8));
    assert(ranges.size()==1 && ranges[0].first==0 && ranges[0].second==4096);
    assert(select(0x20104,8));
    assert(ranges.size()==2 && ranges[0].first==0 && ranges[1].first==0x20000);
    assert(select(0x20004,8)); assert(ranges.size()==2);
    assert(select(0x5000-4,8));
    assert(ranges.size()==3 && ranges[1].first==0x4000 && ranges[1].second==8192);
    assert(!select(0,window)); // explicit fallback to unchanged full window
    ranges.clear();
    assert(history.ForEachSelected(base+window,base+2*window,base+window+0x304,
        base+window+0x30c,[&](auto address,auto length){
            ranges.emplace_back(address-base-window,length);
        }));
    assert(ranges.size()==1 && ranges[0].first==0);
}
'''
        with tempfile.TemporaryDirectory() as temp:
            file=Path(temp)/"test.cpp"
            exe=Path(temp)/"test"
            file.write_text(cpp)
            result=subprocess.run([CXX,"-std=c++20","-O2","-Wall","-Wextra","-Werror",
              "-I",str(ROOT/"port/gpu/shadps4"),str(file),"-o",str(exe)],
              capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            result=subprocess.run([str(exe)],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
