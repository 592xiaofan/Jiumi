# The daemon is started by name through app_process.
-keep class com.ybblab.jiumi.probe.ProbeMain { public static void main(java.lang.String[]); }
-keep class com.ybblab.jiumi.probe.IProbeService { *; }
-keep class com.ybblab.jiumi.probe.IProbeService$* { *; }
