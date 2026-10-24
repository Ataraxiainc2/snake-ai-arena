package arena.engine

import java.util.Locale

/**
 * The standings, the same on the PC and the phone: every snake by its place in this episode, with no column that
 * repeats another (length is score + 1, foods add up the scores, the rules follow from the score). Two layouts:
 * [line], compact (place, name without its provider, score now, best in this episode, best ever, ranking score =
 * best x code factor, deaths in this game, * = already died in this episode), and [detail], for the pause screen: the
 * same plus deaths by cause (own body, wall, rival, hunger), the average score at death, foods in this game and the
 * average decision time. Screens refresh them every [REFRESH_MS].
 */
object Standings {
    /** How often the screens refresh the table: often enough to follow, rarely enough to cost nothing. */
    const val REFRESH_MS = 5_000L

    /** The name column's width: longer names are cut. */
    const val NAME = 22

    class Row(
        val place: Int, val snake: Int, val name: String, val now: Int, val episode: Int, val best: Int,
        val ranking: Double, val deaths: Int, val fell: Boolean,
        /** Deaths by cause: own body, wall, rival, hunger. */
        val self: Int, val wall: Int, val rival: Int, val hunger: Int,
        /** The average score at death (0 before the first death), foods in this game, ms per decision (null: no code). */
        val average: Int, val foods: Long, val ms: Double?,
    )

    /** The column names, at most 4 characters each but the ranking's and the details' 6. */
    class Labels(
        val now: String = "now", val episode: String = "ep", val best: String = "best", val ranking: String = "rank",
        val self: String = "body", val wall: String = "wall", val rival: String = "rival", val hunger: String = "hunger",
        val average: String = "avg", val foods: String = "foods",
    )

    /** Every snake, by its place in this episode; [name] is what the screen calls it. */
    fun of(a: Arena, name: (Snake) -> String = { short(it.name) }): List<Row> = a.episodeStandings().mapIndexed { i, s ->
        val ended = s.lives.filter { it.cause != "episode_reset" }
        fun dead(cause: String) = ended.count { it.cause == cause }
        Row(
            i + 1, s.id, name(s), s.score, s.episodeBest, s.bestScore, s.rankingScore, ended.size, a.fell(s),
            dead("self"), dead("wall"), dead("enemy"), dead("starvation"),
            if (ended.isEmpty()) 0 else ended.sumOf { it.score } / ended.size, s.foodsEaten,
            if (s.kind == Kind.TACTIC && s.decCount > 0) s.decSumMs / s.decCount else null,
        )
    }

    /** A tactic's file name without its provider ("Anthropic-Claude_Opus_5.5" is "Claude_Opus_5.5"). */
    fun short(name: String): String {
        val dash = name.indexOf('-')
        return if (dash > 0 && '_' !in name.substring(0, dash) && name.getOrNull(dash + 1)?.isLetter() == true) name.substring(dash + 1) else name
    }

    fun header(l: Labels = Labels()): String = String.format(
        Locale.ROOT, "%3s %-${NAME}s %4s %4s %4s %6s %3s", "#", "", l.now, l.episode, l.best, l.ranking, "†",
    )

    fun line(r: Row): String = String.format(
        Locale.ROOT, "%3d %-${NAME}s %4d %4d %4d %6.1f %3s",
        r.place, r.name.take(NAME), r.now, r.episode, r.best, r.ranking, "${r.deaths}${if (r.fell) "*" else ""}",
    )

    fun detailHeader(l: Labels = Labels()): String = header(l) + String.format(
        Locale.ROOT, " %6s %6s %6s %6s %6s %6s %6s", l.self, l.wall, l.rival, l.hunger, l.average, l.foods, "ms",
    )

    fun detail(r: Row): String = line(r) + String.format(
        Locale.ROOT, " %6d %6d %6d %6d %6d %6d %6s", r.self, r.wall, r.rival, r.hunger, r.average, r.foods,
        r.ms?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-",
    )
}
