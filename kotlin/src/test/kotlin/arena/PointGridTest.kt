package arena

import arena.engine.Arena
import arena.engine.ArenaConfig
import arena.engine.FoodRace
import arena.engine.PointGrid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PointGrid must give exactly what the full scans it replaces give (Features.target's nearest food, the DQN snakes'
 * "a rival as near" check, FoodRace's nearest and second-nearest head), ties included, on every board size in use,
 * both board modes, crowded and empty boards, and points piled on one cell.
 */
class PointGridTest {
    private val boards = listOf(390 to 180, 260 to 120, 195 to 90, 130 to 60, 192 to 108, 40 to 30, 50 to 35, 12 to 8)

    @Test
    fun sameAnswersAsTheFullScans() {
        val rnd = Random(2026)
        var checked = 0
        for ((w, h) in boards) for (points in listOf(0, 1, 2, 7, 64, 400)) for (round in 0 until 6) {
            val a = Arena(ArenaConfig(w, h, 2, 1, 1), emptyList())
            val n = points
            val x = IntArray(n)
            val y = IntArray(n)
            // some rounds pile points on a few cells (ties), the others spread them out
            val cells = if (round % 2 == 0) maxOf(1, n / 3) else w * h
            val pool = IntArray(cells) { rnd.nextInt(w * h) }
            for (k in 0 until n) {
                val c = pool[rnd.nextInt(pool.size)]
                x[k] = c % w
                y[k] = c / w
            }
            val ids = IntArray(n) { it }
            val grid = PointGrid(w, h, maxOf(1, n))
            assertTrue(grid.enabled, "${w}x$h has a bucket side")
            grid.build(n, x, y, ids)
            val v = grid.Visits()
            for (q in 0 until 40) {
                val qx = rnd.nextInt(w)
                val qy = rnd.nextInt(h)
                for (tor in listOf(true, false)) {
                    // nearest: the lowest index among the nearest, as the scan in list order
                    var bestD = Long.MAX_VALUE
                    var best = -1
                    for (k in 0 until n) {
                        val d = a.dist2(qx, qy, x[k], y[k], tor)
                        if (d < bestD) {
                            bestD = d
                            best = k
                        }
                    }
                    assertEquals(best, grid.nearest(a, qx, qy, tor, v), "nearest ${w}x$h n=$n ($qx,$qy) tor=$tor")
                    // anyWithin, at distances around the real ones
                    val exclude = if (n > 0) rnd.nextInt(n) else -1
                    for (d in listOf(0L, 1L, 2L, bestD.coerceAtMost(1_000_000L), rnd.nextLong(0, 2_000L))) {
                        val want = (0 until n).any { it != exclude && a.dist2(x[it], y[it], qx, qy, tor) <= d }
                        assertEquals(want, grid.anyWithin(a, qx, qy, d, tor, exclude, v), "anyWithin ${w}x$h n=$n d=$d tor=$tor")
                    }
                    checked++
                }
            }
            // FoodRace with and without the index, on foods spread over the board
            val foods = IntArray(1 + rnd.nextInt(50)) { rnd.nextInt(w * h) }
            val heads = IntArray(n) { y[it] * w + x[it] }
            for (tor in listOf(true, false)) {
                val scan = FoodRace().apply { compute(w, h, tor, foods, heads, ids, n) }
                val fast = FoodRace().apply { compute(w, h, tor, foods, heads, ids, n, grid, grid.Visits()) }
                assertContentEquals(scan.best, fast.best, "race best ${w}x$h n=$n tor=$tor")
                assertContentEquals(scan.owner, fast.owner, "race owner ${w}x$h n=$n tor=$tor")
                assertContentEquals(scan.second, fast.second, "race second ${w}x$h n=$n tor=$tor")
            }
            a.close()
        }
        println("PointGrid: $checked queries identical to the full scans")
    }

    @Test
    fun boardsWithoutACommonSideKeepTheScans() {
        assertTrue(!PointGrid(191, 107, 100).enabled)
    }
}
