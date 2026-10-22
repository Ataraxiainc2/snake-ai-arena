package arena.engine

/**
 * CPython's `random` module (Mersenne Twister MT19937) for the calls SNAKES.py's engine makes: seed(int), randint,
 * choice and random(). Same seed, same calls -> the same numbers as Python, so a Kotlin game can be replayed against
 * SNAKES.py move by move (kotlin/parity).
 */
class PyRandom(seed: Long) {
    private val mt = IntArray(N)
    private var index = N + 1

    init {
        seed(seed)
    }

    /** random.seed(n) for an int n: init_by_array over the 32-bit words of |n|. */
    fun seed(seed: Long) {
        var n = if (seed < 0) -seed else seed
        val key = ArrayList<Int>()
        do {
            key.add((n and 0xffffffffL).toInt())
            n = n ushr 32
        } while (n != 0L)
        initByArray(key.toIntArray())
    }

    private fun initGenrand(s: Int) {
        mt[0] = s
        for (i in 1 until N) {
            val prev = mt[i - 1]
            mt[i] = 1812433253 * (prev xor (prev ushr 30)) + i
        }
        index = N
    }

    private fun initByArray(key: IntArray) {
        initGenrand(19650218)
        var i = 1
        var j = 0
        var k = maxOf(N, key.size)
        while (k > 0) {
            val prev = mt[i - 1]
            mt[i] = (mt[i] xor ((prev xor (prev ushr 30)) * 1664525)) + key[j] + j
            i++
            j++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            if (j >= key.size) j = 0
            k--
        }
        k = N - 1
        while (k > 0) {
            val prev = mt[i - 1]
            mt[i] = (mt[i] xor ((prev xor (prev ushr 30)) * 1566083941)) - i
            i++
            if (i >= N) { mt[0] = mt[N - 1]; i = 1 }
            k--
        }
        mt[0] = 0x80000000.toInt()
        index = N
    }

    private fun twist() {
        for (kk in 0 until N) {
            val y = (mt[kk] and UPPER) or (mt[(kk + 1) % N] and LOWER)
            mt[kk] = mt[(kk + M) % N] xor (y ushr 1) xor (if (y and 1 != 0) MATRIX_A else 0)
        }
        index = 0
    }

    /** genrand_uint32() as an unsigned value in a Long. */
    fun nextU32(): Long {
        if (index >= N) twist()
        var y = mt[index++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and 0x9d2c5680.toInt())
        y = y xor ((y shl 15) and 0xefc60000.toInt())
        y = y xor (y ushr 18)
        return y.toLong() and 0xffffffffL
    }

    /** getrandbits(k) for 0 <= k <= 32. */
    fun getrandbits(k: Int): Long = if (k == 0) 0L else nextU32() ushr (32 - k)

    /** _randbelow(n): uniform in [0, n), n >= 1 (rejection sampling on bit_length(n) bits, as CPython 3.10+). */
    fun randbelow(n: Int): Int {
        val k = 32 - Integer.numberOfLeadingZeros(n)
        var r = getrandbits(k)
        while (r >= n) r = getrandbits(k)
        return r.toInt()
    }

    /** randint(a, b), both included. */
    fun randint(a: Int, b: Int): Int = a + randbelow(b - a + 1)

    /** choice(seq) for a sequence of [size] items: the index chosen. */
    fun choiceIndex(size: Int): Int = randbelow(size)

    /** random(): a double in [0, 1) with 53 random bits. */
    fun random(): Double {
        val a = nextU32() ushr 5
        val b = nextU32() ushr 6
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0)
    }

    private companion object {
        const val N = 624
        const val M = 397
        const val MATRIX_A = 0x9908b0df.toInt()
        const val UPPER = 0x80000000.toInt()
        const val LOWER = 0x7fffffff
    }
}
