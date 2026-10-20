package ro.cobrabm.fexdroid;
class IssHudTest {
 public static void main(String[] args)throws Exception{
  assert PerfHudMetrics.diffFrames(-1,60)==0;
  assert PerfHudMetrics.diffFrames(60,120)==60;
  assert PerfHudMetrics.diffFrames(120,0)==0;
  assert PerfHudMetrics.gpuBusyRatio("35 100")==35;
  assert PerfHudMetrics.gpuBusyRatio("0 0")==-1;
  assert PerfHudMetrics.cpuUsage(new long[]{100,40},new long[]{200,70})==70;
  assert PerfHudMetrics.processCpuUsage(0,400,1000000000L,2000000000L,8)==50;
  assert PerfHudMetrics.fpsSegment(45,88,2).equals("45→88 LSFG 2x");
  assert PerfHudMetrics.fpsSegment(45,45,2).equals("45");
  java.io.File path=java.io.File.createTempFile("iss-hud", ".ini");
  java.nio.file.Files.write(path.toPath(),"# kept\nother=unchanged\nshow_fps=0\n".getBytes());
  java.util.Map<String,String> changes=new java.util.LinkedHashMap<>();
  changes.put("show_fps","1");changes.put("overlay_corner","4");changes.put("overlay_anchor_y","2");
  assert HudConfig.update(path,changes); HudConfig c=HudConfig.load(path);
  assert c.enabled && c.anchor==4 && c.vertical==2;
  assert new String(java.nio.file.Files.readAllBytes(path.toPath())).contains("other=unchanged");
  path.delete();System.out.println("HUD rates, load counters and configuration: OK");
 }
}
