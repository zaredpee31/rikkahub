package me.rerere.rikkahub.data.palace

import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PalaceDigestionTest {
    private fun item(id: Long) = PalaceDigestion.Item(id, "c$id", "neutral", 5, 0L)
    private val material = PalaceDigestion.Material(
        attic = listOf(item(1), item(2)),
        sills = listOf(item(10)),
        episodes = (20L..25L).map { item(it) },
    )

    @Test
    fun planRespectsCapsAndFirstActionWins() {
        val reply = """
            我想了想 [A0] 那件事。
            ```json
            [{"id":"A0","action":"resolve"},{"id":"A0","action":"fade"},{"id":"A9","action":"deepen"},
             {"id":"W0","action":"fulfill"},
             {"id":"E0","action":"worry","reflection":"w1"},{"id":"E1","action":"worry","reflection":"w2"},
             {"id":"E2","action":"worry","reflection":"w3"},{"id":"E3","action":"aspire","reflection":"s"},
             {"id":"E4","action":"distill","reflection":"d","plate_room":"study"},
             {"id":"E5","action":"distill","reflection":"u","plate_room":"user_room"},
             {"id":"A1","action":"keep"}]
            ```
        """.trimIndent()
        val plan = PalaceDigestion.plan(material, PalaceDigestion.parse(reply))
        assertEquals(listOf(1L), plan.resolve)
        assertTrue(plan.fade.isEmpty())
        assertTrue(plan.deepen.isEmpty())
        assertEquals(listOf(10L to "c10"), plan.fulfill)
        assertEquals(listOf("w1", "w2"), plan.newAttic)
        assertEquals(listOf("s"), plan.newSill)
        assertEquals(mapOf("study" to listOf("d")), plan.submissions)
        assertEquals((20L..25L).toList(), plan.digested)
    }

    @Test
    fun emptyArrayIsFineButGarbageThrows() {
        assertTrue(PalaceDigestion.parse("[]").isEmpty())
        assertThrows(PalaceDigestion.ParseException::class.java) { PalaceDigestion.parse("想不出来") }
    }

    private fun old(content: String, learned: Long) = PalacePlateEntryEntity(
        assistantId = "s", plate = PalacePlate.BEDROOM, content = content, firstLearnedAt = learned, sourceCount = 1, updatedAt = learned,
    )

    @Test
    fun fallbackKeepsOldAndDeliversSubmissionTrimmingOldest() {
        val olds = (1L..10L).map { old("旧$it", it) }
        val out = PalaceDigestion.mergePlate(PalacePlate.BEDROOM, olds, listOf("新的一条"), null, 100L)
        assertEquals(10, out.size)
        assertTrue(out.any { it.content == "新的一条" })
        assertTrue(out.none { it.content == "旧1" })
    }

    @Test
    fun submissionMissedByModelIsAddedBack() {
        val olds = listOf(old("她怕黑", 1L))
        val out = PalaceDigestion.mergePlate(PalacePlate.BEDROOM, olds, listOf("吵完架她会先来找我"), listOf("她怕黑，要开小灯" to 0), 100L)
        assertEquals(listOf("她怕黑，要开小灯", "吵完架她会先来找我"), out.map { it.content })
        assertEquals(2, out[0].sourceCount)
        assertEquals(1L, out[0].firstLearnedAt)
    }

    @Test
    fun bedroomNamingGuardOnlyBlocksNewEntries() {
        val olds = listOf(old("我们是恋人", 1L))
        val out = PalaceDigestion.mergePlate(PalacePlate.BEDROOM, olds, listOf("我们的关系是恋爱"), null, 100L)
        assertEquals(listOf("我们是恋人"), out.map { it.content })
    }

    @Test
    fun emptyMergeOutputFallsBack() {
        assertNull(PalaceDigestion.parsePlateMerge("[]"))
        assertNull(PalaceDigestion.parsePlateMerge("不知道"))
    }
}
