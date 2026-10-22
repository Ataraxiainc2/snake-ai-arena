package arena.reference

import arena.api.Move
import arena.api.Perception
import arena.api.SnakeTactic

/*
 * Exact Kotlin ports of three Python tactics from tacticas/ (same decisions on the same board, ties included), used
 * to replay whole games against SNAKES.py (kotlin/parity) and as realistic load for benchmarks. They are the
 * organizer's ports, not entries: in the Kotlin arena they play as "ref-<file name>".
 *
 * Python works in pixels, these in cells: every distance there is 20x the one here, so all comparisons agree.
 */

/** Shared scratch of the three ports: stamped cell sets (no clearing between calls) and a BFS queue. */
abstract class FloodTactic : SnakeTactic {
    protected var w = 0
    protected var h = 0
    protected var blocked = IntArray(0)
    protected var bg = 0
    private var seen = IntArray(0)
    private var sg = 0
    private var queue = IntArray(0)
    protected var head = 0
    protected var toroidal = true

    protected fun setUp(p: Perception) {
        w = p.width
        h = p.height
        if (blocked.size != w * h) {
            blocked = IntArray(w * h)
            seen = IntArray(w * h)
            queue = IntArray(w * h + 1)
            bg = 0
            sg = 0
        }
        bg++
        head = p.myBody[0]
        toroidal = p.myScore < 200
    }

    /** n(c, d): the next cell; off the board (walls on) it is the snake's own head, as in the Python files. */
    protected fun next(c: Int, d: Int): Int {
        val x = c % w + DX[d]
        val y = c / w + DY[d]
        if (toroidal) return Math.floorMod(y, h) * w + Math.floorMod(x, w)
        return if (x in 0 until w && y in 0 until h) y * w + x else head
    }

    /** D(a, c): Manhattan distance, the shorter way round on a toroidal board. */
    protected fun dist(a: Int, c: Int): Int {
        val i = Math.abs(a % w - c % w)
        val j = Math.abs(a / w - c / w)
        return if (toroidal) minOf(i, w - i) + minOf(j, h - j) else i + j
    }

    /** The four cells next to an enemy head (only those on the board can ever be tested). */
    protected fun blockAround(c: Int) {
        val x = c % w
        val y = c / w
        for (d in 0 until 4) {
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (nx in 0 until w && ny in 0 until h) blocked[ny * w + nx] = bg
        }
    }

    /**
     * T = min(F, key=K): K(f) is the distance to the food, worse if an enemy head is nearer (ties count half). The
     * nearest enemy head per food comes from the arena (Perception.foodRivalDistance, the same Manhattan distance).
     */
    protected fun target(p: Perception, fallback: Int): Int {
        val foods = p.foods
        if (foods.isEmpty()) return fallback
        val hx = head % w
        val hy = head / w
        var best = fallback
        var bestK = Int.MAX_VALUE
        for (i in foods.indices) {
            val f = foods[i]
            val d = d2(hx, hy, f % w, f / w)
            val e = p.foodRivalDistance(i)
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bestK) {
                bestK = k
                best = f
            }
        }
        return best
    }

    private fun d2(ax: Int, ay: Int, bx: Int, by: Int): Int {
        val i = if (ax > bx) ax - bx else bx - ax
        val j = if (ay > by) ay - by else by - ay
        return if (toroidal) minOf(i, w - i) + minOf(j, h - j) else i + j
    }

    /** f(c): breadth-first count of free cells from c, capped: [cap] once more than [cap] cells (or [stopAt]). */
    protected fun room(c: Int, cap: Int, stopAt: Int): Int {
        if (blocked[c] == bg) return 0
        sg++
        seen[c] = sg
        var count = 1
        queue[0] = c
        var qh = 0
        var qt = 1
        while (qh < qt) {
            val cc = queue[qh++]
            for (d in 0 until 4) {
                val k = next(cc, d)
                if (seen[k] != sg && blocked[k] != bg) {
                    seen[k] = sg
                    count++
                    queue[qt++] = k
                    if (count > cap || k == stopAt) return cap
                }
            }
        }
        return count
    }

    protected fun isFood(p: Perception, c: Int): Boolean {
        for (f in p.foods) if (f == c) return true
        return false
    }

    companion object {
        val DX = intArrayOf(0, 0, -1, 1)
        val DY = intArrayOf(-1, 1, 0, 0)
        const val NO_STOP = -1
    }
}

