package arena.engine

import arena.api.Perception

/**
 * Perception.foodRivalDistance for every snake at once: per food, the nearest head (and its owner) and the nearest
 * head of anyone else, in Manhattan moves (the shorter way round while toroidal). One foods x snakes pass per turn
 * and board mode, instead of one per snake: with 400 snakes and 320 foods that is 128,000 distances, not 51 million.
 */
class FoodRace {
    var best = IntArray(0)
        private set
    var owner = IntArray(0)
        private set
    var second = IntArray(0)
        private set
    private var hx = IntArray(0)
    private var hy = IntArray(0)
    private var ids = IntArray(0)

    private val one = IntArray(3)

    /**
     * [heads]: cells of the heads, [headIds]: whose they are (snake ids), first [n] of them. [index], when it holds
     * exactly those heads, answers each food without scanning them all (the same numbers, see PointGrid).
     */
    fun compute(
        w: Int, h: Int, toroidal: Boolean, foods: IntArray, heads: IntArray, headIds: IntArray, n: Int,
        index: PointGrid? = null, visits: PointGrid.Visits? = null,
    ) {
        val f = foods.size
        if (best.size != f) {
            best = IntArray(f)
            owner = IntArray(f)
            second = IntArray(f)
        }
        if (index != null && visits != null && index.enabled) {
            for (i in 0 until f) {
                index.race(foods[i] % w, foods[i] / w, toroidal, visits, one)
                best[i] = one[0]
                owner[i] = one[1]
                second[i] = one[2]
            }
            return
        }
        if (hx.size < n) {
            hx = IntArray(n)
            hy = IntArray(n)
            ids = IntArray(n)
        }
        for (k in 0 until n) {
            hx[k] = heads[k] % w
            hy[k] = heads[k] / w
            ids[k] = headIds[k]
        }
        for (i in 0 until f) {
            val fx = foods[i] % w
            val fy = foods[i] / w
            var b = Int.MAX_VALUE
            var s = Int.MAX_VALUE
            var o = -1
            for (k in 0 until n) {
                var dx = if (hx[k] > fx) hx[k] - fx else fx - hx[k]
                var dy = if (hy[k] > fy) hy[k] - fy else fy - hy[k]
                if (toroidal) {
                    if (w - dx < dx) dx = w - dx
                    if (h - dy < dy) dy = h - dy
                }
                val d = dx + dy
                if (d < b) {
                    s = b
                    b = d
                    o = ids[k]
                } else if (d < s) {
                    s = d
                }
            }
            best[i] = b
            owner[i] = o
            second[i] = s
        }
    }

    fun attach(p: Perception) {
        p.rivalBest = best
        p.rivalOwner = owner
        p.rivalSecond = second
    }
}
