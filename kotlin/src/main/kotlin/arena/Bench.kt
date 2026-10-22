package arena

import arena.engine.Arena
import arena.engine.ArenaConfig
import arena.engine.Brain
import arena.engine.Kind
import arena.engine.Rules
import arena.engine.TacticEntry
import arena.loader.TacticLoader
import java.io.File
import java.util.Locale

/**
 * `bench`: a real game at full speed, timed. Default: 400 snakes on the phone's 390 x 180 board, the loaded
 * tactic files plus copies of the reference ports for the rest (or DQN snakes with --dqn N). It plays --warmup
 * turns (the JIT compiles, the snakes grow) and then times --ticks turns: the whole tick, the decide() phase (all
 * tactics) and the engine around it, plus each tactic's average call.
 *
 * --digest N prints, every N turns, a CRC of the whole game state (every snake's score, life and cells, and the
 * foods). With --no-time-limit (no move turned random by a slow call) a game depends only on its seed and its tactic
 * files, so the same command on the PC and on the phone (dalvikvm64, see README) must print the same lines: the proof
 * that a tactic plays the same game in both places.
 */
object Bench {
    fun run(o: Options, root: File): Int {
        val (w, h) = o.board(390 to 180)
        val snakes = o.int("snakes", 400)
        val dqn = o.int("dqn", 0)
        val loaded = when {
            o.flag("no-files") -> emptyList()
            // on the phone there is no compiler: the tactics come packed in the DEX (pack + phoneDex)
            o.str("precompiled", "").isNotEmpty() -> File(o.str("precompiled", "")).readLines().filter { it.isNotBlank() }.map { line ->
                val (name, cls, chars) = line.split('\t')
                val ctor = Class.forName(cls).getDeclaredConstructor()
                TacticEntry(name, "$name.kt", chars.toInt(), { ctor.newInstance() as arena.api.SnakeTactic })
            }
            else -> TacticLoader(File(root, TACTICS), File(WORK, "tactics")).loadAll { println(it) }
        }
        val wanted = snakes - dqn
        val entries = ArrayList<TacticEntry>(loaded)
        // --ref NAME fills the rest with one port only (Skeleton: almost free, so the engine's own cost shows)
        val fill = if (o.flag("fill-loaded") && loaded.isNotEmpty()) loaded // copies of the real tactics
        else o.str("ref", "").let { r -> if (r.isEmpty()) Runner.referenceEntries(1) else Runner.referenceEntries(1).filter { it.name == "ref-$r" } }
        require(fill.isNotEmpty()) { "no reference port ${o.str("ref", "")}" }
        var k = 1
        while (entries.size < wanted) {
            entries += fill.map { e -> TacticEntry(e.name + "#" + k, e.file, 0, e.factory, reference = true) }
            k++
        }
        val cfg = ArenaConfig(w, h, snakes, o.int("foods", Rules.defaultFoods(snakes)), o.long("seed", 1),
            budgetNanos = o.long("budget-us", 0L).takeIf { it > 0 }?.times(1000),
            threads = o.int("threads", 1), enforceTime = !o.flag("no-time-limit"),
            callClock = if (o.flag("cpu-clock")) cpuClock ?: { System.nanoTime() } else ({ System.nanoTime() }))
        val arena = Arena(cfg, entries.take(wanted), if (dqn > 0) Brain(1) else null)
        val warm = o.int("warmup", 2000)
        val ticks = o.int("ticks", 500)
        val t0 = System.nanoTime()
        repeat(warm) { arena.tick() }
        val warmMs = (System.nanoTime() - t0) / 1e6
        for (s in arena.snakes) { s.decCount = 0; s.decSumMs = 0.0; s.decMaxMs = 0.0; s.timeouts = 0; s.decOver = 0 }
        var total = 0L
        var decide = 0L
        var maxTick = 0L
        // --fps paces the turns as a live game would (0 = flat out); --report-sec prints each window with the
        // CPU time this process used (Linux/Android) and the hottest CPU sensor, to see heat and throttling
        val pace = Pacer(o.int("fps", 0))
        val reportNs = (o.double("report-sec", 0.0) * 1e9).toLong()
        var wStart = System.nanoTime()
        var wTicks = 0
        var wSum = 0L
        var wMax = 0L
        var wCpu = cpuNanos()
        val digestEvery = o.int("digest", 0)
        repeat(ticks) {
            pace.await()
            arena.tick()
            if (digestEvery > 0 && arena.frame % digestEvery == 0L) println("digest turn ${arena.frame} ${digest(arena)}")
            total += arena.lastTickNanos
            decide += arena.lastDecideNanos
            maxTick = maxOf(maxTick, arena.lastTickNanos)
            wTicks++
            wSum += arena.lastTickNanos
            wMax = maxOf(wMax, arena.lastTickNanos)
            val now = System.nanoTime()
            if (reportNs > 0 && now - wStart >= reportNs) {
                val cpu = cpuNanos()
                val sec = (now - wStart) / 1e9
                println(String.format(Locale.ROOT, "  [%5.0f s] %6.0f turns/s  tick %.3f ms (max %.2f)  CPU %s  %s",
                    (now - t0) / 1e9, wTicks / sec, wSum / 1e6 / wTicks, wMax / 1e6,
                    if (cpu >= 0 && wCpu >= 0) String.format(Locale.ROOT, "%.0f%% of one core", (cpu - wCpu) / 1e7 / sec) else "n/a",
                    hottest()))
                wStart = now; wTicks = 0; wSum = 0; wMax = 0; wCpu = cpu
            }
        }
        val lens = arena.snakes.map { it.body.size }
        println(String.format(Locale.ROOT, "%d snakes (%d tactics, %d DQN) on %dx%d, %d foods, %d threads, budget %.3f ms; warm-up %d turns in %.1f s",
            snakes, wanted, dqn, w, h, cfg.foods, cfg.threads, arena.budget / 1e6, warm, warmMs / 1000))
        println(String.format(Locale.ROOT, "bodies: average %.1f cells, longest %d; best score %d",
            lens.average(), lens.max(), arena.snakes.maxOf { it.bestScore }))
        println(String.format(Locale.ROOT, "per turn: %.3f ms (max %.3f) = decide %.3f ms + engine %.3f ms -> %.0f turns/s",
            total / 1e6 / ticks, maxTick / 1e6, decide / 1e6 / ticks, (total - decide) / 1e6 / ticks, ticks * 1e9 / total))
        val byTactic = arena.snakes.filter { it.kind == Kind.TACTIC }.groupBy { it.name.substringBefore('#') }
        for ((name, ss) in byTactic.toSortedMap()) {
            val calls = ss.sumOf { it.decCount }
            val ms = ss.sumOf { it.decSumMs }
            println(String.format(Locale.ROOT, "  %8.4f ms per call (max %.3f), %d timeouts  %s x%d",
                ms / maxOf(1, calls), ss.maxOf { it.decMaxMs }, ss.sumOf { it.timeouts }, name, ss.size))
        }
        arena.close()
        return 0
    }