/** tacticas/Moonshot_AI-Kimi_K3_High.py */
class KimiK3High : FloodTactic() {
    override fun decide(p: Perception): Int {
        setUp(p)
        val b = p.myBody
        val v = p.myScore
        val tl = b[b.size - 1]
        if (v > 98) {
            val upto = if (p.previousReward != 0f) b.size else b.size - 1
            for (i in 0 until upto) blocked[b[i]] = bg
        }
        if (v > 298) {
            for (e in p.enemies) {
                val eb = e.body
                for (i in 0 until eb.size - 1) blocked[eb[i]] = bg
                blockAround(eb[0])
            }
        }
        val t = target(p, tl)
        val cap = b.size + 8
        val hungry = p.turnsWithoutFood > 800
        var best = -1
        var k0 = 0; var k1 = 0; var k2 = 0; var k3 = 0
        for (a in p.validMoves) {
            val c = next(head, a)
            val r = room(c, cap, tl)
            val x0: Int; val x1: Int; val x2: Int; val x3: Int
            if (hungry) {
                x0 = if (r > 0) 1 else 0; x1 = -dist(c, t); x2 = 0; x3 = 0
            } else {
                x0 = r; x1 = if (isFood(p, c)) 1 else 0; x2 = -dist(c, t); x3 = -dist(c, tl)
            }
            if (best < 0 || x0 > k0 || x0 == k0 && (x1 > k1 || x1 == k1 && (x2 > k2 || x2 == k2 && x3 > k3))) {
                best = a; k0 = x0; k1 = x1; k2 = x2; k3 = x3
            }
        }
        return best
    }
}

/** tacticas/Anthropic-Claude_Opus_5.5_IAN.py */
class OpusIan : FloodTactic() {
    override fun decide(p: Perception): Int {
        setUp(p)
        val b = p.myBody
        val v = p.myScore
        val tl = b[b.size - 1]
        if (v > 98) for (c in b) blocked[c] = bg
        if (v > 298) {
            for (e in p.enemies) {
                for (c in e.body) blocked[c] = bg
                blockAround(e.body[0])
            }
        }
        val t = target(p, tl)
        val cap = b.size + 8
        val hungry = p.turnsWithoutFood > 800
        var best = -1
        var k0 = 0; var k1 = 0; var k2 = 0; var k3 = 0
        for (a in p.validMoves) {
            val c = next(head, a)
            val r = room(c, cap, NO_STOP)
            val x0: Int; val x1: Int; val x2: Int; val x3: Int
            if (hungry) {
                x0 = if (r > 0) 1 else 0; x1 = -dist(c, t); x2 = 0; x3 = 0
            } else {
                x0 = r; x1 = if (isFood(p, c)) 1 else 0; x2 = -dist(c, t); x3 = -dist(c, tl)
            }
            if (best < 0 || x0 > k0 || x0 == k0 && (x1 > k1 || x1 == k1 && (x2 > k2 || x2 == k2 && x3 > k3))) {
                best = a; k0 = x0; k1 = x1; k2 = x2; k3 = x3
            }
        }
        return best
    }
}

/** tacticas/Anthropic-Claude_Opus_5.5_NAI.py */
class OpusNai : FloodTactic() {
    private var own = IntArray(0)
    private var og = 0

