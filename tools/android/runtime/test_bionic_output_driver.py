"""Regression: Bloodframe must not override VSync; exercise actual routing and ELF validation."""
from pathlib import Path
import os
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[3]
JAVA=ROOT/'android/app/src/ro/cobrabm/fexdroid'

class OutputDriverTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('c++'), 'C++ compiler unavailable')
    def test_live_vsync_and_multiplier_transitions(self):
        source=(ROOT/'port/gpu/shadps4/video_core/renderer_vulkan/vk_swapchain_ahb.cpp').read_text()
        update=source[source.index('void Swapchain::UpdateVsync()'):source.index('Swapchain::~Swapchain()')]
        generation=source[source.index('void Swapchain::SetFrameGeneration('):source.index('void Swapchain::Recreate(')]
        harness=r'''
#include <atomic>
#include <cassert>
#include <initializer_list>
using u32=unsigned;
struct TlgSettings { std::atomic<bool> vsync{false};static TlgSettings& Get(){static TlgSettings s;return s;} };
u32 BufferCount(u32 n){return n>=2&&n<=4?n+3:3;}
struct Swapchain {
 struct Client {bool fifo=false;bool Connected(){return true;}bool SetFifo(bool v){fifo=v;return true;}} client;
 struct Window{u32 GetWidth(){return 1280;}u32 GetHeight(){return 720;}} window;
 u32 framegen_multiplier=0,image_count=3,width=1280,height=720;bool lost=false;
 void PeerGone(){lost=true;}void Recreate(u32,u32){}
 void UpdateVsync();void SetFrameGeneration(u32);
};
'''+update+generation+r'''
int main(){Swapchain s;
 for(bool sync:{false,true,false}) {
  TlgSettings::Get().vsync=sync;
  for(u32 m:{0,2,3,4,0}){
   s.SetFrameGeneration(m);assert(s.client.fifo==sync);assert(s.image_count==BufferCount(m));
   s.UpdateVsync();assert(s.client.fifo==sync);assert(!s.lost);
  }
 }
}
'''
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp);(p/'test.cpp').write_text(harness)
            compiled=subprocess.run(['c++','-std=c++17',str(p/'test.cpp'),'-o',str(p/'test')],capture_output=True,text=True)
            self.assertEqual(0,compiled.returncode,compiled.stderr)
            subprocess.run([str(p/'test')],check=True)
        # Initial connection must follow the same policy as live changes.
        connect=source[source.index('client.Connect('):source.index('Present: no connection')]
        self.assertNotIn('||',connect)

    def test_actual_shell_driver_routing(self):
        source=(ROOT/'tools/android/bionic/run-bionic.sh').read_text()
        block=source[source.index('# Family-aware defaults:'):source.index('echo "ISS Android Bionic:')]
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);runtime=root/'arm64'
            for family in ['gen8','gen7']:
                file=runtime/'drivers'/family/'libvulkan_freedreno.so';file.parent.mkdir(parents=True);file.write_bytes(b'test')
            custom=root/'custom-drivers'/'abcdef'/'libvulkan_freedreno.so'
            custom.parent.mkdir(parents=True);custom.write_bytes(b'test')
            script='set -eu\nbase="$TEST_BASE"\nruntime="$base/arm64"\nchip="$TEST_CHIP"\n'+block+'\nprintf "%s" "${ISS_VULKAN_DRIVER:-system}"\n'
            for chip,expected in [('0x44050001','gen8'),('0x43050a01','gen7'),('0x06030000','gen7'),('unknown','system')]:
                env=dict(os.environ,TEST_BASE=temp,TEST_CHIP=chip,ISS_BIONIC_DRIVER='auto')
                result=subprocess.run(['sh','-c',script],env=env,capture_output=True,text=True,check=True)
                self.assertEqual(result.stdout.splitlines()[-1],str(runtime/'drivers'/expected/'libvulkan_freedreno.so') if expected!='system' else 'system')
            env.update(ISS_BIONIC_DRIVER='custom',ISS_CUSTOM_DRIVER=str(custom))
            result=subprocess.run(['sh','-c',script],env=env,capture_output=True,text=True,check=True)
            self.assertEqual(str(custom),result.stdout.splitlines()[-1])
            env['ISS_CUSTOM_DRIVER']='/tmp/not-private/libvulkan_freedreno.so'
            self.assertEqual(4,subprocess.run(['sh','-c',script],env=env,capture_output=True).returncode)

    @unittest.skipUnless(shutil.which('java'),'Java unavailable')
    def test_actual_import_validator_rejects_wrong_architecture_and_missing_hal(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)
            # Minimal AArch64 DSO with one defined HMI dynamic symbol.
            elf=bytearray(256);elf[:6]=b'\x7fELF\x02\x01';struct.pack_into('<HH',elf,16,3,183)
            struct.pack_into('<Q',elf,40,64);struct.pack_into('<HH',elf,58,64,2)
            struct.pack_into('<I',elf,68,11);struct.pack_into('<QQ',elf,88,192,24)
            struct.pack_into('<I',elf,104,1);struct.pack_into('<Q',elf,120,24)
            struct.pack_into('<QQ',elf,152,216,4);elf[196]=16;struct.pack_into('<H',elf,198,1);elf[216:220]=b'HMI\0'
            (p/'driver.so').write_bytes(elf)
            test=r'''package ro.cobrabm.fexdroid;
import java.nio.file.*;
public class ImportTest{
 static void reject(byte[] b)throws Exception{try{ImportValidator.validateElf(b);throw new AssertionError("accepted invalid driver");}catch(java.io.IOException expected){}}
 public static void main(String[] args)throws Exception{
  byte[] b=Files.readAllBytes(Paths.get(args[0]));ImportValidator.validateElf(b);
  if(!AppLanguage.text("English","Português").equals("English"))throw new AssertionError("default language");
  java.lang.reflect.Field field=AppLanguage.class.getDeclaredField("portuguese");field.setAccessible(true);field.setBoolean(null,true);
  if(!AppLanguage.text("English","Português").equals("Português"))throw new AssertionError("language switch");
  field.setBoolean(null,false);
  byte[] bad=b.clone();bad[18]=62;reject(bad);bad=b.clone();bad[216]='X';reject(bad);
  bad=b.clone();bad[4]=1;reject(bad);bad=b.clone();bad[198]=0;reject(bad);reject(new byte[0]);
 }
}'''
            (p/'ImportTest.java').write_text(test)
            actual=(JAVA/'BionicDriverSettings.java').read_text()
            validator=actual[actual.index(' static void validateElf('):actual.index(' private static void importZip(')]
            (p/'ImportValidator.java').write_text('package ro.cobrabm.fexdroid; import java.io.*; import java.nio.*; final class ImportValidator {'+validator+'}')
            language=(JAVA/'AppLanguage.java').read_text()
            method=language[language.index(' static String text('):language.index(' static void show(')]
            (p/'AppLanguage.java').write_text('package ro.cobrabm.fexdroid; final class AppLanguage { private static volatile boolean portuguese;'+method+'}')
            compile=subprocess.run(['java','com.sun.tools.javac.Main','-encoding','UTF-8','-d',temp,str(p/'AppLanguage.java'),str(p/'ImportValidator.java'),str(p/'ImportTest.java')],capture_output=True,text=True)
            self.assertEqual(0,compile.returncode,compile.stderr)
            result=subprocess.run(['java','-ea','-cp',temp,'ro.cobrabm.fexdroid.ImportTest',str(p/'driver.so')],capture_output=True,text=True)
            self.assertEqual(0,result.returncode,result.stderr)

if __name__=='__main__':unittest.main()
