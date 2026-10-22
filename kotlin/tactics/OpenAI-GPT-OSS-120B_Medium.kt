class Tactic : SnakeTactic {
    // Simple BFS to the nearest food while staying safe.
    // Uses the arena‑provided marks/queue to avoid allocations.
    override fun decide(p: Perception): Int {
        // Head position
        val head = p.myBody[0]
        val w = p.width
        val h = p.height

        // Quick win: if a neighboring cell is empty and has food, take it
        for (m in p.validMoves) {
            val nx = (head % w + Move.DX[m] + w) % w
            val ny = (head / w + Move.DY[m] + h) % h
            val idx = ny * w + nx
            if (p.bodies[idx].toInt() == 0 && p.foods.contains(idx)) {
                return m
            }
        }

        // BFS setup
        val markId = p.newMark()               // unique value for this search
        val q = p.queue
        var qStart = 0
        var qEnd = 0

        // Enqueue all valid moves as start positions
        for (m in p.validMoves) {
            val nx = (head % w + Move.DX[m] + w) % w
            val ny = (head / w + Move.DY[m] + h) % h
            val idx = ny * w + nx
            // Only consider cells that are currently empty
            if (p.bodies[idx].toInt() == 0) {
                p.marks[idx] = markId
                q[qEnd++] = (m shl 28) or idx   // pack move + cell index
            }
        }

        // BFS limited to a safe number of steps to respect the time budget
        val maxSteps = 2000
        var steps = 0
        while (qStart < qEnd && steps < maxSteps) {
            val packed = q[qStart++]
            val move = packed ushr 28
            val cell = packed and 0x0FFFFFFF
            // Goal test: is this cell a food?
            if (p.foods.contains(cell)) {
                return move
            }
            // Explore neighbours
            val cx = cell % w
            val cy = cell / w
            for (nm in p.validMoves) {
                val nx = (cx + Move.DX[nm] + w) % w
                val ny = (cy + Move.DY[nm] + h) % h
                val nIdx = ny * w + nx
                // Skip if already visited or occupied by any body
                if (p.marks[nIdx] == markId) continue
                if (p.bodies[nIdx].toInt() != 0) continue
                p.marks[nIdx] = markId
                q[qEnd++] = (move shl 28) or nIdx   // keep original first move
            }
            steps++
        }

        // No path found (or budget exceeded) → fallback to first safe move
        return p.validMoves[0]
    }
}