class Tactic : SnakeTactic {
    // Updated tactic addressing arbiter feedback:
    // 1. Verify safety after BFS food discovery.
    // 2. Flood‑fill limited by body length + 10 and nanos budget.
    // 3. Below 100 points bodies are not considered fatal.
    // 4. Use p.marks to flag food cells for fast checks.
    override fun decide(p: Perception): Int {
        val w = p.width
        val h = p.height
        val head = p.myBody[0]
        val hx = head % w
        val hy = head / w
        val score = p.myScore

        // ---- Mark all food cells with a temporary mark ----
        val foodMark = p.newMark()
        for (f in p.foods) {
            p.marks[f] = foodMark
        }

        // ---- Determine safe moves respecting toroidal and walls ----
        val safeMoves = IntArray(4)
        var safeCount = 0
        val validSet = BooleanArray(4)
        for (m in p.validMoves) validSet[m] = true
        for (dir in 0..3) {
            if (!validSet[dir]) continue
            var nx = hx + Move.DX[dir]
            var ny = hy + Move.DY[dir]
            if (p.toroidal) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            } else {
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue // wall kill
            }
            val idx = ny * w + nx
            // Below 100 points bodies are ignored as lethal obstacles
            if (score >= 100 && p.bodies[idx].toInt() != 0) continue
            safeMoves[safeCount++] = dir
        }
        if (safeCount == 0) return p.validMoves[0]

        // ---- Immediate food capture (safe moves only) ----
        for (i in 0 until safeCount) {
            val dir = safeMoves[i]
            var nx = hx + Move.DX[dir]
            var ny = hy + Move.DY[dir]
            if (p.toroidal) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            }
            val idx = ny * w + nx
            if (p.marks[idx] == foodMark) {
                if (isMoveSafe(dir, p, w, h, score, foodMark)) return dir
            }
        }

        // ---- BFS to nearest food using all 4 directions ----
        val bfsMark = p.newMark()
        val q = p.queue
        var qStart = 0
        var qEnd = 0
        // Seed queue with safe moves, keep originating move in high bits
        for (i in 0 until safeCount) {
            val dir = safeMoves[i]
            var nx = hx + Move.DX[dir]
            var ny = hy + Move.DY[dir]
            if (p.toroidal) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            }
            val idx = ny * w + nx
            p.marks[idx] = bfsMark
            q[qEnd++] = (dir shl 28) or idx
        }
        val maxBfsSteps = 2000
        var steps = 0
        while (qStart < qEnd && steps < maxBfsSteps) {
            if (p.nanosLeft() <= 0) break
            val packed = q[qStart++]
            val firstDir = packed ushr 28
            val cell = packed and 0x0FFFFFFF
            if (p.marks[cell] == foodMark) {
                // Verify safety before committing to this direction
                if (isMoveSafe(firstDir, p, w, h, score, foodMark)) return firstDir
                // otherwise continue searching
            }
            val cx = cell % w
            val cy = cell / w
            for (dir in 0..3) {
                var nx = cx + Move.DX[dir]
                var ny = cy + Move.DY[dir]
                if (p.toroidal) {
                    nx = (nx + w) % w
                    ny = (ny + h) % h
                } else {
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                }
                val nIdx = ny * w + nx
                if (p.marks[nIdx] == bfsMark) continue
                if (score >= 100 && p.bodies[nIdx].toInt() != 0) continue
                p.marks[nIdx] = bfsMark
                q[qEnd++] = (firstDir shl 28) or nIdx
            }
            steps++
        }

        // ---- Fallback: choose safe move with largest reachable area (limited flood‑fill) ----
        var bestDir = safeMoves[0]
        var bestArea = -1
        val limitArea = p.myBody.size + 10
        for (i in 0 until safeCount) {
            val dir = safeMoves[i]
            if (!isMoveSafe(dir, p, w, h, score, foodMark, limitArea)) continue
            val area = reachableArea(dir, p, w, h, score, limitArea)
            if (area > bestArea) {
                bestArea = area
                bestDir = dir
            }
        }
        return bestDir
    }

    // Checks if moving in `dir` leaves enough free space (optional area limit).
    private fun isMoveSafe(
        dir: Int,
        p: Perception,
        w: Int,
        h: Int,
        score: Int,
        foodMark: Int,
        areaLimit: Int = Int.MAX_VALUE
    ): Boolean {
        // Simple flood‑fill limited by areaLimit and nanos budget
        var nx = (p.myBody[0] % w) + Move.DX[dir]
        var ny = (p.myBody[0] / w) + Move.DY[dir]
        if (p.toroidal) {
            nx = (nx + w) % w
            ny = (ny + h) % h
        } else {
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) return false
        }
        val startIdx = ny * w + nx
        if (score >= 100 && p.bodies[startIdx].toInt() != 0) return false
        val floodMark = p.newMark()
        val q = p.queue
        var fStart = 0
        var fEnd = 0
        p.marks[startIdx] = floodMark
        q[fEnd++] = startIdx
        var area = 0
        while (fStart < fEnd && area < areaLimit && p.nanosLeft() > 0) {
            val cur = q[fStart++]
            area++
            val cx = cur % w
            val cy = cur / w
            for (d in 0..3) {
                var fx = cx + Move.DX[d]
                var fy = cy + Move.DY[d]
                if (p.toroidal) {
                    fx = (fx + w) % w
                    fy = (fy + h) % h
                } else {
                    if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue
                }
                val fIdx = fy * w + fx
                if (p.marks[fIdx] == floodMark) continue
                if (score >= 100 && p.bodies[fIdx].toInt() != 0) continue
                p.marks[fIdx] = floodMark
                q[fEnd++] = fIdx
            }
        }
        // Consider move safe if we explored at least `areaLimit` cells (or ran out of time)
        return area >= areaLimit || p.nanosLeft() <= 0
    }

    // Returns reachable area from the cell after moving `dir`, respecting limits.
    private fun reachableArea(
        dir: Int,
        p: Perception,
        w: Int,
        h: Int,
        score: Int,
        limit: Int
    ): Int {
        var nx = (p.myBody[0] % w) + Move.DX[dir]
        var ny = (p.myBody[0] / w) + Move.DY[dir]
        if (p.toroidal) {
            nx = (nx + w) % w
            ny = (ny + h) % h
        }
        val startIdx = ny * w + nx
        if (score >= 100 && p.bodies[startIdx].toInt() != 0) return 0
        val floodMark = p.newMark()
        val q = p.queue
        var fStart = 0
        var fEnd = 0
        p.marks[startIdx] = floodMark
        q[fEnd++] = startIdx
        var area = 0
        while (fStart < fEnd && area < limit && p.nanosLeft() > 0) {
            val cur = q[fStart++]
            area++
            val cx = cur % w
            val cy = cur / w
            for (d in 0..3) {
                var fx = cx + Move.DX[d]
                var fy = cy + Move.DY[d]
                if (p.toroidal) {
                    fx = (fx + w) % w
                    fy = (fy + h) % h
                } else {
                    if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue
                }
                val fIdx = fy * w + fx
                if (p.marks[fIdx] == floodMark) continue
                if (score >= 100 && p.bodies[fIdx].toInt() != 0) continue
                p.marks[fIdx] = floodMark
                q[fEnd++] = fIdx
            }
        }
        return area
    }
}
