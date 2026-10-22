package arena.engine

/** A snake's cells, head first, in a ring buffer: O(1) for the head going in and the tail going out or back. */
class Body {
    private var buf = IntArray(16)
    private var mask = 15
    private var start = 0
    var size = 0
        private set

    operator fun get(i: Int): Int = buf[(start + i) and mask]
    val head: Int get() = buf[start]
    val tail: Int get() = buf[(start + size - 1) and mask]

    fun reset(cell: Int) {
        start = 0
        size = 1
        buf[0] = cell
    }

    fun pushFront(cell: Int) {
        if (size == buf.size) grow()
        start = (start - 1) and mask
        buf[start] = cell
        size++
    }

    fun popBack(): Int {
        size--
        return buf[(start + size) and mask]
    }

    fun pushBack(cell: Int) {
        if (size == buf.size) grow()
        buf[(start + size) and mask] = cell
        size++
    }

    fun contains(cell: Int, from: Int = 0): Boolean {
        for (i in from until size) if (buf[(start + i) and mask] == cell) return true
        return false
    }

    fun toArray(): IntArray = IntArray(size) { buf[(start + it) and mask] }

    private fun grow() {
        val nb = IntArray(buf.size * 2)
        for (i in 0 until size) nb[i] = buf[(start + i) and mask]
        buf = nb
        mask = nb.size - 1
        start = 0
    }
}
