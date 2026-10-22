package arena.engine

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Snake colours that stay apart on a black board, the same on the PC and on the phone: each new colour is, of all the
 * candidates, the one farthest from every colour already taken (farthest-point sampling in CIELAB, where distance is
 * close to how different two colours look). The candidates are bright enough to read on black, and the colours
 * that mean something else are taken first, so no snake looks like them: white (food), the red of a flashing head
 * and the human players' colours. The first colours handed out are the most different; with hundreds of snakes they
 * get closer, but always as far apart as the candidates allow.
 */
object Palette {
    /** Food, the head flashing while a rule changes. */
    private val RESERVED = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF3232.toInt())

    /** Hues every 5 degrees in three saturations and two brightness levels, kept if light enough on black. */
    private val candidates: List<Int> by lazy {
        val out = ArrayList<Int>()
        for (h in 0 until 360 step 5) for (s in floatArrayOf(1f, 0.75f, 0.5f)) for (v in floatArrayOf(1f, 0.8f)) {
            val c = hsv(h.toFloat(), s, v)
            if (lab(c)[0] >= MIN_LIGHTNESS) out.add(c)
        }
        out
    }

    /** The human players' colours: green (SNAKES.py's), gold, pink and sky blue. Never given to another snake. */
    val HUMANS = intArrayOf(0xFF00C846.toInt(), 0xFFFFC400.toInt(), 0xFFFF4FD8.toInt(), 0xFF4FC3FF.toInt())

    /** A colour per snake of [snakes] (ARGB): the players theirs, every other snake its own. */
    fun forSnakes(snakes: Array<Snake>): IntArray {
        val others = colors(snakes.count { it.kind != Kind.HUMAN }, HUMANS)
        var k = 0
        return IntArray(snakes.size) { i -> if (snakes[i].kind == Kind.HUMAN) HUMANS[snakes[i].id % HUMANS.size] else others[k++] }
    }

    /** [n] colours (ARGB), as distinct from each other and from [taken] (and white and alert red) as possible. */
    fun colors(n: Int, taken: IntArray = IntArray(0)): IntArray {
        val pool = candidates
        val labs = pool.map(::lab)
        // distance from each candidate to the nearest colour taken so far
        val nearest = DoubleArray(pool.size) { Double.MAX_VALUE }
        // a colour that means something (a player, food, a flashing head) counts as nearer than it is, so the snakes
        // keep a wider berth from it than from each other
        fun take(c: DoubleArray, weight: Double = 1.0) {
            for (i in pool.indices) nearest[i] = minOf(nearest[i], distance(labs[i], c) / weight)
        }
        (RESERVED + taken).forEach { take(lab(it), RESERVED_WEIGHT) }
        return IntArray(n) {
            var best = 0
            for (i in pool.indices) if (nearest[i] > nearest[best]) best = i
            take(labs[best])
            pool[best]
        }
    }

    private const val MIN_LIGHTNESS = 50.0
    private const val RESERVED_WEIGHT = 1.8

    internal fun distance(a: DoubleArray, b: DoubleArray): Double {
        val dl = a[0] - b[0]
        val da = a[1] - b[1]
        val db = a[2] - b[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60).toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun byte(f: Float) = ((f + m) * 255 + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (byte(r) shl 16) or (byte(g) shl 8) or byte(b)
    }

    /** sRGB (ARGB int) to CIELAB, D65 white. */
    internal fun lab(argb: Int): DoubleArray {
        fun linear(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(argb shr 16 and 0xFF)
        val g = linear(argb shr 8 and 0xFF)
        val b = linear(argb and 0xFF)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }
}
