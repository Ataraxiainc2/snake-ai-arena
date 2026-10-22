package arena

import arena.engine.Arena
import arena.engine.ArenaConfig
import arena.engine.TacticEntry
import arena.reference.References
import java.io.File

/**
 * Replays a game recorded from SNAKES.py by kotlin/parity/python_trace.py with the Kotlin engine and the exact ports
 * in arena.reference, and stops at the first tick where anything differs: foods (and their order), every snake's
 * score, direction, hunger, rule flags, reward, body, best score and its 23-value state.
 */
object Parity {
    class Outcome(val identical: Boolean, val ticks: Int, val message: String)

    fun run(trace: File): Boolean {
        val r = trace.bufferedReader().use { replay(it.lineSequence().iterator()) }
        println(r.message)
        return r.identical
    }

    /** Replays the trace given as its lines (header first). */
    fun replay(lines: Iterator<String>): Outcome {
        val header = lines.next().split(" ").associate { it.substringBefore("=") to it.substringAfter("=") }
        val digest = header["digest"] == "1"
        val (w, h) = header.getValue("cells").split("x").map { it.toInt() }
        val names = header.getValue("tactics").split(",")
        val entries = names.map { n ->
            val base = n.substringBefore("#")
            val f = References.all[base] ?: References.parityOnly[base] ?: error("no Kotlin port of $n in arena.reference")
            TacticEntry(n, "$n.py", 1, f)
        }
        val arena = Arena(
            ArenaConfig(w, h, names.size, header.getValue("foods").toInt(), header.getValue("seed").toLong(),
                threads = 1, enforceTime = false, fullState = !digest),
            entries,
        )
        var ticks = 0
        var maxScore = 0
        val t0 = System.nanoTime()
        while (lines.hasNext()) {
            val expected = lines.next()
            arena.tick()
            val got = line(arena, digest)
            val diff = if (digest) {
                val crc = java.util.zip.CRC32().apply { update(got.toByteArray()) }.value
                if ("%08x".format(crc) == expected) null else "CRC differs (record the trace without --digest to see what)"
            } else compare(expected, got)
            if (diff != null) {
                return Outcome(false, ticks, "MISMATCH at tick ${arena.frame}: $diff\n  python: ${expected.take(600)}\n  kotlin: ${got.take(600)}")
            }
            ticks++
            maxScore = maxOf(maxScore, arena.snakes.maxOf { it.bestScore })
        }
        val ms = (System.nanoTime() - t0) / 1e6
        val causes = arena.snakes.flatMap { s -> s.lives.map { it.cause } }.filter { it != "episode_reset" }
            .groupingBy { it }.eachCount().toSortedMap()
        arena.close()
        return Outcome(true, ticks, "IDENTICAL: $ticks ticks of ${names.size} snakes on ${w}x$h (seed ${header["seed"]}), " +
            "${arena.deathsTotal} deaths $causes, ${arena.episode} episodes, best score $maxScore, ${"%.0f".format(ms)} ms")
    }

    private fun line(a: Arena, digest: Boolean): String {
        val sb = StringBuilder()
        sb.append(a.frame).append('|')
        val foods = a.foodList()
        foods.joinTo(sb, ",")
        for (s in a.snakes) {
            sb.append('|')
            sb.append(s.score).append(';').append(s.dir).append(';').append(s.turnsWithoutFood).append(';')
            sb.append(if (s.warning) 1 else 0).append(';').append(if (s.selfOn) 1 else 0).append(';')
            if (digest) sb.append(Math.round(s.lastReward * 10)) else sb.append(s.lastReward)
            sb.append(';').append(if (s.diedLastStep) 1 else 0).append(';').append(s.maxScore).append(';')
            s.body.toArray().joinTo(sb, ",")
            if (digest) continue
            sb.append(';')
            s.prevState.joinTo(sb, ",")
        }
        return sb.toString()
    }

    /** null when equal; numbers in the state may differ by float32 rounding only. */
    private fun compare(expected: String, got: String): String? {
        val e = expected.split("|")
        val g = got.split("|")
        if (e.size != g.size) return "different number of fields"
        if (e[0] != g[0]) return "tick number"
        if (e[1] != g[1]) return "foods"
        for (i in 2 until e.size) {
            val ef = e[i].split(";")
            val gf = g[i].split(";")
            val who = "snake ${i - 2}"
            val labels = listOf("score", "direction", "turns without food", "warning", "rule on", "reward", "died", "best", "body")
            for (k in labels.indices) {
                val same = if (k == 5) ef[k].toDouble() == gf[k].toDouble() else ef[k] == gf[k]
                if (!same) return "$who ${labels[k]}: python ${ef[k].take(200)} kotlin ${gf[k].take(200)}"
            }
            val es = ef[9].split(",").map { it.toDouble() }
            val gs = gf[9].split(",").map { it.toDouble() }
            for (k in es.indices) {
                if (Math.abs(es[k] - gs[k]) > 1e-6) return "$who state[$k]: python ${es[k]} kotlin ${gs[k]}"
            }
        }
        return null
    }
}
