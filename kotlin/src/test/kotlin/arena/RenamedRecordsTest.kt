package arena

import arena.engine.Brain
import arena.engine.Kind
import arena.engine.Names
import arena.engine.Snake
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A renamed entry (names v2) keeps the best score saved under its old name, and the old record moves to the new one. */
class RenamedRecordsTest {
    @Test
    fun `the record follows the rename`() {
        val (old, new) = Names.RENAMED.entries.first()
        val file = File.createTempFile("q_tables_kotlin", ".bin").apply { deleteOnExit() }
        file.delete()
        Brain.save(file, null, arrayOf(Snake(0, old, Kind.TACTIC, null).apply { maxScore = 361 }), 1)

        val renamed = Snake(0, new, Kind.TACTIC, null)
        Brain.load(file, null, arrayOf(renamed))
        assertEquals(361, renamed.maxScore)

        Brain.save(file, null, arrayOf(renamed), 2)
        val probeOld = Snake(1, old, Kind.TACTIC, null)
        val probeNew = Snake(2, new, Kind.TACTIC, null)
        Brain.load(file, null, arrayOf(probeOld, probeNew))
        assertEquals(0, probeOld.maxScore)
        assertEquals(361, probeNew.maxScore)
    }
}
