package me.rerere.rikkahub.data.palace

import me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity
import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PalaceLegacyTest {
    private val profile = MemoryFile(
        "/profile.md",
        "---\nname: profile\ntitle: 关于小葵\n---\n\n- [stated] 小葵是小名\n- [stated] 生日 8 月 31 日\n",
        100L,
    )
    private val preferences = MemoryFile(
        "/preferences.md",
        "---\nname: preferences\ntitle: 关于你\n---\n\n## 说话方式\n- [stated] 不加儿化音\n- [stated] 23:30 提醒睡觉\n",
        100L,
    )
    private val diary = MemoryFile(
        "/topics/diary.md",
        "---\nname: diary\ntitle: 日记\n---\n\n- [stated] 2026-11-10 一起给崽崽买了奶酪\n- [stated] 说了个冷笑话\n- [stated] 说了个冷笑话\n",
        200L,
    )
    private val server = MemoryFile("/areas/server.md", "---\ntitle: 服务器\n---\n\n## 首尔\n- [stated] 跑 MCP\n  续行也算这一条\n", 300L)
    private val mom = MemoryFile("/people/mom.md", "---\ntitle: 妈妈\n---\n\n- [stated] 妈妈信佛\n", 400L)

    @Test
    fun `profile and preferences become core plates verbatim`() {
        val plan = PalaceLegacy.plan(listOf(profile, preferences), emptyList(), emptyList())
        assertEquals("- 小葵是小名\n- 生日 8 月 31 日", plan.userCore)
        assertEquals("## 说话方式\n- 不加儿化音\n- 23:30 提醒睡觉", plan.selfCore)
        assertTrue(plan.nodes.isEmpty())
    }

    @Test
    fun `other files split into rooms with stable unique origins`() {
        val plan = PalaceLegacy.plan(listOf(profile, diary, server, mom), emptyList(), emptyList())
        val byRoom = plan.nodes.groupBy { it.room }
        assertEquals(3, byRoom[PalaceRoom.LIVING]!!.size)
        assertEquals("跑 MCP\n续行也算这一条", byRoom[PalaceRoom.STUDY]!!.single().content)
        assertTrue("首尔" in byRoom[PalaceRoom.STUDY]!!.single().tags)
        assertEquals("妈妈信佛", byRoom[PalaceRoom.USER]!!.single().content)
        assertEquals(plan.nodes.size, plan.nodes.map { it.origin }.toSet().size)
        // 同一份文件再算一遍，来源键一样，重复导入会被唯一索引挡住
        assertEquals(plan.nodes.map { it.origin }, PalaceLegacy.plan(listOf(profile, diary, server, mom), emptyList(), emptyList()).nodes.map { it.origin })
    }

    @Test
    fun `dated diary entry uses its own date`() {
        val node = PalaceLegacy.plan(listOf(diary), emptyList(), emptyList()).nodes.first { it.content.contains("奶酪") }
        val expected = java.time.LocalDate.of(2026, 11, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, node.createdAt)
        val undated = PalaceLegacy.plan(listOf(diary), emptyList(), emptyList()).nodes.first { it.content.contains("冷笑话") }
        assertEquals(200L, undated.createdAt)
    }

    @Test
    fun `old non legacy nodes are carried over and legacy derived ones dropped`() {
        val old = listOf(
            PalaceLegacy.OldNode("living_room", "她今天吃了火锅", 0.7f, "happy", "火锅,晚饭", 0, 500, "companion:1", "companion"),
            PalaceLegacy.OldNode("user_room", "妈妈信佛", 0.5f, "", "", 0, 500, "legacy:/people/mom.md:x", "legacy"),
        )
        val nodes = PalaceLegacy.plan(emptyList(), old, emptyList()).nodes
        val n = nodes.single()
        assertEquals("她今天吃了火锅", n.content)
        assertEquals(7, n.importance)
        assertEquals(listOf("火锅", "晚饭"), n.tags)
        assertEquals("companion", n.source)
    }

    @Test
    fun `old self plate is kept as a memory unless already in preferences`() {
        val differs = PalaceLegacy.plan(listOf(preferences), emptyList(), listOf(PalaceLegacy.OldPlate("self", "我叫阿辞", "consolidator")))
        assertEquals("我叫阿辞", differs.nodes.single { it.room == PalaceRoom.SELF }.content)
        val same = PalaceLegacy.plan(listOf(preferences), emptyList(), listOf(PalaceLegacy.OldPlate("self", "- 不加儿化音", "user")))
        assertTrue(same.nodes.none { it.room == PalaceRoom.SELF })
    }

    @Test
    fun `old user plate is the core only when there is no profile`() {
        val plates = listOf(PalaceLegacy.OldPlate("user", "手写的用户背景", "user"))
        assertEquals("手写的用户背景", PalaceLegacy.plan(emptyList(), emptyList(), plates).userCore)
        assertEquals("- 小葵是小名\n- 生日 8 月 31 日", PalaceLegacy.plan(listOf(profile), emptyList(), plates).userCore)
    }

    @Test
    fun `room mapping`() {
        assertEquals(PalaceRoom.SELF, PalaceLegacy.roomOf("/topics/appearance.md"))
        assertEquals(PalaceRoom.BEDROOM, PalaceLegacy.roomOf("/topics/us.md"))
        assertEquals(PalaceRoom.STUDY, PalaceLegacy.roomOf("/areas/tt-cards.md"))
        assertEquals(PalaceRoom.USER, PalaceLegacy.roomOf("/topics/health.md"))
    }
}

