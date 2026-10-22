package arena

import arena.engine.Palette
import kotlin.test.Test
import kotlin.test.assertTrue

/** The snake colours stay apart from each other and from white, alert red and the players, and read on black. */
class PaletteTest {
    private fun minDistance(colors: IntArray): Double {
        var min = Double.MAX_VALUE
        for (i in colors.indices) for (j in i + 1 until colors.size) {
            min = minOf(min, Palette.distance(Palette.lab(colors[i]), Palette.lab(colors[j])))
        }
        return min
    }

    @Test
    fun `twenty snakes and four players all look different`() {
        val all = Palette.colors(20, Palette.HUMANS) + Palette.HUMANS + intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF3232.toInt())
        val min = minDistance(all)
        // a CIELAB distance of 20 is an obvious difference at a glance
        assertTrue(min >= 20, "closest two colours are only $min apart")
    }

    @Test
    fun `no snake looks like a player`() {
        val snakes = Palette.colors(20, Palette.HUMANS)
        val min = snakes.minOf { c -> Palette.HUMANS.minOf { h -> Palette.distance(Palette.lab(c), Palette.lab(h)) } }
        assertTrue(min >= 30, "a snake is only $min from a player's colour")
    }

    @Test
    fun `every colour reads on the black board`() {
        Palette.colors(400, Palette.HUMANS).forEach { assertTrue(Palette.lab(it)[0] >= 50, "too dark: %08x".format(it)) }
    }
}
