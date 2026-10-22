package arena

import arena.api.Perception
import arena.api.SnakeTactic
import arena.engine.Rules
import arena.loader.TacticLoader
import java.io.File
import kotlin.random.Random

/**
 * `check [kotlin/tactics/X.kt ...]`: what the arena does before a tactic plays, then the same battery as
 * `SNAKES.py --check` (216 random positions, both grids, every rule phase) and a crowd test (400 snakes on the
 * phone's 390 x 180 board, 0.25 ms budget). FAIL: not loaded, any error or invalid move, or a median above twice
 * the budget (those moves would be random).
 */
object Check {
    fun run(paths: List<String>, folder: File, cache: File): Int {
        val loader = TacticLoader(folder, cache)
        val files = if (paths.isEmpty()) loader.files().filter { it.length() > 0 } else paths.map { File(it) }
        if (files.isEmpty()) println("no tactic files to check")
        var ok = true
        for (f in files) {
            val rel = f.path.replace('\\', '/')
            if (!f.name.endsWith(".kt") || f.absoluteFile.parentFile?.name != "tactics" || f.absoluteFile.parentFile?.parentFile?.name != "kotlin") {
                println("FAIL $rel: entries must be a single kotlin/tactics/<Provider-Model_Name>.kt file"); ok = false; continue
            }
            val r = TacticLoader(f.parentFile, cache).load(f)
            val e = r.entry
            if (e == null) {
                println("FAIL $rel: ${r.reason}"); ok = false; continue
            }
            val (good, line) = battery(e.factory)
            println((if (good) "PASS " else "FAIL ") + "$rel: ${e.chars} chars, $line")
            ok = ok && good
        }
        return if (ok) 0 else 1
    }

    /** (passed, report) for one tactic. */
    fun battery(factory: () -> SnakeTactic): Pair<Boolean, String> {
        val t = factory()
        val rnd = Random(7)
        var errors = 0
        var invalid = 0
        var first = ""
        val times = ArrayList<Double>()
        val budget = 15_000_000L
        repeat(3) { round ->
            for ((w, h) in listOf(192 to 108, 50 to 35)) for (score in SCORES) repeat(12) {
                val p = Synthetic.perception(w, h, score, rnd)
                val (move, err, ns) = call(t, p, budget)
                if (round < 2) return@repeat // warm-up rounds (the JIT compiles the hot loops)
                if (err != null) { errors++; if (first.isEmpty()) first = err }
                if (err == null && move !in p.validMoves) invalid++
                times.add(ns / 1e6)
            }
        }
        times.sort()
        val crowdBudget = Rules.budgetNanos(400)
        val crowd = (0 until 12).map {
            Synthetic.perception(390, 180, intArrayOf(50, 150, 250, 310)[it % 4], rnd, enemies = 399, foods = 320,
                enemyScores = intArrayOf(5, 150, 250, 310), maxEnemyLen = 160)
        }
        val crowdTimes = ArrayList<Double>()
        repeat(4) { round ->
            for (p in crowd) {
                val (move, err, ns) = call(t, p, crowdBudget)
                if (round == 0) continue
                if (err != null) { errors++; if (first.isEmpty()) first = err }
                if (err == null && move !in p.validMoves) invalid++
                crowdTimes.add(ns / 1e6)
            }
        }
        crowdTimes.sort()
        val kb = memoryKb(factory, crowd[0], crowdBudget)
        val med = times[times.size / 2]
        val p99 = times[(times.size * 0.99).toInt()]
        val cMed = crowdTimes[crowdTimes.size / 2]
        val cAvg = crowdTimes.average()
        val line = "errors=$errors, invalid=$invalid, median=${f2(med)} ms, p99=${f2(p99)} ms, max=${f2(times.last())} ms; " +
            "400-snake crowd: avg ${f3(cAvg)} ms, median ${f3(cMed)} ms (budget ${f3(crowdBudget / 1e6)} ms); " +
            "memory ~$kb KB per snake (${kb * 400 / 1024} MB for 400)"
        val budgetMs = crowdBudget / 1e6
        return when {
            errors > 0 || invalid > 0 -> false to "$line  FAIL $first"
            med > 2 * 15.0 -> false to "$line  FAIL median above 30 ms"
            cMed > 2 * budgetMs -> false to "$line  FAIL crowd median above twice the budget (random moves)"
            cAvg > budgetMs -> true to "$line  WARNING: crowd average above the budget"
            kb > MEMORY_KB -> true to "$line  WARNING: above $MEMORY_KB KB per snake"
            else -> true to line
        }
    }

    /** Heap a Tactic keeps after a decide() on the phone's 390 x 180 board, in KB (average of 20 instances). */
    private fun memoryKb(factory: () -> SnakeTactic, p: Perception, budget: Long): Long {
        val rt = Runtime.getRuntime()
        fun used(): Long { repeat(3) { System.gc(); Thread.sleep(20) }; return rt.totalMemory() - rt.freeMemory() }
        val before = used()
        val keep = List(20) { factory().also { t -> call(t, p, budget) } }
        val after = used()
        check(keep.size == 20)
        return maxOf(0L, (after - before) / 20 / 1024)
    }

    /** Up to 400 Tactic instances live at once on the phone (rules_kotlin.txt section 4). */
    const val MEMORY_KB = 256

    private fun call(t: SnakeTactic, p: Perception, budget: Long): Triple<Int, String?, Long> {
        Synthetic.arm(p, budget)
        val t0 = System.nanoTime()
        return try {
            val m = t.decide(p)
            Triple(m, null, System.nanoTime() - t0)
        } catch (e: Throwable) {
            if (e is VirtualMachineError && e !is StackOverflowError) throw e
            Triple(-1, e.toString().take(200), System.nanoTime() - t0)
        }
    }

    private val SCORES = intArrayOf(0, 20, 99, 120, 199, 210, 299, 305, 420)
    private fun f2(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)
    private fun f3(v: Double) = "%.3f".format(java.util.Locale.ROOT, v)
}
