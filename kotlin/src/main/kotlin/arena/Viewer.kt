package arena

import arena.engine.Arena
import arena.engine.GameEvent
import arena.engine.Kind
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.Rectangle2D
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * `view`: the live arena full screen (Swing, no extra libraries; --window for a window). Arrow keys steer the human
 * snake (--human), + and - change the speed (0 = as fast as possible), P pauses, Esc quits; everything is saved as
 * in `run`. As SNAKES.py's window: messages at the top centre (top 3 every 60 s, a new leader, the best learner every
 * 30 s, the record to 10 points, rule warnings, deaths), the ranking (here at the bottom centre, as a watermark), a
 * head flashing red while a rule changes for its snake, and a pause at the end of each episode (Enter goes on, or it
 * goes on by itself after 60 s). The game runs on its own thread; the window paints a copy taken after each turn.
 */
object Viewer {
    private class Row(val rank: Int, val name: String, val score: Int, val ranking: Double, val best: Int, val snake: Int)
    private class Message(val text: String, val color: Color, val until: Long)
    private class Frame(
        val w: Int, val h: Int, val turn: Long, val episode: Int, val bodies: Array<IntArray>, val flash: BooleanArray,
        val foods: IntArray, val ranking: List<Row>, val colors: Array<Color>, val humanHead: Int, val stats: String,
        val episodeEnd: String?,
    )

    @Volatile private var frame: Frame? = null
    @Volatile private var running = true
    @Volatile private var paused = false
    @Volatile private var goOn = false
    @Volatile private var current: Arena? = null
    @Volatile private var paintNanos = 0L
    @Volatile private var paints = 0
    private val messages = ConcurrentLinkedQueue<Message>()

    private const val EPISODE_WAIT_MS = 60_000L
    private const val MESSAGE_MS = 5_000L
    private const val MAX_MESSAGES = 6

