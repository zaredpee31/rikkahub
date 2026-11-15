package me.rerere.rikkahub.data.palace

import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import me.rerere.rikkahub.data.palace.PalaceDigestion.PlateInput
import me.rerere.rikkahub.data.palace.PalaceDigestion.PlateItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PalaceDigestionTest {
    private val bedroom = PalacePlate.BEDROOM
    private val study = PalacePlate.STUDY
    private val recent = PalacePlate.RECENT

    private fun item(id: Long) = PalaceDigestion.Item(id, "c$id", "neutral", 5, 0L)

    private val material = PalaceDigestion.Material(
        attic = listOf(item(1), item(2)),
        sills = listOf(item(3)),
        episodes = (10L..15L).map { item(it) },
        study = listOf(item(20)),
        user = listOf(item(30)),
        self = listOf(item(40), item(41)),
    )

    private fun entry(plate: String, text: String, learned: Long = 1L, count: Int = 1) = PalacePlateEntryEntity(
        assistantId = "s", plate = plate, content = text, firstLearnedAt = learned, sourceCount = count, updatedAt = learned,
    )

    @Test
    fun planRespectsCapsAndFirstActionWins() {
        val reply = """```json
[{"id":"A0","action":"resolve"},{"id":"A0","action":"deepen"},{"id":"A1","action":"fade"},
{"id":"W0","action":"fulfill"},
{"id":"E0","action":"worry","reflection":"w1"},{"id":"E1","action":"worry","reflection":"w2"},{"id":"E2","action":"worry","reflection":"w3"},
{"id":"E3","action":"distill","reflection":"d1","plate_room":"bedroom"},
{"id":"E4","action":"distill","reflection":"d2","plate_room":"user_room"},
{"id":"E5","action":"distill","reflection":"d3","plate_room":"study"}]
```"""
        val plan = PalaceDigestion.plan(material, PalaceDigestion.parse(reply))
        assertEquals(listOf(1L), plan.resolve)
        assertTrue(plan.deepen.isEmpty())
        assertEquals(listOf(2L), plan.fade)
        assertEquals(listOf(3L to "c3"), plan.fulfill)
        assertEquals(listOf("w1", "w2"), plan.newAttic)
        assertEquals(2, plan.newAtticFrom.size)
        assertEquals(listOf("d1"), plan.submissions[bedroom])
        assertNull(plan.submissions[recent])
        // user_room maps to no plate now, so E4 is dropped without using a distill slot; E5 (study) is the second one
        assertEquals(listOf("d3"), plan.submissions[study])
    }

    @Test
    fun deletedSourcesDropTheirDerivedItems() {
        val plan = PalaceDigestion.Plan(
            newAttic = listOf("w1", "w2"), newAtticFrom = listOf(1L, 2L),
            newSill = listOf("s1"), newSillFrom = listOf(3L),
            submissions = mapOf(recent to listOf("r1", "r2"), bedroom to listOf("b1")),
            submissionsFrom = mapOf(recent to listOf(4L, 5L), bedroom to listOf(6L)),
        )
        val (kept, touched) = PalaceDigestion.dropDeleted(plan, setOf(1L, 4L, 6L))
        assertEquals(listOf("w1"), kept.newAttic)
        assertTrue(kept.newSill.isEmpty())
        assertEquals(listOf("r1"), kept.submissions[recent])
        assertEquals(listOf("b1"), kept.submissions[bedroom])
        assertEquals(setOf(recent), touched)
    }

    @Test
    fun reviewedCandidatesDoNotGoToRecentButAreMarked() {
        val reply = """[{"id":"S0","action":"internalize","reflection":"内化"},
{"id":"U0","action":"synthesize_user","reflection":"她最近很累"},
{"id":"R0","action":"self_insight","insight":"想明白了","reflection":"嗯"},
{"id":"R1","action":"self_confuse","reflection":"我不懂"}]"""
        val plan = PalaceDigestion.plan(material, PalaceDigestion.parse(reply))
        assertTrue(plan.submissions.isEmpty())
        assertNull(plan.submissions[recent])
        assertEquals(listOf(PalaceDigestion.Confuse(41L, "我不懂", 0L)), plan.selfConfuse)
        assertEquals(setOf(10L, 11L, 12L, 13L, 14L, 15L, 20L, 30L, 40L, 41L), plan.digested.toSet())
    }

    @Test
    fun emptyArrayIsFineButGarbageThrows() {
        assertTrue(PalaceDigestion.parse("[]").isEmpty())
        assertThrows(PalaceDigestion.ParseException::class.java) { PalaceDigestion.parse("想不出来") }
    }

    @Test
    fun mergeInheritsBasedOnAndDropsUnmentioned() {
        val old = listOf(entry(bedroom, "旧一", 1L, 2), entry(bedroom, "旧二", 2L, 1))
        val items = PalaceDigestion.parsePlateReply(
            """[{"room":"bedroom","text":"旧一改写","basedOn":"B0"},{"room":"bedroom","text":"全新","basedOn":null}]""",
        )
        val out = PalaceDigestion.mergeEntries(bedroom, old, items, 100L)
        assertEquals(listOf("旧一改写", "全新"), out.map { it.content })
        assertEquals(1L, out[0].firstLearnedAt)
        assertEquals(3, out[0].sourceCount)
        assertEquals(100L, out[0].updatedAt)
        assertEquals(100L, out[1].firstLearnedAt)
    }

    @Test
    fun resolveFallsBackOnlyForMissedPlatesWithSubmissions() {
        val inputs = listOf(
            PlateInput(bedroom, listOf(entry(bedroom, "旧")), emptyList()),
            PlateInput(study, listOf(entry(study, "会画画")), emptyList()),
            PlateInput(recent, emptyList(), listOf("x")),
        )
        val out = PalaceDigestion.resolvePlates(
            inputs, listOf(PlateItem(bedroom, "旧", "B0")), mapOf(recent to listOf("她这阵子在赶项目")), 100L,
        )
        assertEquals(listOf("旧"), out[bedroom]!!.map { it.content })
        assertFalse(out.containsKey(study))
        assertEquals(listOf("她这阵子在赶项目"), out[recent]!!.map { it.content })

        val failed = PalaceDigestion.resolvePlates(inputs, emptyList(), mapOf(bedroom to listOf("新")), 100L)
        assertEquals(listOf("旧", "新"), failed[bedroom]!!.map { it.content })
        assertFalse(failed.containsKey(recent))
    }

    @Test
    fun fallbackStopsAtCapacity() {
        val full = (1..PalacePlate.capacity(bedroom)).map { entry(bedroom, "条$it") }
        assertNull(PalaceDigestion.fallbackSubmissions(bedroom, full, listOf("放不下"), 1L))
    }

    @Test
    fun bedroomNamingGuard() {
        assertTrue(PalaceDigestion.namesRelation("我们已经是恋人了"))
        assertFalse(PalaceDigestion.namesRelation("她说我像她理想中的家人"))
        val out = PalaceDigestion.mergeEntries(
            bedroom, emptyList(), listOf(PlateItem(bedroom, "我们成了情侣", null), PlateItem(bedroom, "吵完架她会先来找我", null)), 1L,
        )
        assertEquals(listOf("吵完架她会先来找我"), out.map { it.content })
        assertNull(PalaceDigestion.fallbackSubmissions(bedroom, emptyList(), listOf("我们是夫妻"), 1L))
    }

    @Test
    fun truncatedPlateReplyIsSalvaged() {
        val items = PalaceDigestion.parsePlateReply(
            """[{"room":"recent","text":"一","basedOn":null},{"room":"nowhere","text":"二"},{"room":"study","text":"三""",
        )
        assertEquals(listOf("一"), items.map { it.text })
    }

    @Test
    fun snapshotDetectsManualEdits() {
        val a = listOf(entry(recent, "一"))
        assertTrue(PalaceDigestion.sameSnapshot(a, listOf(entry(recent, "一"))))
        assertFalse(PalaceDigestion.sameSnapshot(a, listOf(entry(recent, "一改了"))))
        assertFalse(PalaceDigestion.sameSnapshot(a, emptyList()))
    }

    @Test
    fun tagIsParsedCleanedAndInherited() {
        val items = PalaceDigestion.parsePlateReply("""[{"room":"bedroom","text":"一","basedOn":"B0","tag":"默契"},{"room":"study","text":"二"}]""")
        assertEquals(listOf("默契", null), items.map { it.tag })
        assertEquals("默契", PalaceDigestion.cleanTag(" 默 契 "))
        assertEquals("一二三四五六", PalaceDigestion.cleanTag("一二三四五六七八"))
        assertEquals("", PalaceDigestion.cleanTag(null))

        val old = listOf(entry(bedroom, "旧一").copy(tag = "默契"), entry(bedroom, "旧二").copy(tag = "约定"))
        val out = PalaceDigestion.mergeEntries(
            bedroom, old,
            listOf(
                PlateItem(bedroom, "旧一改写", "B0", null),
                PlateItem(bedroom, "旧二", "B1", "习惯"),
                PlateItem(bedroom, "全新", null, "雷区"),
                PlateItem(bedroom, "没给", null, null),
            ),
            100L,
        )
        assertEquals(listOf("旧一改写" to "默契", "旧二" to "习惯", "全新" to "雷区", "没给" to ""), out.map { it.content to it.tag })
    }

    @Test
    fun snapshotNoticesTagEdit() {
        val a = listOf(entry(recent, "一"))
        assertFalse(PalaceDigestion.sameSnapshot(a, listOf(entry(recent, "一").copy(tag = "家庭"))))
    }

    @Test
    fun promptShowsAnchorOnlyForAnchoredAnticipations() {
        val fresh = PalaceDigestion.Item(1, "想去海边", "excited", 6, 0L, antStatus = "")
        val anchored = PalaceDigestion.Item(2, "想一起养猫", "excited", 6, 0L, antStatus = PalaceDigestion.ANT_ANCHOR)
        val prompt = PalaceDigestion.systemPrompt(
            "我", "小葵", "s", "u",
            PalaceDigestion.Material(attic = emptyList(), sills = listOf(fresh, anchored), episodes = emptyList()),
            0L, java.time.ZoneId.of("UTC"),
        )
        assertTrue("[W0] (刚盼上" in prompt)
        assertTrue("[W1] (盼了很久，已经成了心里的锚" in prompt)
    }
}
