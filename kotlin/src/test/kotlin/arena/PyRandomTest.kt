package arena

import arena.engine.PyRandom
import kotlin.test.Test
import kotlin.test.assertEquals

/** The numbers CPython 3.13 gives for the same seeds and calls (random.Random(seed).randint / choice / random). */
class PyRandomTest {
    private class Case(val seed: Long, val r50: List<Int>, val r192: List<Int>, val choice: String, val random: List<Double>, val bits: Long)

    private val cases = listOf(
        Case(0, listOf(24, 48, 26, 2, 16, 32), listOf(124, 103, 77), "DCBB", listOf(0.28183784439970383, 0.7558042041572239), 2655874620),
        Case(1, listOf(8, 36, 48, 4, 16, 7), listOf(126, 115, 120), "DBAD", listOf(0.02834747652200631, 0.8357651039198697), 1858720390),
        Case(7, listOf(20, 9, 25, 41, 3, 4), listOf(137, 24, 93), "ABAA", listOf(0.4336456836623859, 0.06985542357461894), 389609433),
        Case(123456789, listOf(41, 28, 34, 45, 38, 25), listOf(77, 176, 119), "DCDD", listOf(0.5455817482443385, 0.207714227402927), 2577657052),
        Case(1099511627781, listOf(32, 33, 17, 42, 1, 33), listOf(11, 42, 20), "BBBB", listOf(0.6560036600388945, 0.3802657629059806), 520193590),
    )

    @Test
    fun sameNumbersAsCPython() {
        for (c in cases) {
            val r = PyRandom(c.seed)
            assertEquals(c.r50, List(6) { r.randint(0, 49) }, "seed ${c.seed} randint(0, 49)")
            assertEquals(c.r192, List(3) { r.randint(0, 191) }, "seed ${c.seed} randint(0, 191)")
            assertEquals(c.choice, String(CharArray(4) { "ABCD"[r.choiceIndex(4)] }), "seed ${c.seed} choice")
            assertEquals(c.random, List(2) { r.random() }, "seed ${c.seed} random()")
            assertEquals(c.bits, r.getrandbits(32), "seed ${c.seed} getrandbits(32)")
        }
    }
}
