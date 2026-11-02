"""Compile the complete production AudioOut and read real PCM from its FIFO."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[3]
CC=shutil.which('cc')

@unittest.skipUnless(CC,'C compiler unavailable')
class NativeAudioTests(unittest.TestCase):
    def test_native_formats_ring_and_real_fifo_output(self):
        code=r'''
#define _GNU_SOURCE
#include "runtime_audio.c"
#include <assert.h>
#include <poll.h>
#include <limits.h>
uintptr_t runtime_lookup(const RuntimeExport* table,size_t count,const char* name){
 for(size_t i=0;i<count;++i)if(!strcmp(table[i].name,name))return (uintptr_t)table[i].function;
 return 0;
}
static void conversion(void){
 Port p={.channels=1,.sample_bytes=2};int l,r;int16_t mono=1234;
 frame_to_stereo(&p,(const unsigned char*)&mono,&l,&r);assert(l==1234 && r==1234);
 p.channels=2;int16_t stereo[2]={-32768,32767};
 frame_to_stereo(&p,(const unsigned char*)stereo,&l,&r);assert(l==-32768 && r==32767);
 p.is_float=1;p.sample_bytes=4;float f[8]={.25f,-.5f};
 frame_to_stereo(&p,(const unsigned char*)f,&l,&r);assert(l==8192 && r==-16384);
 f[0]=NAN;f[1]=INFINITY;frame_to_stereo(&p,(const unsigned char*)f,&l,&r);assert(l==0 && r==0);
 f[0]=1e30f;f[1]=-1e30f;frame_to_stereo(&p,(const unsigned char*)f,&l,&r);assert(l==32767 && r==-32768);
 p.channels=8;memset(f,0,sizeof f);f[2]=.5f;
 frame_to_stereo(&p,(const unsigned char*)f,&l,&r);assert(l>4200 && l<4300 && l==r);
 memset(f,0,sizeof f);f[6]=.5f;frame_to_stereo(&p,(const unsigned char*)f,&l,&r);assert(l>3000 && l<3100 && r==0);
 // Exercise actual native source-byte queue accounting and wraparound without a mixer thread.
 Port* ring=&ports[0];ring->ring_cap=16;ring->ring=malloc(16);ring->ring_head=12;ring->ring_len=0;
 pthread_once(&fifo_locks_once,fifo_init_locks);unsigned char bytes[12];for(int i=0;i<12;++i)bytes[i]=(unsigned char)(i+1);
 sink_put(ring,bytes,12);assert(sink_queued(ring)==12);
 for(int i=0;i<12;++i)assert(ring->ring[(12+i)%16]==bytes[i]);
 sink_put(ring,bytes,12);assert(sink_queued(ring)==12); // bounded when reader pauses
 sink_close(ring);assert(!ring->ring);memset(ring,0,sizeof *ring);
}
static int read_pair(int fd,int left,int right){
 unsigned char data[4096];int carry=0;
 for(int tries=0;tries<300;++tries){
  struct pollfd p={fd,POLLIN,0};if(poll(&p,1,10)<=0)continue;
  ssize_t n=read(fd,data+carry,sizeof data-(size_t)carry);if(n<0 && errno==EAGAIN)continue;assert(n>=0);
  size_t whole=((size_t)carry+(size_t)n)/4*4;
  for(size_t at=0;at<whole;at+=4){int16_t lr[2];memcpy(lr,data+at,4);if(lr[0]==left && lr[1]==right)return 1;}
  carry+=(int)n-(int)whole;if(carry)memmove(data,data+whole,(size_t)carry);
 }
 return 0;
}
int main(int argc,char** argv){
 assert(argc==2);
 if(!strcmp(argv[1],"conversion")){conversion();return 0;}
 if(!strcmp(argv[1],"invalid")){
  FILE* f=fopen("not-a-pipe","wb");assert(f);fclose(f);setenv("TLG_AUDIO_FIFO","not-a-pipe",1);
  assert(!audio_backend() && fifo_fd==-1);return 0;
 }
 assert(mkfifo("audio.fifo",0600)==0);int fd=open("audio.fifo",O_RDWR|O_NONBLOCK);assert(fd>=0);
 setenv("TLG_AUDIO_FIFO","audio.fifo",1);assert(audio_init()==0);
 // Audio3d's host helper bypasses guest AudioOutInit but must feed the same native mixer.
 int handle=runtime_audio_host_open(126,256,4);assert(handle>=0);
 float samples[512];for(int i=0;i<256;++i){samples[2*i]=.25f;samples[2*i+1]=-.5f;}
 int32_t volume[8]={16384,32768};assert(audio_volume(handle,3,volume)==0);
 assert(runtime_audio_host_output(handle,samples,0)==512);
 assert(read_pair(fd,4096,-16384)); // actual s16 stereo bytes, including the per-channel volume
 assert(runtime_audio_host_close(handle)==0);
 int next=runtime_audio_host_open(126,256,1);assert(next==handle);
 int16_t shorts[512];for(int i=0;i<512;++i)shorts[i]=2345;
 assert(runtime_audio_host_output(next,shorts,0)==512);assert(read_pair(fd,2345,2345));
 assert(runtime_audio_host_close(next)==0);close(fd);return 0;
}
'''
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp);(path/'audio.c').write_text(code)
            result=subprocess.run([CC,'-std=gnu11','-O1','-g','-Wall','-Wextra','-D__ANDROID__=1','-I'+str(ROOT/'port/src'),'-fsanitize=undefined','-fno-sanitize-recover=all',str(path/'audio.c'),'-pthread','-lm','-o',str(path/'audio')],capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            for mode in ('conversion','invalid','fifo'):
                with self.subTest(mode=mode):
                    result=subprocess.run([str(path/'audio'),mode],cwd=temp,capture_output=True,text=True,timeout=12)
                    self.assertEqual(result.returncode,0,result.stderr+result.stdout)

if __name__=='__main__':unittest.main()
