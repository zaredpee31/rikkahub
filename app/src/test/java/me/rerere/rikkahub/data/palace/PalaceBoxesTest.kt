package me.rerere.rikkahub.data.palace

import me.rerere.rikkahub.data.palace.PalaceBoxes.BoxView
import me.rerere.rikkahub.data.palace.PalaceBoxes.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalaceBoxesTest {
    private fun box(id: Long, closed: Boolean = false, live: Int = 1, count: Int = 0, created: Long = id, compressed: Long = 0) =
        BoxView(id, closed, live, count, created, compressed, created)

    @Test
    fun chooseCreatesUsesOrMerges() {
        assertEquals(Choice.Create(0L), PalaceBoxes.choose(emptyList()))
        // 封了的和满了的都不能再放，开新盒，前一个是最近写过总结的那个
        val sealed = box(1, closed = true, compressed = 50)
        val full = box(2, live = PalaceBoxes.LIVE_HARD_CAP, compressed = 80)
        assertEquals(Choice.Create(2L), PalaceBoxes.choose(listOf(sealed, full)))
        assertEquals(Choice.Use(3L), PalaceBoxes.choose(listOf(sealed, box(3))))
        // 几个都能放：留写过总结次数最多的，一样多就留最早的
        assertEquals(Choice.Merge(5L, listOf(4L, 6L)), PalaceBoxes.choose(listOf(box(4, count = 1), box(5, count = 2), box(6, count = 2, created = 9))))
    }

    @Test
    fun groupLinksDedupesAndDropsSelf() {
        val g = PalaceBoxes.groupLinks(listOf(1L to 2L, 1L to 2L, 1L to 3L, 4L to 4L, 5L to 1L))
        assertEquals(mapOf(1L to listOf(2L, 3L), 5L to listOf(1L)), g)
    }

    @Test
    fun parseCompressedNormalizes() {
        val reply = "<think>{\"content\":\"x\"}</think>```json\n" +
            """{"content":"${"字".repeat(1000)}","name":"和领导的冲突","tags":["领导","领导","加班"],"room":"garage","importance":15,"mood":"angry"}""" +
            "\n```"
        val c = PalaceBoxes.parseCompressed(reply)!!
        assertEquals(PalaceBoxes.SUMMARY_HARD_MAX, c.content.length)
        assertEquals("和领导的冲突", c.name)
        assertEquals(listOf("领导", "加班"), c.tags)
        assertEquals(PalaceRoom.LIVING, c.room)
        assertEquals(10, c.importance)
        assertNull(PalaceBoxes.parseCompressed("写不出来"))
        assertNull(PalaceBoxes.parseCompressed("""{"content":"","name":"x"}"""))
    }

    @Test
    fun snippetsSkipShortAndSampleEvenly() {
        assertEquals(listOf("今天加班了"), PalaceBoxes.snippets(listOf("嗯", "好的", "今天加班了")))
        val many = (0 until 100).map { "第${it}条消息内容" }
        val s = PalaceBoxes.snippets(many)
        assertEquals(PalaceBoxes.RELATED_SNIPPETS, s.size)
        assertEquals(many.first(), s.first())
    }

    @Test
    fun extractionReadsSameEventRefs() {
        val batch = listOf(PalaceMessage("a", true, "x", 1_700_000_000_000L))
        val reply = """[{"content":"a","room":"study"},
{"content":"","room":"study","sameAs":["0"]},
{"content":"b","room":"study","relatedTo":["O1","O9"],"sameAs":["0"],"eventName":"和领导的冲突","eventTags":["领导"]},
{"content":"c","room":"study","sameAs":["5","2","1"]}]"""
        val out = PalaceExtraction.parse(reply, batch, relatedCount = 2)
        assertEquals(3, out.size)
        assertEquals(listOf(1), out[1].related)
        assertEquals(listOf(0), out[1].sameAs)
        assertEquals("和领导的冲突", out[1].eventName)
        // 只能指向前面已经输出的那几条
        assertEquals(listOf(1), out[2].sameAs)
        assertTrue(PalaceExtraction.parse(reply, batch).all { it.related.isEmpty() })
    }

    @Test
    fun boxSummariesComeFirstInPlateMaterial() {
        val items = listOf(
            PalaceDigestion.Item(1, "旧的重要", "neutral", 9, 10, recordedAt = 10),
            PalaceDigestion.Item(2, "新的", "neutral", 3, 200, recordedAt = 200),
            PalaceDigestion.Item(3, "盒子总结", "neutral", 5, 50, recordedAt = 300, summary = true),
        )
        assertEquals(listOf("盒子总结", "新的", "旧的重要"), PalaceDigestion.pickMaterial(items, since = 100))
    }

    @Test
    fun roomsMapToPlates() {
        assertEquals(PalacePlate.BEDROOM, PalaceDigestion.plateOfRoom(PalaceRoom.BEDROOM))
        assertEquals(PalacePlate.RECENT, PalaceDigestion.plateOfRoom(PalaceRoom.USER))
        assertEquals(PalacePlate.RECENT, PalaceDigestion.plateOfRoom(PalaceRoom.SELF))
        assertNull(PalaceDigestion.plateOfRoom(PalaceRoom.ATTIC))
    }
}