    override fun decide(p: Perception): Int {
        setUp(p)
        if (own.size != w * h) {
            own = IntArray(w * h)
            og = 0
        }
        og++
        val b = p.myBody
        val v = p.myScore
        if (v > 98) {
            for (i in 0 until b.size - 1) {
                blocked[b[i]] = bg
                own[b[i]] = og
            }
        }
        if (v > 298) {
            for (e in p.enemies) {
                val eb = e.body
                for (i in 0 until eb.size - 1) blocked[eb[i]] = bg
                blockAround(eb[0])
            }
        }
        val t = target(p, head)
        val cap = b.size + 3
        val tl = b[b.size - 1]
        var best = -1
        var k0 = 0; var k1 = 0; var k2 = 0; var k3 = 0
        for (a in p.validMoves) {
            val c = next(head, a)
            val x0 = room(c, cap, tl)
            val x1 = if (own[c] != og) 1 else 0
            val x2 = if (isFood(p, c)) 1 else 0
            val x3 = -dist(c, t)
            if (best < 0 || x0 > k0 || x0 == k0 && (x1 > k1 || x1 == k1 && (x2 > k2 || x2 == k2 && x3 > k3))) {
                best = a; k0 = x0; k1 = x1; k2 = x2; k3 = x3
            }
        }
        return best
    }
}

/**
 * kotlin/parity/tactics/Parity-Chaos.py (parity test only): Kimi_K3_High plus deterministic trouble: below 100 points
 * exceptions, invalid and reversing moves; from 200 on, a straight dive into the nearest wall.
 */
class ParityChaos : SnakeTactic {
    private val kimi = KimiK3High()
    private var dive = -1

    override fun decide(p: Perception): Int {
        val w = p.width
        val h = p.height
        val hd = p.myBody[0]
        val x = hd % w
        val y = hd / w
        val v = p.myScore
        val k = (x * 7 + y * 13 + v * 3 + p.myBody.size) % 29
        if (v < 200) dive = -1
        if (dive < 0 && v >= 200 && (k == 5 || k == 6)) {
            // min((distance, name)): ties go to the alphabetically first name (DOWN < LEFT < RIGHT < UP)
            val opts = listOf(x to Move.LEFT, w - 1 - x to Move.RIGHT, y to Move.UP, h - 1 - y to Move.DOWN)
            dive = opts.minWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { NAMES[it.second] }).second
        }
        if (dive >= 0 && dive in p.validMoves) return dive
        if (v < 100 && k == 0) throw IllegalStateException("chaos")
        if (v < 100 && k == 1) return 99
        if (v < 100 && k == 2) return OPPOSITE[p.myDirection]
        return kimi.decide(p)
    }

    private companion object {
        val NAMES = arrayOf("UP", "DOWN", "LEFT", "RIGHT")
        val OPPOSITE = intArrayOf(1, 0, 3, 2)
    }
}

/** kotlin/tactics/rules_kotlin.txt, the skeleton: the first move onto a cell with no body (valid, not strong). */
class Skeleton : SnakeTactic {
    override fun decide(p: Perception): Int {
        val hd = p.myBody[0]
        val x = hd % p.width
        val y = hd / p.width
        for (m in p.validMoves) {
            val nx = (x + FloodTactic.DX[m] + p.width) % p.width
            val ny = (y + FloodTactic.DY[m] + p.height) % p.height
            if (p.bodies[ny * p.width + nx].toInt() == 0) return m
        }
        return p.validMoves[0]
    }
}

/** The ports by the Python file they reproduce. */
object References {
    val all: Map<String, () -> SnakeTactic> = linkedMapOf(
        "Moonshot_AI-Kimi_K3_High" to { KimiK3High() },
        "Anthropic-Claude_Opus_5.5_IAN" to { OpusIan() },
        "Anthropic-Claude_Opus_5.5_NAI" to { OpusNai() },
        "Skeleton" to { Skeleton() },
    )

    /** Tactics that only exist for the parity test (kotlin/parity/tactics). */
    val parityOnly: Map<String, () -> SnakeTactic> = mapOf("Parity-Chaos" to { ParityChaos() })
}