    /** CRC32 of the game state: per snake its id, life, score and cells (head first), then the foods; and the best score. */
    fun digest(a: Arena): String {
        val crc = java.util.zip.CRC32()
        val buf = java.nio.ByteBuffer.allocate(4)
        fun put(v: Int) {
            buf.clear()
            crc.update(buf.putInt(v).array())
        }
        for (s in a.snakes) {
            put(s.id)
            put(if (s.alive) 1 else 0)
            put(s.score)
            put(s.body.size)
            for (k in 0 until s.body.size) put(s.body[k])
        }
        for (f in a.foodList()) put(f)
        return String.format(Locale.ROOT, "crc %08x best %d", crc.value, a.snakes.maxOf { it.bestScore })
    }

    /** CPU time of this process (all threads) from /proc/self/stat, in ns; -1 where there is no /proc. */
    private fun cpuNanos(): Long = runCatching {
        val f = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
        (f[11].toLong() + f[12].toLong()) * 10_000_000L // utime + stime, in 1/100 s
    }.getOrDefault(-1L)

    /** The hottest CPU sensor and the core clocks (Android/Linux), when readable. */
    private fun hottest(): String = runCatching {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }.orEmpty()
        val cpu = zones.mapNotNull { z ->
            val type = runCatching { File(z, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
            if (!type.startsWith("cpu")) return@mapNotNull null
            runCatching { File(z, "temp").readText().trim().toInt() / 1000.0 }.getOrNull()
        }
        val freq = (0..7).mapNotNull { runCatching { File("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000 }.getOrNull() }
        (if (cpu.isEmpty()) "" else String.format(Locale.ROOT, "CPU %.1f C", cpu.max())) +
            (if (freq.isEmpty()) "" else "  clocks ${freq.joinToString("/")} MHz")
    }.getOrDefault("")

    /** `pack`: compiles every tactic file here and writes a jar of their classes and their list, for phoneDex. */
    fun pack(root: File): Int {
        val out = File(WORK, "build/phone").apply { mkdirs() }
        val loader = TacticLoader(File(root, TACTICS), File(WORK, "tactics"))
        val lines = ArrayList<String>()
        java.util.jar.JarOutputStream(File(out, "tactics.jar").outputStream()).use { jar ->
            for (f in loader.files()) {
                val r = loader.load(f)
                val e = r.entry ?: continue
                val dir = r.classes ?: continue
                for (c in dir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }) {
                    jar.putNextEntry(java.util.jar.JarEntry(c.relativeTo(dir).invariantSeparatorsPath))
                    jar.write(c.readBytes())
                    jar.closeEntry()
                }
                lines += "${e.name}\t${r.className}\t${e.chars}"
            }
        }
        File(out, "tactics.txt").writeText(lines.joinToString("\n") + "\n")
        println("packed ${lines.size} tactics into ${out.path} (tactics.jar, tactics.txt); next: ./gradlew phoneDex")
        return 0
    }
}
