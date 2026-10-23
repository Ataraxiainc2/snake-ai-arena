package arena

import arena.engine.Arena
import arena.engine.GameEvent
import arena.engine.Kind
import arena.engine.Palette
import arena.engine.Standings
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
import java.awt.Rectangle
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
 * 30 s, the record to 10 points, rule warnings, deaths), every snake's standings under the board ([Standings], the
 * same table as the phone's), a head flashing red while a rule changes for its snake, and a pause at the end of each
 * episode (Enter goes on, or it goes on by itself after 60 s). The game runs on its own thread; the window paints a
 * copy taken after each turn.
 */
object Viewer {
    private class Message(val text: String, val color: Color, val until: Long)
    private class Frame(
        val w: Int, val h: Int, val turn: Long, val episode: Int, val bodies: Array<IntArray>, val flash: BooleanArray,
        val foods: IntArray, val ranking: List<Standings.Row>, val colors: Array<Color>, val humanHead: Int, val stats: String,
        val episodeEnd: String?,
        /** The human's direction (a Move) and the hold on screen: the 3-2-1 number, or "" while only marking the player. */
        val humanDir: Int, val hold: String?,
    )

    @Volatile private var frame: Frame? = null
    @Volatile private var running = true
    @Volatile private var paused = false
    @Volatile private var goOn = false
    @Volatile private var current: Arena? = null
    @Volatile private var paintNanos = 0L

    /** The game waits until then: a 3-2-1 before each episode, or the players marked before going on after a pause. */
    @Volatile private var holdUntil = 0L
    @Volatile private var holdCountdown = false
    @Volatile private var paints = 0
    private val messages = ConcurrentLinkedQueue<Message>()

    private const val EPISODE_WAIT_MS = 60_000L
    private const val MESSAGE_MS = 5_000L
    private const val MAX_MESSAGES = 6
    private const val COUNTDOWN_MS = 3_000L
    private const val LOCATE_MS = 2_000L

    /**
     * Holds the game: a 3-2-1 ([countdown], at the start of every episode, the snakes on their new places) or 2 s
     * marking the player's snake and where it goes (after a pause), as the app does after a question.
     */
    private fun hold(countdown: Boolean) {
        holdCountdown = countdown
        holdUntil = System.currentTimeMillis() + if (countdown) COUNTDOWN_MS else LOCATE_MS
    }

