package arena.engine

import java.util.Locale

/**
 * The table under the board, the same on the PC and the phone: every snake by its place in this episode, what it does
 * now and what it did. Columns: place, name, score now, best in this episode, length, the rules that apply to it now
 * (free: nothing kills; warn: its own body kills soon; self: its own body kills; walls; rivals), deaths in this game
 * (* = it already died in this episode), foods in this game, then the history: best score ever and the ranking score
 * (best x code factor). The phone gives the column names and the rules in its language ([Labels]).
 */
object Standings {
    class Row(
        val place: Int, val snake: Int, val name: String, val now: Int, val episode: Int, val length: Int,
        val rule: Rule, val deaths: Int, val fell: Boolean, val foods: Long, val best: Int, val ranking: Double,
    )

    enum class Rule { FREE, WARN, SELF, WALLS, RIVALS }

    /** The column names and the rules' words, at most 7 characters each (the columns' width). */
    class Labels(
        val now: String = "now", val episode: String = "ep", val length: String = "len", val rule: String = "rule",
        val deaths: String = "deaths", val foods: String = "foods", val best: String = "best", val ranking: String = "rank",
        val rules: List<String> = listOf("free", "warn", "self", "walls", "rivals"),
    )

    /** Every snake, by its place in this episode; [name] is what the screen calls it. */
    fun of(a: Arena, name: (Snake) -> String = { it.name }): List<Row> = a.episodeStandings().mapIndexed { i, s ->
        Row(
            i + 1, s.id, name(s), s.score, s.episodeBest, s.body.size, rule(s), s.lives.count { it.cause != "episode_reset" },
            a.fell(s), s.foodsEaten, s.bestScore, s.rankingScore,
        )
    }

    private fun rule(s: Snake): Rule = when {
        s.score >= Rules.ENEMY_COLLISION -> Rule.RIVALS
        s.score >= Rules.WALL_COLLISION -> Rule.WALLS
        s.selfOn -> Rule.SELF
        s.warning -> Rule.WARN
        else -> Rule.FREE
    }

    /** The header, for names [nameWidth] characters wide. */
    fun header(nameWidth: Int, l: Labels = Labels()): String = String.format(
        Locale.ROOT, "%3s %-${nameWidth}s %5s %5s %5s %-7s %7s %6s %5s %7s",
        "#", "", l.now, l.episode, l.length, l.rule, l.deaths, l.foods, l.best, l.ranking,
    )

    /** One row, aligned with [header]. */
    fun line(r: Row, nameWidth: Int, l: Labels = Labels()): String = String.format(
        Locale.ROOT, "%3d %-${nameWidth}s %5d %5d %5d %-7s %7s %6d %5d %7.1f",
        r.place, r.name.take(nameWidth), r.now, r.episode, r.length, l.rules[r.rule.ordinal],
        "${r.deaths}${if (r.fell) "*" else " "}", r.foods, r.best, r.ranking,
    )
}
