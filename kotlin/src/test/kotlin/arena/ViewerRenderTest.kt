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
 * The viewer paints a busy game full screen, 4K and full HD (the board in the screen's shape, as `view` makes it),
 * and the end of an episode, the board reaching every edge. With the environment variable VIEWER_SCREENS set to a
 * folder, the pictures are written there to look at.
 */
class ViewerRenderTest {
    private fun game(screenW: Int, screenH: Int): Arena {
        val snakes = 18
        val w = Rules.board(snakes).first
        val h = Math.round(w.toDouble() * screenH / screenW).toInt()
        val arena = Arena(ArenaConfig(w, h, snakes, Rules.defaultFoods(snakes), 7L), Runner.referenceEntries(2).take(15), Brain(7L))
        repeat(3_000) { arena.tick() }
        return arena
    }

    @Test
    fun `the board fills the screen and every snake's standings are painted`() {
        val out = System.getenv("VIEWER_SCREENS")?.let { File(it).apply { mkdirs() } }
        for ((w, h) in listOf(3840 to 2160, 1920 to 1080)) {
            val arena = game(w, h)
            val cell = minOf(w.toDouble() / arena.width, h.toDouble() / arena.height)
            // square cells and less than one cell left over on either side: the snakes use the whole screen
            assertTrue(w - cell * arena.width < cell && h - cell * arena.height < cell, "board ${arena.width}x${arena.height} on ${w}x$h")
            val image = Viewer.render(arena, w, h)
            out?.let { ImageIO.write(image, "png", File(it, "viewer_${w}x$h.png")) }
            if (w == 3840) out?.let { ImageIO.write(Viewer.render(arena, w, h, "Episode 1 finished"), "png", File(it, "viewer_episode_end.png")) }
            arena.close()
        }
    }
}