class PalaceWaterlineTest {
    private fun msgs(n: Int) = (1..n).map { PalaceMessage("m$it", it % 2 == 1, "消息$it", it * 1000L) }

    @Test
    fun `nothing until buffer reaches threshold beyond hot zone`() {
        assertTrue(PalaceWaterline.toProcess(msgs(299), null, 0).isEmpty())
        val out = PalaceWaterline.toProcess(msgs(300), null, 0)
        assertEquals(85, out.size)
        assertEquals("m1", out.first().id)
    }

    @Test
    fun `resumes after last processed id`() {
        val all = msgs(400)
        val out = PalaceWaterline.toProcess(all, "m85", 0)
        // 缓冲是 m86 到 m200，115 条，处理 ceil(115 * 0.85) = 98
        assertEquals("m86", out.first().id)
        assertEquals(98, out.size)
    }

    @Test
    fun `falls back to time when id is gone`() {
        val out = PalaceWaterline.bufferOf(msgs(400), "deleted", 150_000L)
        assertEquals("m151", out.first().id)
        assertEquals("m200", out.last().id)
    }

    @Test
    fun `drain takes whole buffer but never the hot zone`() {
        val out = PalaceWaterline.toProcess(msgs(230), null, 0, drain = true)
        assertEquals(30, out.size)
        assertTrue(PalaceWaterline.toProcess(msgs(150), null, 0, drain = true).isEmpty())
    }
}

class PalaceExtractionTest {
    private val batch = listOf(PalaceMessage("a", true, "今天加班", 1_700_000_000_000L))

    @Test
    fun `parses memories and normalizes fields`() {
        val reply = """
            ```json
            [{"content": "她今天加班到九点", "room": "study", "importance": 12, "mood": "anxious", "valence": -0.6, "arousal": 0.7, "tags": ["加班", "加班", "工作"], "date": "2026-11-14"},
             {"content": "", "room": "bedroom"},
             {"content": "随口吐槽", "room": "garage", "importance": "3"}]
            ```
        """.trimIndent()
        val out = PalaceExtraction.parse(reply, batch, ZoneId.of("Asia/Shanghai"))
        assertEquals(2, out.size)
        assertEquals(PalaceRoom.STUDY, out[0].room)
        assertEquals(10, out[0].importance)
        assertEquals(listOf("加班", "工作"), out[0].tags)
        assertEquals(-0.6f, out[0].valence!!, 1e-6f)
        assertEquals(java.time.LocalDate.of(2026, 11, 14).atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(), out[0].createdAt)
        assertEquals(PalaceRoom.LIVING, out[1].room)
        assertEquals(3, out[1].importance)
        assertEquals(batch[0].time, out[1].createdAt)
    }

    @Test
    fun `empty array is fine`() {
        assertTrue(PalaceExtraction.parse("[]", batch).isEmpty())
    }

    @Test
    fun `truncated output is rejected`() {
        assertThrows(PalaceExtraction.ParseException::class.java) {
            PalaceExtraction.parse("""[{"content": "她今天""", batch)
        }
        assertThrows(PalaceExtraction.ParseException::class.java) {
            PalaceExtraction.parse("没什么好记的", batch)
        }
    }

