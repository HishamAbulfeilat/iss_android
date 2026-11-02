import ro.cobrabm.fexdroid.BionicSaveArchive;
import ro.cobrabm.fexdroid.BionicSession;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class BionicSaveArchiveTest {
 static final String TITLE="CUSA00004";
 static void put(File root,String name,byte[] bytes)throws Exception {File f=new File(root,name);f.getParentFile().mkdirs();Files.write(f.toPath(),bytes);}
 static void put(File root,String name,String text)throws Exception {put(root,name,text.getBytes("UTF-8"));}
 static String get(File root,String name)throws Exception {return new String(Files.readAllBytes(new File(root,name).toPath()),"UTF-8");}
 static byte[] zip(String... entries)throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){
   for(int i=0;i<entries.length;i+=2){z.putNextEntry(new ZipEntry(entries[i]));z.write(entries[i+1].getBytes("UTF-8"));z.closeEntry();}
  }return bytes.toByteArray();
 }
 static int load(File root,byte[] data)throws Exception {return BionicSaveArchive.importSaves(root,new ByteArrayInputStream(data),TITLE);}
 static void rejected(File root,byte[] data)throws Exception {
  try{load(root,data);throw new AssertionError("Invalid import accepted");}catch(IOException expected){}
  assert get(root,"1/"+TITLE+"/infamousssAuto0/data").equals("original");
 }
 public static void main(String[] args)throws Exception {
  File temp=Files.createTempDirectory("iss-save-").toFile(),root=new File(temp,"savedata");
  switch(args[0]){
  case "roundtrip": {
   File source=new File(temp,"source");
   put(source,"1/"+TITLE+"/infamousssAuto0/data","new");
   put(source,"1/"+TITLE+"/infamousssAuto0/empty",new byte[0]);
   put(source,"1/"+TITLE+"/infamousssAuto0.sce_sys/param.bin","metadata");
   put(source,"1/"+TITLE+".memory/memory.dat","memory");
   File large=new File(source,"1/"+TITLE+"/infamousssAuto0/large");
   try(RandomAccessFile f=new RandomAccessFile(large,"rw")){f.setLength(27*1024*1024);f.seek(f.length()-1);f.write(42);}
   ByteArrayOutputStream out=new ByteArrayOutputStream();BionicSaveArchive.exportSaves(source,out);
   boolean portableMeta=false;
   try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){
    ZipEntry entry;while((entry=z.getNextEntry())!=null){
     if(entry.getName().equals("savedata/1/"+TITLE+"/infamousssAuto0/sce_sys/param.bin"))portableMeta=true;
     assert !entry.getName().contains("infamousssAuto0.sce_sys/");
    }
   }
   assert portableMeta;
   put(root,"1/"+TITLE+"/infamousssAuto0/obsolete","remove");
   put(root,"1/"+TITLE+"/infamousssAuto1/data","keep");
   put(root,"1/CUSA99999/infamousssAuto0/data","other region");
   assert load(root,out.toByteArray())==1;
   assert get(root,"1/"+TITLE+"/infamousssAuto0/data").equals("new");
   assert !new File(root,"1/"+TITLE+"/infamousssAuto0/obsolete").exists();
   assert new File(root,"1/"+TITLE+"/infamousssAuto0/empty").isFile();
   assert new File(root,"1/"+TITLE+"/infamousssAuto0/large").length()==27*1024*1024;
   assert get(root,"1/"+TITLE+"/infamousssAuto1/data").equals("keep");
   assert get(root,"1/CUSA99999/infamousssAuto0/data").equals("other region");
   assert get(root,"1/"+TITLE+"/infamousssAuto0.sce_sys/param.bin").equals("metadata");
   assert get(root,"1/"+TITLE+".memory/memory.dat").equals("memory");
   break;
  }
  case "bachata": {
   assert load(root,zip("Bachata/savedata/1/"+TITLE+"/infamousssAuto0/data","progress",
    "Bachata/savedata/1/"+TITLE+"/infamousssAuto0/sce_sys/param.bin","meta"))==1;
   assert get(root,"1/"+TITLE+"/infamousssAuto0/data").equals("progress");
   assert get(root,"1/"+TITLE+"/infamousssAuto0.sce_sys/param.bin").equals("meta");
   assert !new File(root,"1/"+TITLE+"/infamousssAuto0/sce_sys").exists();
   assert load(root,zip("infamousssAuto1/data","loose slot"))==1;
   break;
  }
  case "invalid": {
   put(root,"1/"+TITLE+"/infamousssAuto0/data","original");
   rejected(root,zip("../../escape","bad","infamousssAuto0/data","new"));
   assert !new File(temp.getParentFile(),"escape").exists();
   rejected(root,zip("CUSA99999/infamousssAuto0/data","wrong region"));
   rejected(root,zip("a/"+TITLE+"/infamousssAuto0/data","a","b/"+TITLE+"/infamousssAuto0/data","b"));
   rejected(root,new byte[]{1,2,3});
   byte[] valid=zip("infamousssAuto0/data","x".repeat(1000));rejected(root,Arrays.copyOf(valid,45));
   assert new File(temp,"savedata").isDirectory();
   break;
  }
  case "crc": {
   put(root,"1/"+TITLE+"/infamousssAuto0/data","original");
   byte[] payload="CRC-checked-progress".getBytes("UTF-8");CRC32 crc=new CRC32();crc.update(payload);
   ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){
    ZipEntry e=new ZipEntry("infamousssAuto0/data");e.setMethod(ZipEntry.STORED);e.setSize(payload.length);e.setCrc(crc.getValue());z.putNextEntry(e);z.write(payload);z.closeEntry();
   }
   byte[] bytes=b.toByteArray();int start=30+"infamousssAuto0/data".length();bytes[start]^=1;rejected(root,bytes);break;
  }
  case "title": {
   byte[] sfo=new byte[128];ByteBuffer b=ByteBuffer.wrap(sfo).order(ByteOrder.LITTLE_ENDIAN);
   b.putInt(0,0x46535000);b.putInt(8,36);b.putInt(12,64);b.putInt(16,1);
   b.putShort(20,(short)0);b.putInt(24,10);b.putInt(32,0);
   System.arraycopy("TITLE_ID\0".getBytes("UTF-8"),0,sfo,36,9);
   System.arraycopy((TITLE+"\0").getBytes("UTF-8"),0,sfo,64,10);
   put(temp,"sce_sys/param.sfo",sfo);assert BionicSaveArchive.readTitle(temp).equals(TITLE);
   b.putInt(32,Integer.MAX_VALUE);put(temp,"sce_sys/param.sfo",sfo);
   try{BionicSaveArchive.readTitle(temp);throw new AssertionError();}catch(IOException expected){}break;
  }
  case "recovery": {
   File journal=new File(temp,".save-import-"+UUID.randomUUID());
   put(journal,"previous/1/"+TITLE+"/infamousssAuto0/data","original");
   put(journal,"replacement/1/"+TITLE+"/infamousssAuto0/data","incomplete");
   BionicSaveArchive.recover(root);
   assert get(root,"1/"+TITLE+"/infamousssAuto0/data").equals("original");
   assert !journal.exists();
   // A crash after both renames keeps the committed root, not the old journal.
   put(journal,"previous/1/"+TITLE+"/infamousssAuto0/data","obsolete");
   BionicSaveArchive.recover(root);
   assert get(root,"1/"+TITLE+"/infamousssAuto0/data").equals("original");
   assert !journal.exists();break;
  }
  case "lock": {
   try(BionicSession.Guard guard=BionicSession.acquire(temp)){
    try{BionicSession.acquire(temp);throw new AssertionError("Concurrent access accepted");}catch(IOException expected){}
   }
   try(BionicSession.Guard guard=BionicSession.acquire(temp)){}break;
  }
  default: throw new AssertionError(args[0]);
  }
 }
}
