class Tactic : SnakeTactic {
    private var w = 0; private var h = 0
    private var blocked = IntArray(0); private var bg = 0
    private var seen = IntArray(0); private var sg = 0
    private var queue = IntArray(0)
    private var head = 0; private var tor = true

    private fun setUp(p: Perception) {
        w = p.width; h = p.height
        if (blocked.size != w * h) {
            blocked = IntArray(w * h); seen = IntArray(w * h)
            queue = IntArray(w * h + 1); bg = 0; sg = 0
        }
        bg++; head = p.myBody[0]; tor = p.toroidal
    }

    // Next cell in direction d from c; returns own head if off-board (wall mode), matching Python port behaviour
    private fun next(c: Int, d: Int): Int {
        val x = c % w + Move.DX[d]; val y = c / w + Move.DY[d]
        if (tor) return ((y % h + h) % h) * w + ((x % w + w) % w)
        return if (x in 0 until w && y in 0 until h) y * w + x else head
    }

    private fun dist(a: Int, c: Int): Int {
        val i = if (a % w > c % w) a % w - c % w else c % w - a % w
        val j = if (a / w > c / w) a / w - c / w else c / w - a / w
        return if (tor) minOf(i, w - i) + minOf(j, h - j) else i + j
    }

    private fun blockAround(c: Int) {
        val x = c % w; val y = c / w
        for (d in 0 until 4) {
            val nx = x + Move.DX[d]; val ny = y + Move.DY[d]
            if (nx in 0 until w && ny in 0 until h) blocked[ny * w + nx] = bg
        }
    }

    // BFS flood fill from c, capped at cap; stopAt triggers early cap return (tail-chase safe-exit check)
    private fun room(c: Int, cap: Int, stopAt: Int): Int {
        if (blocked[c] == bg) return 0
        sg++; seen[c] = sg
        var count = 1; queue[0] = c; var qh = 0; var qt = 1
        while (qh < qt) {
            val cc = queue[qh++]
            for (d in 0 until 4) {
                val k = next(cc, d)
                if (seen[k] != sg && blocked[k] != bg) {
                    seen[k] = sg; count++; queue[qt++] = k
                    if (count > cap || k == stopAt) return cap
                }
            }
        }
        return count
    }

    private fun isFood(foods: IntArray, c: Int): Boolean {
        for (f in foods) if (f == c) return true; return false
    }

    // Pick best food: favour foods we reach before enemies using arena's pre-computed foodRivalDistance
    private fun target(p: Perception, fallback: Int): Int {
        val foods = p.foods; if (foods.isEmpty()) return fallback
        var best = fallback; var bestK = Int.MAX_VALUE
        for (i in foods.indices) {
            val f = foods[i]
            val dx = if (f % w > head % w) f % w - head % w else head % w - f % w
            val dy = if (f / w > head / w) f / w - head / w else head / w - f / w
            val d = if (tor) minOf(dx, w - dx) + minOf(dy, h - dy) else dx + dy
            val e = p.foodRivalDistance(i)  // arena computes this once per turn for all snakes
            val k = d + (if (e < d) 8 + 2 * (d - e) else 0) + (if (e == d) 2 else 0)
            if (k < bestK) { bestK = k; best = f }
        }
        return best
    }

    override fun decide(p: Perception): Int {
        setUp(p)
        val b = p.myBody; val v = p.myScore; val tl = b[b.size - 1]

        // Use the engine's own rule flags — more reliable than inferring from score
        val blockSelf = p.selfCollision || p.selfCollisionWarning
        val blockEnemies = p.enemyCollision

        // Build obstacle map
        if (blockSelf) {
            // If we just ate, tail stays (body didn't shrink), so include it
            val upto = if (p.previousReward >= 14f) b.size else b.size - 1
            for (i in 0 until upto) blocked[b[i]] = bg
        }
        if (blockEnemies) {
            for (e in p.enemies) {
                val eb = e.body
                // Tail is safe only if enemy didn't eat; we can't know for sure so be conservative: exclude last
                for (i in 0 until eb.size - 1) blocked[eb[i]] = bg
                blockAround(eb[0])  // all cells adjacent to enemy head are potentially lethal (head-on kill)
            }
        }

        val hungry = p.turnsWithoutFood > 700
        val t: Int
        if (hungry) {
            // Starving: ignore food competition, go straight for closest food
            var best = tl; var bestD = Int.MAX_VALUE
            for (f in p.foods) { val d = dist(head, f); if (d < bestD) { bestD = d; best = f } }
            t = best
        } else {
            t = target(p, tl)
        }

        val cap = b.size + 12  // flood cap: body length + generous margin

        var bestMove = -1
        var k0 = 0; var k1 = 0; var k2 = 0; var k3 = 0; var k4 = 0; var k5 = 0

        for (a in p.validMoves) {
            val c = next(head, a)
            // Pass tl as stopAt: room() returns cap immediately when tail is reachable,
            // meaning we always have a safe exit (standard tail-chase fallback)
            val r = room(c, cap, tl)
            if (r == 0) continue  // completely walled in — skip, handle below

            val x0: Int; val x1: Int; val x2: Int; val x3: Int; val x4: Int; val x5: Int
            if (hungry) {
                // Starving: just get to food fast, any space > 2 is acceptable
                x0 = if (r > 2) 1 else 0; x1 = -dist(c, t); x2 = 0; x3 = 0; x4 = 0; x5 = 0
            } else {
                // Normal play: prioritise space tier, then food, then proximity to target/tail, then momentum
                x0 = when { r >= cap -> 2; r >= cap / 2 -> 1; else -> 0 }
                x1 = if (isFood(p.foods, c)) 1 else 0
                x2 = -dist(c, t)
                x3 = r
                x4 = -dist(c, tl)
                x5 = if (a == p.myDirection) 1 else 0
            }
            if (bestMove < 0
                || x0 > k0
                || x0 == k0 && (x1 > k1
                || x1 == k1 && (x2 > k2
                || x2 == k2 && (x3 > k3
                || x3 == k3 && (x4 > k4
                || x4 == k4 && x5 > k5))))) {
                bestMove = a; k0 = x0; k1 = x1; k2 = x2; k3 = x3; k4 = x4; k5 = x5
            }
        }

        // All candidates had room == 0 (truly trapped): take the least-bad direction
        if (bestMove < 0) {
            var fallbackMove = p.validMoves[0]; var fallbackR = -1
            for (a in p.validMoves) {
                val c = next(head, a); val r = room(c, cap, tl)
                if (r > fallbackR) { fallbackR = r; fallbackMove = a }
            }
            return fallbackMove
        }
        return bestMove
    }
}