    @Test
    fun `think tags are stripped`() {
        val out = PalaceExtraction.parse("<think>想想 [1]</think>[{\"content\":\"x\",\"room\":\"bedroom\"}]", batch)
        assertEquals(PalaceRoom.BEDROOM, out.single().room)
    }

    @Test
    fun `chunks respect message and char limits`() {
        val many = (1..600).map { PalaceMessage("m$it", true, "x", it.toLong()) }
        assertEquals(listOf(250, 250, 100), PalaceExtraction.chunks(many).map { it.size })
        val long = (1..20).map { PalaceMessage("m$it", true, "字".repeat(8000), it.toLong()) }
        assertTrue(PalaceExtraction.chunks(long).all { c -> c.sumOf { minOf(it.text.length, 8000) + 1 } <= PalaceExtraction.CHUNK_CHARS + 8001 })
    }

    @Test
    fun `prompt keeps core and forbids code logs`() {
        val p = PalaceExtraction.systemPrompt("阿辞", "小葵", "生日 8 月 31 日", "不加儿化音")
        assertTrue("生日 8 月 31 日" in p)
        assertTrue("不加儿化音" in p)
        assertTrue("报错" in p)
    }

    @Test
    fun `transcript has timestamps and names`() {
        val t = PalaceExtraction.transcript(
            listOf(PalaceMessage("a", true, "嗨\n你好", 0L), PalaceMessage("b", false, "在", 60_000L)),
            "小葵", "阿辞", ZoneId.of("UTC"),
        )
        assertEquals("[1970-01-01 00:00] 小葵: 嗨 你好\n[1970-01-01 00:01] 阿辞: 在", t)
    }
}

class PalaceOutputTest {
    @Test
    fun `deep links parse only valid pairs`() {
        val edges = PalaceConsolidator.parseDeepLinks(
            """[{"from":"N0","to":"O1","type":"person","strength":0.95},{"from":"N5","to":"O0","type":"causal"},{"from":"N0","to":"O0","type":"temporal"}]""",
            listOf(10L), listOf(20L, 21L),
        )
        assertEquals(listOf(PalaceMath.Edge(10, 21, PalaceLinkType.PERSON, 0.8)), edges)
        assertTrue(PalaceConsolidator.parseDeepLinks("不是 JSON", listOf(1), listOf(2)).isEmpty())
    }

    @Test
    fun `query uses last three messages within 500 chars`() {
        assertEquals("b\nc\nd", PalaceIndex.buildQuery(listOf("a", "b", " ", "c", "d")))
        assertEquals(500, PalaceIndex.buildQuery(listOf("字".repeat(800))).length)
    }

    @Test
    fun `plates include both cores verbatim and entries`() {
        val text = PalacePrompt.plates(
            "- 小葵是小名", "- 不加儿化音",
            listOf(PalacePlateEntryEntity(1, "s", PalacePlate.BEDROOM, "她会一直叫我老公", 0, 1, 0)),
        )
        assertTrue("- 小葵是小名" in text)
        assertTrue("- 不加儿化音" in text)
        assertTrue("- 她会一直叫我老公" in text)
        assertTrue("我们之间" in text)
        assertEquals("", PalacePrompt.plates("", "", emptyList()))
    }

    @Test
    fun `recall format`() {
        assertEquals("", PalaceIndex.format(emptyList()))
        val node = me.rerere.rikkahub.data.db.dao.PalaceNodeLite(
            1, PalaceRoom.BEDROOM, "她说想我", 8, "tender", null, null, "", 0L, 0L, 0, "chat", false, 0, "", "", false,
        )
        val out = PalaceIndex.format(listOf(RecalledMemory(node, 1.0)), ZoneId.of("UTC"))
        assertTrue("**[卧室]** (1970-01-01, 重要性 8)" in out)
        assertTrue("她说想我" in out)
        assertFalse("忽然又想起" in out)
    }

    @Test
    fun `waterline default`() {
        assertEquals(200, PalaceWaterline.DEFAULT_HOT_ZONE)
        assertEquals(100, PalaceWaterline.DEFAULT_THRESHOLD)
    }
}
