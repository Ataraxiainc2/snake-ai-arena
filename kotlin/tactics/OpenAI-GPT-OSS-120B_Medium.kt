class Tactic : SnakeTactic {
    // Safe move selection with toroidal awareness, full 4‑direction BFS to food,
    // and flood‑fill safety check to avoid self‑enclosure.
    override fun decide(p: Perception): Int {
        val w = p.width
        val h = p.height
        val head = p.myBody[0]
        val hx = head % w
        val hy = head / w

        // ---------- Determine safe moves (respect walls & toroidal) ----------
        val safeMoves = IntArray(4)   // max 4 safe moves
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
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue   // wall kills
            }
            val idx = ny * w + nx
            if (p.bodies[idx].toInt() != 0) continue                   // occupied
            safeMoves[safeCount++] = dir
        }
        if (safeCount == 0) return p.validMoves[0]                     // no safe move

        // ---------- Immediate food capture ----------
        for (i in 0 until safeCount) {
            val dir = safeMoves[i]
            var nx = hx + Move.DX[dir]
            var ny = hy + Move.DY[dir]
            if (p.toroidal) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            }
            val idx = ny * w + nx
            if (p.foods.contains(idx)) return dir
        }

        // ---------- BFS to nearest food (all 4 directions) ----------
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
            val packed = q[qStart++]
            val firstDir = packed ushr 28
            val cell = packed and 0x0FFFFFFF
            if (p.foods.contains(cell)) return firstDir

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
                if (p.bodies[nIdx].toInt() != 0) continue
                p.marks[nIdx] = bfsMark
                q[qEnd++] = (firstDir shl 28) or nIdx
            }
            steps++
        }

        // ---------- Flood‑fill safety check for each safe move ----------
        var bestDir = safeMoves[0]
        var bestArea = -1
        for (i in 0 until safeCount) {
            val dir = safeMoves[i]
            var nx = hx + Move.DX[dir]
            var ny = hy + Move.DY[dir]
            if (p.toroidal) {
                nx = (nx + w) % w
                ny = (ny + h) % h
            }
            val startIdx = ny * w + nx

            // Flood fill using a fresh mark
            val floodMark = p.newMark()
            var fStart = 0
            var fEnd = 0
            p.marks[startIdx] = floodMark
            q[fEnd++] = startIdx
            var area = 0

            while (fStart < fEnd) {
                val cur = q[fStart++]
                area++
                val cx2 = cur % w
                val cy2 = cur / w
                for (d in 0..3) {
                    var fx = cx2 + Move.DX[d]
                    var fy = cy2 + Move.DY[d]
                    if (p.toroidal) {
                        fx = (fx + w) % w
                        fy = (fy + h) % h
                    } else {
                        if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue
                    }
                    val fIdx = fy * w + fx
                    if (p.marks[fIdx] == floodMark) continue
                    if (p.bodies[fIdx].toInt() != 0) continue
                    p.marks[fIdx] = floodMark
                    q[fEnd++] = fIdx
                }
            }

            // Prefer moves that leave at least twice the current length reachable
            val needed = p.myBody.size * 2
            if (area >= needed && area > bestArea) {
                bestArea = area
                bestDir = dir
            }
        }
        return bestDir
    }
}