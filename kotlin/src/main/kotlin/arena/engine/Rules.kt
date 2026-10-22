package arena.engine

/** SNAKES.py's constants, in cells and turns (SNAKES.py works in pixels with BLOCK_SIZE = 20). */
object Rules {
    const val SELF_COLLISION = 100
    const val WALL_COLLISION = 200
    const val ENEMY_COLLISION = 300
    const val SELF_WARNING_TURNS = 600
    const val STARVATION_TURNS = 1500
    const val FLOOD_CAP = 80
    const val FLOOD_CAP_DQN = 400
    const val STATE_SIZE = 23
    const val EXTRA_FOOD_ON_DEATH = 25
    const val CODE_SIZE_PENALTY = 0.05
    const val REWARD_EAT = 15.0
    const val REWARD_DIE = -15.0

    /** UP, DOWN, LEFT, RIGHT, as SNAKES.py's ACTIONS. */
    val NAMES = arrayOf("UP", "DOWN", "LEFT", "RIGHT")
    val DX = intArrayOf(0, 0, -1, 1)
    val DY = intArrayOf(-1, 1, 0, 0)
    val OPPOSITE = intArrayOf(1, 0, 3, 2)

    val PHILOSOPHERS = listOf(
        "Aristotle", "Plato", "Socrates", "Descartes", "Kant",
        "Nietzsche", "Hegel", "Schopenhauer", "Sartre", "Camus",
        "Heidegger", "Merleau-Ponty", "Foucault", "Derrida", "Simondon",
        "Rousseau", "Voltaire", "Locke", "Hume", "Berkeley",
        "Spinoza", "Leibniz", "Rawls", "Habermas", "Wittgenstein",
        "Chomsky", "Marx", "Freud", "Jaspers", "Bergson",
        "Cioran", "Baudrillard", "Deleuze", "Guattari", "Arendt",
        "Aquinas", "Confucius", "Laozi", "Zhuangzi", "Epicurus",
        "Lucretius", "Marcus Aurelius", "Seneca", "Epictetus", "Plotinus",
        "Protagoras", "Gorgias", "Parmenides", "Empedocles", "Anaximander",
    )
    const val HUMAN_NAME = "Human (You)"

    /** SNAKES.py's default_arena for the phone-sized arena: tactics + 3 snakes (min 10), 0.8 foods per snake (min 8). */
    fun defaultSnakes(tactics: Int, max: Int = 400) = minOf(max, maxOf(10, tactics + 3))
    fun defaultFoods(snakes: Int) = maxOf(8, Math.rint(snakes * 0.8).toInt())

    /**
     * Time budget per decide() call: 100 ms shared by all snakes, between 0.1 ms and 15 ms (SNAKES.py's limit).
     * 6 snakes or fewer: 15 ms; 20: 5 ms; 100: 1 ms; 400: 0.25 ms. A call that returns after twice its budget
     * plays a random move.
     */
    fun budgetNanos(snakes: Int): Long = (100_000_000L / maxOf(1, snakes)).coerceIn(100_000L, 15_000_000L)
}