    fun run(o: Options, root: File): Int {
        val events = ArrayList<String>(listOfNotNull(Launch.startLine))
        var round = 1
        var game = Session(o, root, round, events, human = o.flag("human"))
        var arena = game.arena
        val fullScreen = !o.flag("window")
        val pacer = Pacer(o.int("fps", 15))
        val panel = object : JPanel() {
            init {
                background = Color.BLACK
                isFocusable = true
                if (!fullScreen) preferredSize = Dimension(arena.width * 8, arena.height * 8)
            }

            override fun paintComponent(g0: Graphics) {
                val t0 = System.nanoTime()
                super.paintComponent(g0)
                val f = frame ?: return
                paint(g0 as Graphics2D, f, width, height)
                paintNanos = System.nanoTime() - t0
                paints++
            }
        }
        val window = JFrame("Snake Battle Royale - Kotlin arena")
        SwingUtilities.invokeAndWait {
            window.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            window.add(panel)
            val device = window.graphicsConfiguration?.device
                ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            if (fullScreen) {
                // the whole screen, taskbar included, as SNAKES.py's pygame.FULLSCREEN
                window.isUndecorated = true
                if (device.isFullScreenSupported) device.fullScreenWindow = window
                else {
                    window.bounds = device.defaultConfiguration.bounds
                    window.isVisible = true
                }
            } else {
                window.pack()
                window.setLocationRelativeTo(null)
                window.isVisible = true
            }
            panel.requestFocusInWindow()
        }
        window.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) { running = false }
        })
        panel.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_UP -> current?.humanDir = 0
                    KeyEvent.VK_DOWN -> current?.humanDir = 1
                    KeyEvent.VK_LEFT -> current?.humanDir = 2
                    KeyEvent.VK_RIGHT -> current?.humanDir = 3
                    KeyEvent.VK_P -> paused = !paused
                    KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> goOn = true
                    KeyEvent.VK_ESCAPE -> running = false
                    KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> pacer.fps = if (pacer.fps == 0) 0 else if (pacer.fps >= 240) 0 else pacer.fps * 2
                    KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> pacer.fps = if (pacer.fps == 0) 240 else maxOf(1, pacer.fps / 2)
                }
            }
        })
        val os = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        val cores = Runtime.getRuntime().availableProcessors()
        var stats = ""
        var statTurns = 0L
        var statPaints = 0
        var statAt = System.currentTimeMillis()
        val saveMs = (o.double("save-minutes", 5.0) * 60_000).toLong()
        var lastSave = System.currentTimeMillis()
        var lastPaint = 0L
        var colors = colorsOf(arena)
        val watch = Watch()

        fun publish(episodeEnd: String? = null) {
            val a = arena
            val n = a.snakes.size
            val now = System.currentTimeMillis()
            val blink = now / 400 % 2 == 0L
            frame = Frame(
                a.width, a.height, a.frame, a.episode, Array(n) { a.snakes[it].body.toArray() },
                BooleanArray(n) { val s = a.snakes[it]; blink && (s.warning || a.frame < s.flashUntil) },
                a.foodList(),
                a.snakes.sortedByDescending { it.rankingScore }.take(20).mapIndexed { i, s ->
                    Row(i + 1, s.name, s.score, s.rankingScore, s.bestScore, s.id)
                },
                colors, a.snakes.firstOrNull { it.kind == Kind.HUMAN }?.body?.head ?: -1, stats, episodeEnd,
            )
            panel.repaint()
            lastPaint = now
        }

        fun attach(a: Arena) {
            current = a
            a.events = ConcurrentLinkedQueue()
            // the end of an episode waits for the user (Enter or Space), or goes on by itself after 60 s
            a.beforeNewEpisode = {
                goOn = false
                val until = System.currentTimeMillis() + EPISODE_WAIT_MS
                while (running && !goOn && System.currentTimeMillis() < until) {
                    val left = (until - System.currentTimeMillis() + 999) / 1000
                    publish("Episode ${a.episode + 1} finished: every snake died once.   Enter: next episode   (starts by itself in $left s)")
                    Thread.sleep(250)
                }
            }
        }
        attach(arena)
        try {
            while (running) {
                pacer.await()
                if (paused) {
                    Thread.sleep(30)
                    publish()
                    continue
                }
                arena.tick()
                watch.take(arena, colors)
                val now = System.currentTimeMillis()
                if (now - statAt >= 1000) {
                    val sec = (now - statAt) / 1000.0
                    val turns = (arena.frame - statTurns) / sec
                    val cpu = os?.processCpuLoad?.takeIf { it >= 0 }?.let { it * 100 }
                    stats = String.format(
                        Locale.ROOT, "%.0f turns/s  tick %.2f ms  draw %.1f ms  %.0f fps  CPU %s",
                        turns, arena.lastTickNanos / 1e6, paintNanos / 1e6, (paints - statPaints) / sec,
                        cpu?.let { String.format(Locale.ROOT, "%.1f%% of %d cores (%.0f%% of one)", it, cores, it * cores) } ?: "n/a",
                    )
                    statTurns = arena.frame
                    statPaints = paints
                    statAt = now
                }
                if (now - lastPaint >= 15 || pacer.fps in 1..60) publish()
                if (now - lastSave >= saveMs) {
                    lastSave = now
                    if (game.periodic()) { // a new tactic file is ready: next game, same window
                        game.save()
                        arena.close()
                        game = Session(o, root, ++round, events, human = o.flag("human"))
                        arena = game.arena
                        attach(arena)
                        colors = colorsOf(arena)
                        watch.reset()
                    }
                }
            }
        } finally {
            game.save()
            arena.close()
            SwingUtilities.invokeLater {
                window.graphicsConfiguration?.device?.let { if (it.fullScreenWindow === window) it.fullScreenWindow = null }
                window.dispose()
            }
        }
        println("[KOTLIN ARENA] closed after ${arena.frame} turns: ${game.top(3)}")
        return 0
    }

    /** SNAKES.py's centre messages, from the arena's events and from the standings. */
    private class Watch {
        private var leader = -1
        private var lastTop3 = System.currentTimeMillis()
        private var lastLearner = System.currentTimeMillis()
        private var record = Long.MAX_VALUE

        fun reset() { leader = -1; record = Long.MAX_VALUE }

        fun take(a: Arena, colors: Array<Color>) {
            val now = System.currentTimeMillis()
            val q = a.events
            while (q != null) {
                val e: GameEvent = q.poll() ?: break
                val s = a.snakes[e.snake]
                val text = when (e.kind) {
                    "warn100" -> "100 points: ${s.name}'s own body kills in 600 turns"
                    "rule100" -> "Rule 100 on for ${s.name}: its own body now kills"
                    "walls" -> "200 points: walls now kill ${s.name}"
                    "rivals" -> "300 points: every rival body now kills ${s.name}"
                    "ten" -> e.detail?.toLongOrNull()?.takeIf { it < record }?.let { record = it; "New record to 10 points: ${s.name} in $it turns" }
                    "died" -> "${s.name} died: ${e.detail}"
                    else -> null
                } ?: continue
                say(text, colors[e.snake], now)
            }
            val alive = a.snakes.filter { it.alive }
            val top = alive.maxByOrNull { it.score }
            if (top != null && top.body.size >= 5 && top.id != leader) {
                leader = top.id
                say("${top.name} takes the lead!", colors[top.id], now)
            }
            if (now - lastTop3 >= 60_000) {
                lastTop3 = now
                val t3 = a.snakes.sortedByDescending { it.rankingScore }.take(3)
                say("All-time top 3: " + t3.mapIndexed { i, s -> "${i + 1}. ${s.name} (${"%.1f".format(Locale.ROOT, s.rankingScore)})" }
                    .joinToString("  |  "), Color.WHITE, now)
            }
            if (now - lastLearner >= 30_000) {
                lastLearner = now
                a.snakes.filter { it.kind == Kind.DQN }.maxByOrNull { it.bestScore }?.let {
                    say("Top learner: ${it.name} (max ${it.bestScore} points)", colors[it.id], now)
                }
            }
        }

        private fun say(text: String, color: Color, now: Long) {
            messages.add(Message(text, color, now + MESSAGE_MS))
            while (messages.size > MAX_MESSAGES) messages.poll()
        }
    }

    private fun paint(g: Graphics2D, f: Frame, width: Int, height: Int) {
        // the board fills the whole window: 192 x 108 cells on a 16:9 screen are square (20 px at 3840 x 2160)
        val board = g.transform
        g.scale(width.toDouble() / f.w, height.toDouble() / f.h)
        g.color = Color.WHITE
        for (c in f.foods) g.fillRect(c % f.w, c / f.w, 1, 1)
        val colors = f.colors
        for (i in f.bodies.indices) {
            val b = f.bodies[i]
            g.color = colors[i]
            for (k in b.indices.reversed()) {
                val c = b[k]
                if (c < 0) continue
                if (k == 0) g.color = if (f.flash[i]) ALERT else colors[i].brighter()
                g.fillRect(c % f.w, c / f.w, 1, 1)
            }
        }
        f.humanHead.takeIf { it >= 0 }?.let { c -> // a faint ring around your head
            g.color = Color(255, 255, 255, 90)
            g.stroke = BasicStroke(0.12f)
            g.draw(Rectangle2D.Double(c % f.w - 0.4, c / f.w - 0.4, 1.8, 1.8))
        }
        g.transform = board
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val fs = maxOf(9, height / 165) // 13 px at 2160
        g.font = font.deriveFont(fs.toFloat())
        val fm = g.fontMetrics
        // top left: the game and what it costs
        shadowed(g, "turn ${f.turn}  episode ${f.episode + 1}${if (paused) "  PAUSED" else ""}   arrows  +/-  P  Esc", fs, fs + fm.ascent, Color.WHITE, 150)
        shadowed(g, f.stats, fs, fs + fm.ascent + fm.height, Color.WHITE, 150)
        // top centre: SNAKES.py's messages
        val now = System.currentTimeMillis()
        g.font = font.deriveFont(fs * 1.5f)
        val mm = g.fontMetrics
        var y = height / 40 + mm.ascent
        for (m in messages) {
            if (m.until < now) continue
            shadowed(g, m.text, (width - mm.stringWidth(m.text)) / 2, y, m.color, 200)
            y += mm.height
        }
        // bottom centre: the ranking as a watermark, each row in its snake's colour
        g.font = font.deriveFont(fs.toFloat())
        val rows = f.ranking
        val tableW = table(g, fm, rows, f.colors, 0, 0, measureOnly = true)
        table(g, fm, rows, f.colors, (width - tableW) / 2, height - fs - fm.height * rows.size + fm.ascent)
        f.episodeEnd?.let { episodeEnd(g, f, it, width, height, fs) }
    }

    /** The ranking columns (rank, name, score now, ranking score); returns its width. */
    private fun table(g: Graphics2D, fm: FontMetrics, rows: List<Row>, colors: Array<Color>, x: Int, y0: Int, measureOnly: Boolean = false, best: Boolean = false): Int {
        val w0 = fm.stringWidth("00 ")
        val w1 = rows.maxOfOrNull { fm.stringWidth(it.name) } ?: 0
        val w2 = fm.stringWidth(" 0000")
        val w3 = fm.stringWidth("  000.0")
        val w4 = if (best) fm.stringWidth("  max 0000") else 0
        if (measureOnly) return w0 + w1 + w2 + w3 + w4
        for ((k, r) in rows.withIndex()) {
            val y = y0 + fm.height * k
            val c = colors[r.snake]
            fun right(t: String, end: Int) = shadowed(g, t, end - fm.stringWidth(t), y, c, 170)
            right("${r.rank}", x + w0 - fm.stringWidth(" "))
            shadowed(g, r.name, x + w0, y, c, 170)
            right("${r.score}", x + w0 + w1 + w2)
            right(String.format(Locale.ROOT, "%.1f", r.ranking), x + w0 + w1 + w2 + w3)
            if (best) right("max ${r.best}", x + w0 + w1 + w2 + w3 + w4)
        }
        return w0 + w1 + w2 + w3 + w4
    }

    private fun episodeEnd(g: Graphics2D, f: Frame, text: String, width: Int, height: Int, fs: Int) {
        g.font = font.deriveFont(fs * 1.4f)
        val fm = g.fontMetrics
        val tableW = table(g, fm, f.ranking, f.colors, 0, 0, measureOnly = true, best = true)
        val boxH = fm.height * (f.ranking.size + 3) + 2 * fs
        val by = (height - boxH) / 2
        g.color = Color(0, 0, 0, 210) // a band across the screen behind the standings
        g.fillRect(0, by, width, boxH)
        shadowed(g, text, (width - fm.stringWidth(text)) / 2, by + fs + fm.ascent, Color.WHITE, 255)
        table(g, fm, f.ranking, f.colors, (width - tableW) / 2, by + fs + fm.ascent + 2 * fm.height, best = true)
    }

    private fun shadowed(g: Graphics2D, t: String, x: Int, y: Int, c: Color, alpha: Int) {
        g.color = Color(0, 0, 0, 120) // a thin shadow keeps text readable on any colour
        g.drawString(t, x + 1, y + 1)
        g.color = Color(c.red, c.green, c.blue, alpha)
        g.drawString(t, x, y)
    }

    private val ALERT = Color(255, 50, 50)

    /** IBM Plex Mono Medium (Google Fonts, SIL Open Font License; resources/fonts/OFL.txt): small and legible. */
    private val font: Font = runCatching {
        Viewer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Medium.ttf")!!.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }.getOrElse { Font(Font.MONOSPACED, Font.PLAIN, 12) }

    private fun colorsOf(arena: Arena) = Array(arena.snakes.size) { i ->
        if (arena.snakes[i].kind == Kind.HUMAN) Color(0, 200, 70)
        else Color.getHSBColor(((i * 0.618033988749895) % 1.0).toFloat(), 0.88f, 0.95f)
    }
}
