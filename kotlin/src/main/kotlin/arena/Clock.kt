package arena

/**
 * CPU time of the calling thread on the JVM (HotSpot), for ArenaConfig.callClock (`--cpu-clock`); null where
 * unsupported or too coarse. Here and not in arena.engine: java.lang.management is not part of Android, where the app
 * gives the engine `android.os.Debug.threadCpuTimeNanos()` instead (nanoseconds on Linux).
 *
 * Windows counts a thread's CPU time in steps of the scheduler tick (15.6 ms): a 0.05 ms decision that crosses a step
 * reads 15.6 ms, above twice any budget, and was played as a random move (every snake about 6 times in a 2-hour game).
 * Such a clock is not used: the caller falls back to the wall clock, which on the PC (one thread per core) measures
 * the same thing.
 */
val cpuClock: (() -> Long)? = runCatching {
    val bean = java.lang.management.ManagementFactory.getThreadMXBean()
    if (!bean.isCurrentThreadCpuTimeSupported) return@runCatching null
    val read: () -> Long = { bean.currentThreadCpuTime }
    if (stepNanos(read) > MAX_STEP_NANOS) null else read
}.getOrNull()

/** "cpu" or "wall": which clock times the calls (for the telemetry and the start line). */
val clockName: String get() = if (cpuClock != null) "cpu" else "wall"

private const val MAX_STEP_NANOS = 1_000_000L
private const val PROBE_NANOS = 200_000_000L

/** The smallest step [read] advances by, busy-waiting at most [PROBE_NANOS] of wall time. */
private fun stepNanos(read: () -> Long): Long {
    val end = System.nanoTime() + PROBE_NANOS
    var last = read()
    var smallest = Long.MAX_VALUE
    var seen = 0
    while (seen < 3 && System.nanoTime() < end) {
        val now = read()
        if (now != last) {
            smallest = minOf(smallest, now - last)
            last = now
            seen++
        }
    }
    return smallest
}
