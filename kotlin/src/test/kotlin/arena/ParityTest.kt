package arena

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A game recorded from SNAKES.py (kotlin/parity/python_trace.py --digest: 20,000 ticks, 8 snakes on 40 x 30, seed 11,
 * deaths of every kind, exceptions and invalid moves) must replay identically, tick by tick.
 */
class ParityTest {
    @Test
    fun replaysSnakesPyExactly() {
        val stream = javaClass.getResourceAsStream("/parity/chaos-40x30-seed11.txt") ?: error("missing resource")
        val r = stream.bufferedReader().use { Parity.replay(it.lineSequence().iterator()) }
        println(r.message)
        assertTrue(r.identical, r.message)
        assertTrue(r.ticks == 20_000)
    }
}