    fun run(o: Options, root: File): Int {
        val events = ArrayList<String>(listOfNotNull(Launch.startLine))
        var round = 1
        var game = Session(o, root, round, events, human = o.flag("human"))
        var arena = game.arena
        // ended from outside (Ctrl+C, the Gradle run or the terminal closed, the process terminated): the game loop
        // stops and its finally saves telemetry and learning, as `run` does; Esc ends it from inside (running = false)
        val main = Thread.currentThread()
        Runtime.getRuntime().addShutdownHook(Thread {
            if (!running) return@Thread
            running = false
            Launch.stoppedFromOutside("view")
            main.join(15_000)
        })
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
                    KeyEvent.VK_P -> {
                        paused = !paused
                        if (!paused) hold(countdown = false)
                    }
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
            frame = frameOf(arena, colors, stats, episodeEnd)
            panel.repaint()
            lastPaint = System.currentTimeMillis()
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
        var episode = -1
        try {
            while (running) {
                pacer.await()
                // a new episode (the first one too): everybody on new places, a 3-2-1 before they move
                if (arena.episode != episode) {
                    episode = arena.episode
                    hold(countdown = true)
                }
                if (paused || System.currentTimeMillis() < holdUntil) {
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
                        episode = -1
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

    /** What the window paints, taken from [a] on the game thread. */
    private fun frameOf(a: Arena, colors: Array<Color>, stats: String, episodeEnd: String?): Frame {
        val n = a.snakes.size
        val now = System.currentTimeMillis()
        val blink = now / 400 % 2 == 0L
        return Frame(
            a.width, a.height, a.frame, a.episode, Array(n) { a.snakes[it].body.toArray() },
            BooleanArray(n) { val s = a.snakes[it]; blink && (s.warning || a.frame < s.flashUntil) },
            a.foodList(),
            Standings.of(a),
            colors, a.snakes.firstOrNull { it.kind == Kind.HUMAN }?.body?.head ?: -1, stats, episodeEnd,
            a.snakes.firstOrNull { it.kind == Kind.HUMAN }?.dir ?: 0,
            (holdUntil - now).takeIf { it > 0 }?.let { left -> if (holdCountdown) "${(left + 999) / 1000}" else "" },
        )
    }

    /** The screen as the window would paint [a] on a [width] x [height] screen (tests and screenshots). */
    internal fun render(a: Arena, width: Int, height: Int, episodeEnd: String? = null): java.awt.image.BufferedImage {
        val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        paint(g, frameOf(a, colorsOf(a), "", episodeEnd), width, height)
        g.dispose()
        return image
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
        // square cells, the board (the phone's 195 x 90) as large as the screen allows, at the top: on a 16:9 screen
        // it takes the whole width (3840 x 2160: 19.7 px a cell, 1772 px high) and the standings take the band below
        val board = g.transform
        val cell = minOf(width.toDouble() / f.w, height.toDouble() / f.h)
        val boardH = (cell * f.h).toInt()
        g.translate((width - cell * f.w) / 2, 0.0)
        g.scale(cell, cell)
        g.color = Color(24, 24, 24) // the board's edge
        g.draw(Rectangle2D.Double(0.0, 0.0, f.w.toDouble(), f.h.toDouble()))
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
            if (f.hold != null) locate(g, c % f.w, c / f.w, f.humanDir)
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
        // every snake's standings, each row in its snake's colour: in the band under the board, or over the board's
        // bottom as a watermark when the screen leaves no band
        val band = height - boardH
        if (f.episodeEnd == null) {
            if (band >= fm.height * MIN_BAND_LINES) table(g, f, Rectangle(0, boardH + fs / 2, width, band - fs / 2), fs * 2, 210)
            else table(g, f, Rectangle(0, height * 2 / 3, width, height / 3), fs, 150)
        }
        f.episodeEnd?.let { episodeEnd(g, f, it, width, height, fs) }
        f.hold?.takeIf { it.isNotEmpty() }?.let { n -> // the 3-2-1, large in the middle
            g.font = font.deriveFont(height / 5f)
            val cm = g.fontMetrics
            shadowed(g, n, (width - cm.stringWidth(n)) / 2, (height + cm.ascent) / 2 - cm.descent, Color.WHITE, 230)
        }
    }

    /** The player's snake made obvious while the game holds: a pulsing ring and an arrow the way it is going. */
    private fun locate(g: Graphics2D, x: Int, y: Int, dir: Int) {
        val pulse = 2.5 + 1.0 * Math.sin(System.currentTimeMillis() / 120.0)
        g.color = Color(0, 255, 120, 220)
        g.stroke = BasicStroke(0.35f)
        g.draw(java.awt.geom.Ellipse2D.Double(x + 0.5 - pulse, y + 0.5 - pulse, pulse * 2, pulse * 2))
        val dx = arena.engine.Rules.DX[dir]
        val dy = arena.engine.Rules.DY[dir]
        g.draw(java.awt.geom.Line2D.Double(x + 0.5 + dx * 1.5, y + 0.5 + dy * 1.5, x + 0.5 + dx * 6, y + 0.5 + dy * 6))
    }

    /**
     * Every snake's standings ([Standings]) inside [area], centred, as large as they fit: the font shrinks from [size]
     * to [MIN_FONT], in as many columns as needed; beyond that the last rows are left out, and said so.
     */
    private fun table(g: Graphics2D, f: Frame, area: Rectangle, size: Int, alpha: Int) {
        val rows = f.ranking
        val nameWidth = (rows.maxOfOrNull { it.name.length } ?: 4).coerceIn(4, MAX_NAME)
        val header = Standings.header(nameWidth)
        var fs = size
        while (true) {
            g.font = font.deriveFont(fs.toFloat())
            val fm = g.fontMetrics
            val perColumn = (area.height - fm.descent) / fm.height - 1
            val colW = fm.stringWidth(header) + fm.stringWidth("   ")
            val columns = maxOf(1, area.width / colW)
            if (perColumn >= 1 && (rows.size <= perColumn * columns || fs <= MIN_FONT)) {
                val used = minOf(columns, (rows.size + perColumn - 1) / perColumn)
                val shown = minOf(rows.size, perColumn * used)
                val x0 = area.x + (area.width - used * colW) / 2
                for (c in 0 until used) {
                    val x = x0 + c * colW
                    var y = area.y + fm.ascent
                    shadowed(g, header, x, y, Color.WHITE, alpha)
                    for (r in rows.subList(c * perColumn, minOf(shown, (c + 1) * perColumn))) {
                        y += fm.height
                        val line = Standings.line(r, nameWidth)
                        shadowed(g, line, x, y, f.colors[r.snake], alpha)
                    }
                }
                if (shown < rows.size) {
                    val more = "+${rows.size - shown} more"
                    shadowed(g, more, area.x + area.width - fm.stringWidth(more) - fs, area.y + area.height - fm.descent, Color.WHITE, alpha)
                }
                return
            }
            fs--
        }
    }

    /** Every snake died once: a band across the screen with the message and every snake's standings. */
    private fun episodeEnd(g: Graphics2D, f: Frame, text: String, width: Int, height: Int, fs: Int) {
        val top = height / 8
        val boxH = height * 3 / 4
        g.color = Color(0, 0, 0, 215)
        g.fillRect(0, top, width, boxH)
        g.font = font.deriveFont(fs * 1.5f)
        val fm = g.fontMetrics
        shadowed(g, text, (width - fm.stringWidth(text)) / 2, top + fs + fm.ascent, Color.WHITE, 255)
        val tableTop = top + fs + 2 * fm.height
        table(g, f, Rectangle(0, tableTop, width, top + boxH - tableTop - fs), fs * 2, 255)
    }

    private fun shadowed(g: Graphics2D, t: String, x: Int, y: Int, c: Color, alpha: Int) {
        g.color = Color(0, 0, 0, 120) // a thin shadow keeps text readable on any colour
        g.drawString(t, x + 1, y + 1)
        g.color = Color(c.red, c.green, c.blue, alpha)
        g.drawString(t, x, y)
    }

    private val ALERT = Color(255, 50, 50)
    private const val MIN_BAND_LINES = 6
    private const val MIN_FONT = 8
    private const val MAX_NAME = 34

    /** IBM Plex Mono Medium (Google Fonts, SIL Open Font License; resources/fonts/OFL.txt): small and legible. */
    private val font: Font = runCatching {
        Viewer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Medium.ttf")!!.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }.getOrElse { Font(Font.MONOSPACED, Font.PLAIN, 12) }

    /** The engine's palette (the same as the phone's): every snake a colour of its own, readable on black. */
    private fun colorsOf(arena: Arena) = Palette.forSnakes(arena.snakes).map { Color(it, true) }.toTypedArray()
}
