// Exercise the production overwrite policy on a real serialized owner queue.
#include <algorithm>
#include <array>
#include <atomic>
#include <cassert>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <functional>
#include <future>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
using u8=uint8_t;using u64=uint64_t;using VAddr=u64;
using namespace std::chrono_literals;
// QUEUE
namespace Common {template<class T>T AlignDown(T n,T a){return n/a*a;}}
enum class GpuReadbacksMode{Relaxed,Precise};
struct {GpuReadbacksMode mode=GpuReadbacksMode::Relaxed;bool dma=false;
 auto GetReadbacksMode(){return mode;}bool IsDirectMemoryAccessEnabled(){return dma;}}EmulatorSettings;
struct Stats{std::atomic<u64>cpu_store_applied{},cpu_store_fallback{},cpu_store_coverage_rejected{},cpu_store_backing_rejected{};};
Stats& Readbacks(){static Stats s;return s;}bool ReadbackStatsEnabled(){return true;}void ReportReadbacks(){}
namespace VideoCore {
enum class ReadbackSource{CpuWrite};
struct Ranges {std::vector<std::pair<u64,u64>> data;
 void ForEachInRange(u64 a,u64 n,auto f){for(auto [s,e]:data)if(s<a+n&&a<e)f(std::max(s,a),std::min(e,a+n));}
 void Subtract(u64 a,u64 n){std::erase_if(data,[&](auto r){return a<=r.first&&r.second<=a+n;});}};
struct Snapshot {struct Copy{u64 srcOffset,size;};u64 arena_base=0x1000;
 std::vector<Copy>copies;bool done=false,stale=false;bool Done(){return done;}};
struct Memory {Queue* q;bool fail=false;std::array<u8,4096> bytes;
 bool TryCpuWriteBacking(void* dst,const void* src,u64 n){q->CheckOwner();if(fail)return false;
  std::memcpy(bytes.data()+reinterpret_cast<VAddr>(dst)-0x1000,src,n);return true;}};
struct Tracker {bool gpu=true,cpu=false;Queue* q;
 bool IsRegionGpuModified(u64,u64){q->CheckOwner();return gpu;}
 void UnmarkRegionAsGpuModified(u64 a,u64 n){q->CheckOwner();assert(a==0x1000&&n==4096);gpu=false;}
 void MarkRegionAsCpuModified(u64 a,u64 n){q->CheckOwner();assert(a==0x1000&&n==4096&&!gpu);cpu=true;}};
struct Scheduler {Queue* q;unsigned waits=0,signals=0;
 void WaitHostCopiesFor(u64 a,u64 n){q->CheckOwner();assert(a==0x1000&&n==4096);++waits;}
 void WaitDeferredSignals(){q->CheckOwner();++signals;}};
struct Texture {Queue* q;unsigned resolves=0,invalidations=0;
 void ResolveReadbacks(u64 a,u64 n,bool owner,ReadbackSource){if(owner)q->CheckOwner();assert((owner&&a==0x1000&&n==4096)||(!owner&&a==0x1040&&n==16));++resolves;}
 void InvalidateMemory(u64 a,u64 n){q->CheckOwner();assert(a==0x1040&&n==16);++invalidations;}};
struct BufferCache {
 Queue* liverpool;Memory* memory;Tracker tracker;Tracker* memory_tracker=&tracker;
 std::atomic<u64>eager_count{};Ranges gpu_modified_ranges;std::mutex deferred_mutex;
 std::vector<std::shared_ptr<Snapshot>>deferred_readbacks;Scheduler scheduler;Texture texture_cache;
 unsigned stale_marks=0,settled=0,readback_fallbacks=0;
 void ReadMemory(VAddr,u64,bool,bool,const std::function<bool()>& prepare){
  liverpool->SendCommand<true,true>([&]{if(!prepare())++readback_fallbacks;});
 }
 BufferCache(Queue* q,Memory* m):liverpool(q),memory(m),tracker{true,false,q},scheduler{q},texture_cache{q}{}
 void MarkEagerStale(u64,u64){liverpool->CheckOwner();}
 void MarkDeferredStale(u64,u64,bool cpu){liverpool->CheckOwner();assert(cpu);++stale_marks;
  for(auto& s:deferred_readbacks)if(!s->Done())s->stale=true;}
 void SettleDeferredReadbacks(u64,u64){liverpool->CheckOwner();++settled;
  for(auto& s:deferred_readbacks)if(!s->Done()){assert(s->stale);for(auto c:s->copies)
   gpu_modified_ranges.data.emplace_back(s->arena_base+c.srcOffset,s->arena_base+c.srcOffset+c.size);s->done=true;}}
 bool CpuStoreOverwrite(VAddr,const void*,u64);
};
// METHOD
}
int main(int,char** argv){
 const std::string mode=argv[1];setenv("TLG_WRITE_MERGE",mode=="globaloff"?"0":"1",1);
 if(mode=="off")setenv("TLG_NATIVE_STORE_MERGE","0",1);
 if(mode=="dma")EmulatorSettings.dma=true;
 if(mode=="precise")EmulatorSettings.mode=GpuReadbacksMode::Precise;
 Queue q;VideoCore::Memory memory{&q};memory.bytes.fill(7);VideoCore::BufferCache c{&q,&memory};
 c.gpu_modified_ranges.data={{0x1040,0x1050},{0x3000,0x3010}};
 if(mode=="inside")c.gpu_modified_ranges.data={{0x1044,0x1048}};
 if(mode=="neighbor")c.gpu_modified_ranges.data.push_back({0x1080,0x1090});
 if(mode=="left")c.gpu_modified_ranges.data.push_back({0x1038,0x1048});
 if(mode=="right")c.gpu_modified_ranges.data.push_back({0x1048,0x1058});
 if(mode.find("pending")!=std::string::npos){auto p=std::make_shared<VideoCore::Snapshot>();
  p->copies={{mode=="pending"?0x40ull:0x80ull,16}};p->done=mode=="done-pending";p->stale=mode=="stale-pending";
  c.deferred_readbacks.push_back(p);if(mode=="pending")c.gpu_modified_ranges.data.clear();}
 if(mode=="eager")c.eager_count=1;
 if(mode=="failure")memory.fail=true;
 if(mode=="non-gpu")c.tracker.gpu=false;
 std::array<u8,16>payload;payload.fill(55);
 const bool expected=mode=="exact"||mode=="inside"||mode=="pending"||mode=="done-pending";
 const bool result=c.CpuStoreOverwrite(mode=="misaligned"?0x1041:0x1040,payload.data(),16);
 assert(result==expected);
 const bool eligible=mode!="dma"&&mode!="precise"&&mode!="off"&&mode!="globaloff"&&mode!="misaligned"&&mode!="nonbionic";
 assert(q.submitted==unsigned(eligible));
 assert(c.readback_fallbacks==unsigned(eligible&&!expected&&mode!="non-gpu"));
 if(expected){assert(!c.tracker.gpu&&c.tracker.cpu&&c.scheduler.waits==1&&c.scheduler.signals==1);
  assert(c.texture_cache.resolves==2&&c.texture_cache.invalidations==1&&c.stale_marks==1&&c.settled==1);
  for(unsigned i=0;i<4096;++i)assert(memory.bytes[i]==(i>=64&&i<80?55:7));
  for(auto [a,b]:c.gpu_modified_ranges.data)assert(a>=0x2000||b<=0x1000);
  // A subsequent page upload preserves every neighboring CPU/GPU-coherent byte.
  std::array<u8,4096>device;device=memory.bytes;for(unsigned i=0;i<4096;++i)assert(device[i]==(i>=64&&i<80?55:7));
 }else{for(auto b:memory.bytes)assert(b==7);assert(!c.tracker.cpu);
  if(mode!="failure")assert(c.stale_marks==0&&c.scheduler.waits==0);}
}
