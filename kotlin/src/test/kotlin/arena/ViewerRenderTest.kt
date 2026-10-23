package arena

import arena.engine.Arena
import arena.engine.ArenaConfig
import arena.engine.Brain
import arena.engine.Rules
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The viewer paints a busy game on a 4K and a full-HD screen, and on the end of an episode, without failing. With the
 * environment variable VIEWER_SCREENS set to a folder, the pictures are written there to look at.
 */
class ViewerRenderTest {
    private fun game(): Arena {
        val snakes = 18
        val (w, h) = Rules.board(snakes)
        val arena = Arena(ArenaConfig(w, h, snakes, Rules.defaultFoods(snakes), 7L), Runner.referenceEntries(2).take(15), Brain(7L))
        repeat(3_000) { arena.tick() }
        return arena
    }

    @Test
    fun `the board and every snake's standings are painted`() {
        val arena = game()
        val out = System.getenv("VIEWER_SCREENS")?.let { File(it).apply { mkdirs() } }
        for ((w, h) in listOf(3840 to 2160, 1920 to 1080)) {
            val image = Viewer.render(arena, w, h)
            // something is painted in the band under the board (the standings)
            val boardH = (minOf(w.toDouble() / arena.width, h.toDouble() / arena.height) * arena.height).toInt()
            val lit = (boardH until h).sumOf { y -> (0 until w step 4).count { x -> image.getRGB(x, y) and 0xFFFFFF != 0 } }
            assertTrue(lit > 0, "nothing under the board at ${w}x$h")
            out?.let { ImageIO.write(image, "png", File(it, "viewer_${w}x$h.png")) }
        }
        out?.let { ImageIO.write(Viewer.render(arena, 3840, 2160, "Episode 1 finished"), "png", File(it, "viewer_episode_end.png")) }
        arena.close()
    }
}
