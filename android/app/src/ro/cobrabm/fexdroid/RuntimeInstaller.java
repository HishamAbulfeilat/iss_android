package ro.cobrabm.fexdroid;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
final class RuntimeInstaller {
 static synchronized void install(Context c)throws Exception{
  File root=c.getFilesDir(),marker=new File(root,"runtime.sha256");String expected;try(InputStream in=c.getAssets().open("runtime.sha256")){expected=new String(read(in),StandardCharsets.UTF_8).trim();}
  if(marker.isFile()&&new String(Files.readAllBytes(marker.toPath()),StandardCharsets.UTF_8).trim().equals(expected)&&complete(root))return;
  File archive=new File(root,"runtime.tar.gz");MessageDigest sha=MessageDigest.getInstance("SHA-256");
  try(InputStream in=c.getAssets().open("runtime.tar.gz");OutputStream out=new FileOutputStream(archive)){byte[] b=new byte[262144];int n;while((n=in.read(b))>0){sha.update(b,0,n);out.write(b,0,n);}}
  StringBuilder hash=new StringBuilder();for(byte b:sha.digest())hash.append(String.format("%02x",b&255));if(!expected.equals(hash.toString()))throw new IOException("Runtime checksum mismatch.");
  Process p=new ProcessBuilder("/system/bin/tar","-xzf",archive.getPath(),"-C",root.getPath()).redirectErrorStream(true).start();String error=new String(read(p.getInputStream()),StandardCharsets.UTF_8);if(p.waitFor()!=0)throw new IOException("Extraction failed: "+error);
  if(!complete(root))throw new IOException("Extracted ARM64 runtime incomplete: loader, FEX, Vulkan, Python or startup scripts missing.");Files.write(marker.toPath(),expected.getBytes(StandardCharsets.UTF_8));archive.delete();
 }
 private static boolean complete(File root){
  String[] required={"issport/bin/iss-probe","issport/lib/libisscpu.so","issport/lib/libtlggpu.so",
   "issport/run-iss.sh","issport/launch.py","issport/bin/kgsl-chip-id","issport/scripts/prepare.py",
   "issport/scripts/link_modules.py","issport/scripts/graphics_options.py",
   "rootfs/usr/bin/Xvfb","rootfs/usr/libexec/fxshmd","rootfs/usr/bin/python3.13",
   "rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
   "rootfs/usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so",
   "rootfs/usr/lib/aarch64-linux-gnu/libvulkan_freedreno_gen8.so",
   "rootfs/usr/share/vulkan/icd.d/freedreno_gen8_icd.aarch64.json",
   "rootfs/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json",
   "rootfs/usr/lib/aarch64-linux-gnu/libpulsecommon-17.0.so"};
  for(String path:required)if(!new File(root,path).isFile())return false;
  return new File(root,"issport/bin/iss-probe").canExecute();
 }
 static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toByteArray();}
}
