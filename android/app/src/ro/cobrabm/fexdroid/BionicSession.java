package ro.cobrabm.fexdroid;

import java.io.*;
import java.nio.channels.*;

/** One cross-Activity/process lock for the game and save import/export. */
public final class BionicSession {
 private BionicSession(){}
 public static Guard acquire(File files)throws IOException {
  RandomAccessFile file=new RandomAccessFile(new File(files,"iss-bionic-session.lock"),"rw");
  try {
   FileLock lock=file.getChannel().tryLock();
   if(lock==null)throw new IOException("Feche o jogo e aguarde a operação atual terminar antes de acessar os saves.");
   return new Guard(file,lock);
  }catch(OverlappingFileLockException e){file.close();throw new IOException("Feche o jogo e aguarde a operação atual terminar antes de acessar os saves.");}
  catch(IOException|RuntimeException e){file.close();throw e;}
 }
 public static final class Guard implements AutoCloseable {
  private final RandomAccessFile file;private final FileLock lock;
  Guard(RandomAccessFile file,FileLock lock){this.file=file;this.lock=lock;}
  @Override public void close()throws IOException {try{lock.release();}finally{file.close();}}
 }
}
