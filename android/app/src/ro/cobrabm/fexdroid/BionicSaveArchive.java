package ro.cobrabm.fexdroid;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Decrypted port/Bachata saves only; Android SAF streams are owned by the caller. */
public final class BionicSaveArchive {
 private static final long MAX_BYTES=1024L*1024*1024;
 private static final int MAX_ENTRIES=20000;
 private BionicSaveArchive(){}
 private static void mkdir(File f)throws IOException {if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Não foi possível criar "+f.getName());}
 private static File[] children(File f)throws IOException {File[] a=f.listFiles();if(a==null)throw new IOException("Não foi possível ler "+f.getName());Arrays.sort(a,Comparator.comparing(File::getName));return a;}
 private static void remove(File f)throws IOException {
  if(!f.exists())return;
  if(Files.isSymbolicLink(f.toPath()))throw new IOException("Link simbólico não permitido no save");
  if(f.isDirectory())for(File c:children(f))remove(c);
  if(!f.delete())throw new IOException("Não foi possível remover arquivo temporário");
 }
 private static void copy(File from,File to)throws IOException {
  if(Files.isSymbolicLink(from.toPath()))throw new IOException("Link simbólico não permitido no save");
  if(from.isDirectory()){mkdir(to);for(File c:children(from))copy(c,new File(to,c.getName()));}
  else {mkdir(to.getParentFile());try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){transfer(in,out);out.getFD().sync();}}
 }
 private static void transfer(InputStream in,OutputStream out)throws IOException {byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
 public static boolean hasSaves(File root){if(!root.isDirectory())return false;File[] a=root.listFiles();if(a==null)return false;for(File f:a){if(f.isFile())return true;if(f.isDirectory()&&!Files.isSymbolicLink(f.toPath())&&hasSaves(f))return true;}return false;}
 public static void exportSaves(File root,OutputStream output)throws IOException {
  if(!hasSaves(root))throw new IOException("Ainda não há saves para exportar.");
  // Do not close the SAF stream: finish the archive before the caller closes it.
  ZipOutputStream zip=new ZipOutputStream(output);zip.setLevel(1);
  zipTree(root,"savedata/",zip);zip.finish();zip.flush();
 }
 private static void zipTree(File file,String name,ZipOutputStream zip)throws IOException {
  if(Files.isSymbolicLink(file.toPath()))throw new IOException("Link simbólico não permitido no save");
  ZipEntry e=new ZipEntry(name);e.setTime(file.lastModified());zip.putNextEntry(e);
  if(file.isDirectory()){zip.closeEntry();for(File c:children(file))zipTree(c,name+c.getName()+(c.isDirectory()?"/":""),zip);}
  else {try(InputStream in=new FileInputStream(file)){transfer(in,zip);}zip.closeEntry();}
 }
 private static void unzip(InputStream input,File target)throws IOException {
  ZipInputStream zip=new ZipInputStream(input);Set<String> seen=new HashSet<>();long total=0;int entries=0;
  ZipEntry e;byte[] b=new byte[65536];
  while((e=zip.getNextEntry())!=null){
   if(++entries>MAX_ENTRIES)throw new IOException("ZIP com arquivos demais.");
   String name=e.getName().replace('\\','/');
   if(name.length()>1024||name.startsWith("/")||name.indexOf(':')>=0||name.indexOf('\0')>=0)throw new IOException("Caminho inválido no ZIP.");
   for(String part:name.split("/"))if(part.equals(".."))throw new IOException("Caminho inválido no ZIP.");
   File out=new File(target,name).getCanonicalFile();
   if(!out.toPath().startsWith(target.getCanonicalFile().toPath())||out.equals(target.getCanonicalFile()))throw new IOException("Caminho inválido no ZIP.");
   if(!seen.add(out.getPath()))throw new IOException("Arquivo duplicado no ZIP.");
   if(e.isDirectory()){mkdir(out);continue;}
   mkdir(out.getParentFile());
   try(FileOutputStream stream=new FileOutputStream(out)){
    int n;while((n=zip.read(b))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Save descompactado excede 1 GiB.");stream.write(b,0,n);}stream.getFD().sync();
   }
   zip.closeEntry(); // validates each entry's CRC before a save can be committed
  }
  if(entries==0)throw new IOException("ZIP vazio ou inválido.");
 }
 private static boolean slot(String name){return name.matches("infamousss[A-Za-z0-9_-]*")&&name.length()<32;}
 private static boolean containsSlots(File dir)throws IOException {for(File f:children(dir))if(f.isDirectory()&&slot(f.getName())&&hasSaves(f))return true;return false;}
 private static void findRoots(File dir,String title,int depth,List<File> titles,List<File> loose)throws IOException {
  if(depth>10)return;
  if(dir.getName().matches("CUSA[0-9]{5}")){
   if(dir.getName().equals(title)&&containsSlots(dir))titles.add(dir);
   return; // Never import another region's save as the selected title.
  }
  if(containsSlots(dir))loose.add(dir);
  for(File child:children(dir))if(child.isDirectory()&&!child.getName().equals("__MACOSX"))findRoots(child,title,depth+1,titles,loose);
 }
 public static int importSaves(File root,InputStream input,String title)throws IOException {
  if(title==null||!title.matches("CUSA[0-9]{5}"))throw new IOException("Selecione a pasta do jogo antes de importar saves.");
  File parent=root.getAbsoluteFile().getParentFile();mkdir(parent);
  File work=new File(parent,".save-import-"+UUID.randomUUID());mkdir(work);
  File previous=new File(work,"previous"), replacement=new File(work,"replacement");boolean committed=false;
  try {
   File unpacked=new File(work,"unpacked");mkdir(unpacked);unzip(input,unpacked);
   List<File> titles=new ArrayList<>(),loose=new ArrayList<>();findRoots(unpacked,title,0,titles,loose);
   List<File> roots=titles.isEmpty()?loose:titles;
   if(roots.size()!=1)throw new IOException(roots.isEmpty()?"Nenhum save inFAMOUS descriptografado compatível com "+title+" encontrado no ZIP.":"ZIP ambíguo: exporte apenas um conjunto de saves.");
   File source=roots.get(0);mkdir(replacement);if(root.exists())copy(root,replacement);
   File destination=new File(replacement,"1/"+title);mkdir(destination);int count=0;
   for(File entry:children(source)){
    if(!entry.isDirectory()||!slot(entry.getName()))continue;
    if(!hasSaves(entry))throw new IOException("Pasta de save vazia: "+entry.getName());
    File dest=new File(destination,entry.getName());remove(dest);copy(entry,dest);
    File meta=new File(destination,entry.getName()+".sce_sys");remove(meta);
    File sidecar=new File(source,entry.getName()+".sce_sys");
    if(sidecar.isDirectory())copy(sidecar,meta);
    File nested=new File(dest,"sce_sys");if(nested.isDirectory()){if(!meta.exists())copy(nested,meta);remove(nested);}
    count++;
   }
   File memory=new File(source.getParentFile(),title+".memory");
   if(memory.isDirectory()){File dest=new File(destination.getParentFile(),title+".memory");remove(dest);copy(memory,dest);}
   if(count==0)throw new IOException("Nenhum slot de save reconhecido.");
   // Rename on the same filesystem: a rejected/truncated ZIP never touches the live saves.
   if(root.exists()&&!root.renameTo(previous))throw new IOException("Não foi possível preparar a troca dos saves.");
   if(!replacement.renameTo(root)){
    if(previous.exists()&&!previous.renameTo(root))throw new IOException("Falha ao restaurar saves anteriores. Cópia preservada em "+previous);
    throw new IOException("Falha ao instalar saves; os anteriores foram preservados.");
   }
   committed=true;return count;
  } finally {
   // Keep recovery data if a filesystem error prevented rollback.
   if(committed||!previous.exists())try{remove(work);}catch(IOException ignored){}
  }
 }
 public static String readTitle(File game)throws IOException {
  File sfo=new File(game,"sce_sys/param.sfo");
  if(sfo.length()<20||sfo.length()>1024*1024)throw new IOException("param.sfo inválido: selecione novamente a pasta do jogo.");
  byte[] data=new byte[(int)sfo.length()];try(DataInputStream in=new DataInputStream(new FileInputStream(sfo))){in.readFully(data);}
  try {
   ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
   if(b.getInt(0)!=0x46535000)throw new IllegalArgumentException();
   int keys=b.getInt(8),values=b.getInt(12),count=b.getInt(16);
   if(count<0||count>(data.length-20)/16)throw new IllegalArgumentException();
   for(int i=0;i<count;i++){
    int at=20+16*i,key=keys+(b.getShort(at)&65535),end=key;
    while(end<data.length&&data[end]!=0)end++;
    if(key<0||end==data.length)throw new IllegalArgumentException();
    if(!"TITLE_ID".equals(new String(data,key,end-key,StandardCharsets.UTF_8)))continue;
    int length=b.getInt(at+4),offset=values+b.getInt(at+12);
    if(length<1||length>16||offset<0||offset>data.length-length)throw new IllegalArgumentException();
    String title=new String(data,offset,length,StandardCharsets.UTF_8).replace("\0","").trim();
    if(title.matches("CUSA[0-9]{5}"))return title;
   }
  }catch(RuntimeException ignored){}
  throw new IOException("TITLE_ID não encontrado no param.sfo.");
 }
}
