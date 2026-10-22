package arena

/**
 * CPU time of the calling thread on the JVM (HotSpot), for ArenaConfig.callClock (`--cpu-clock`); null where
 * unsupported. Here and not in arena.engine: java.lang.management is not part of Android, where the app gives the
 * engine `android.os.Debug.threadCpuTimeNanos()` instead.
 */
val cpuClock: (() -> Long)? = runCatching {
    val bean = java.lang.management.ManagementFactory.getThreadMXBean()
    if (bean.isCurrentThreadCpuTimeSupported) ({ bean.currentThreadCpuTime }) else null
}.getOrNull()
