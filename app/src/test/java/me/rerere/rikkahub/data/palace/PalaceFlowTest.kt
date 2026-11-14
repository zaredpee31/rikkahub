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
    fun `drain takes everything after the waterline including the hot zone`() {
        assertEquals(230, PalaceWaterline.toProcess(msgs(230), null, 0, drain = true).size)
        assertEquals(30, PalaceWaterline.toProcess(msgs(230), null, 0).let { PalaceWaterline.bufferOf(msgs(230), null, 0).size })
        // 自动整理的缓冲仍然不含最近 200 条
        assertTrue(PalaceWaterline.toProcess(msgs(230), null, 0).isEmpty())
    }

    @Test
    fun `manual consolidation handles everything even under 200 messages`() {
        val all = msgs(25)
        assertTrue(PalaceWaterline.toProcess(all, null, 0).isEmpty())
        val out = PalaceWaterline.toProcess(all, null, 0, drain = true)
        assertEquals(25, out.size)
        assertEquals("m1", out.first().id)
        assertEquals("m25", out.last().id)
        // 整理完水位在最后一条，再点就没有新的
        assertTrue(PalaceWaterline.toProcess(all, "m25", 25_000L, drain = true).isEmpty())
        // 之后新增的也会被手动整理带上，自动整理不会
        val more = msgs(40)
        assertEquals(15, PalaceWaterline.toProcess(more, "m25", 25_000L, drain = true).size)
        assertTrue(PalaceWaterline.toProcess(more, "m25", 25_000L).isEmpty())
    }
}

class PalaceExtractionTest {
    // 消息时间在 2026-11-14：模型填的日期不能晚于这批最后一条消息的日期
    private val batch = listOf(
        PalaceMessage("a", true, "今天加班", java.time.LocalDateTime.of(2026, 11, 14, 20, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli())
    )

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
        assertTrue("调试" in p)
    }

    @Test
    fun `prompt carries the approved rules verbatim`() {
        val p = PalaceExtraction.systemPrompt("阿辞", "小葵", "", "", hasRelated = true)
        assertTrue("同一件事只记一条。" in p)
        assertTrue("新内容跟旧记忆说的是同一件事，就输出 update，写合并后的完整版本，不要新增。完全重复，就跳过。" in p)
        assertTrue("拿不准就放 living_room，不要往 bedroom 塞。" in p)
        assertTrue("date 写事情发生的那天，不是聊天那天。她今天说昨天的事，就写昨天。" in p)
        assertTrue("不写事实清单，不抄房间里的记忆。" in p)
    }

    @Test
    fun `parses add update skip and recent`() {
        val reply = """{"memories": [
            {"action":"add","content":"新的事","room":"study"},
            {"action":"update","id":"O1","content":"合并后的完整版本","room":"user_room"},
            {"action":"update","id":"O9","content":"编号越界当新增"},
            {"action":"skip","id":"O0"}],
            "recent":["我有点想她","  ","我觉得她在慢慢信我","a","b","c","d","e"]}"""
        val r = PalaceExtraction.parseResult(reply, batch, relatedCount = 2)
        assertEquals(3, r.memories.size)
        assertEquals(null, r.memories[0].updates)
        assertEquals(1, r.memories[1].updates)
        assertEquals(null, r.memories[2].updates)
        assertEquals(1, r.skipped)
        assertEquals(PalaceExtraction.RECENT_MAX, r.recent!!.size)
        assertEquals("我有点想她", r.recent!![0])
    }

    @Test
    fun `no recent field leaves the plate alone and old array format still parses`() {
        assertEquals(null, PalaceExtraction.parseResult("""{"memories": []}""", batch).recent)
        assertEquals(1, PalaceExtraction.parseResult("""[{"content":"x","room":"study"}]""", batch).memories.size)
        assertThrows(PalaceExtraction.ParseException::class.java) {
            PalaceExtraction.parseResult("""{"memories": [{"content": "她今天""", batch)
        }
    }

    @Test
    fun `recent plate is rewritten whole and capped at six`() {
        val e = PalaceDigestion.recentEntries(List(9) { "第${it}句" } + listOf("第0句", " "), 5L)
        assertEquals(6, e.size)
        assertTrue(e.all { it.plate == PalacePlate.RECENT && it.firstLearnedAt == 5L })
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
        assertTrue("**[卧室]** (1970-01-01 周四" in out)
        assertTrue("她说想我" in out)
        assertFalse("忽然又想起" in out)
    }

    @Test
    fun `waterline default`() {
        assertEquals(200, PalaceWaterline.DEFAULT_HOT_ZONE)
        assertEquals(100, PalaceWaterline.DEFAULT_THRESHOLD)
    }
}

class PalaceContextAndDateTest {
    private fun msgs(n: Int) = (1..n).map { PalaceMessage("m$it", it % 2 == 1, "消息$it", it * 1000L) }

    @Test
    fun `hidden count never touches the hot zone`() {
        assertEquals(0, PalaceWaterline.hiddenCount(msgs(150), "m100", 0))
        assertEquals(0, PalaceWaterline.hiddenCount(msgs(400), null, 0))
        assertEquals(85, PalaceWaterline.hiddenCount(msgs(400), "m85", 0))
        // 水位进了最近 200 条（手动整理之后）：默认仍留热区；请求里传 hotZone = 0 就全部隐藏
        assertEquals(200, PalaceWaterline.hiddenCount(msgs(400), "m390", 0))
        assertEquals(390, PalaceWaterline.hiddenCount(msgs(400), "m390", 0, hotZone = 0))
        assertEquals(150, PalaceWaterline.hiddenCount(msgs(400), "deleted", 150_000L))
    }

    @Test
    fun `memory date is the day it happened not a later day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val ms = java.time.LocalDateTime.of(2026, 11, 13, 22, 40).atZone(zone).toInstant().toEpochMilli()
        val batch = listOf(PalaceMessage("a", true, "x", ms))
        val out = PalaceExtraction.parse(
            """[{"content":"a","date":"2026-11-14","time":"23:00"},{"content":"b","date":"2026-11-13","time":"21:30"},{"content":"c","date":"2026-11-13"}]""",
            batch, zone,
        )
        // 模型填了整理当天（晚于这批最后一条消息的日期）：夹回 11-13，只留日期
        assertEquals(java.time.LocalDate.of(2026, 11, 13).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), out[0].createdAt)
        assertEquals(java.time.LocalDateTime.of(2026, 11, 13, 21, 30).atZone(zone).toInstant().toEpochMilli(), out[1].createdAt)
        assertEquals(java.time.LocalDate.of(2026, 11, 13).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), out[2].createdAt)
    }

    @Test
    fun `recall stamp has weekday period and relative day`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = java.time.LocalDate.of(2026, 11, 14)
        fun ms(h: Int, m: Int = 0, d: Int = 13) = java.time.LocalDateTime.of(2026, 11, d, h, m).atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-11-13 周五 晚上（昨天）", PalaceIndex.stamp(ms(22, 40), zone, today))
        assertEquals("2026-11-14 周六 凌晨（今天）", PalaceIndex.stamp(ms(1, 0, 14), zone, today))
        assertEquals("2026-11-13 周五 下午（昨天）", PalaceIndex.stamp(ms(15), zone, today))
        // 12:00 整是只知道日期
        assertEquals("2026-11-12 周四（前天）", PalaceIndex.stamp(ms(12, 0, 12), zone, today))
        assertEquals("2026-11-10 周二 上午", PalaceIndex.stamp(ms(9, 0, 10), zone, today))
    }
}

class PalaceDateRepairTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun ms(d: Int, h: Int = 12) = java.time.LocalDateTime.of(2026, 11, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `fixes only dates later than the batch last message`() {
        val nodes = listOf(
            PalaceDateRepair.Node(1, "chat:c1:m1:0", ms(14)),
            PalaceDateRepair.Node(2, "chat:c1:m1:1", ms(12)),
            PalaceDateRepair.Node(3, "chat:c1:gone:0", ms(14)),
            PalaceDateRepair.Node(4, "legacy:/a.md:abc", ms(14)),
            PalaceDateRepair.Node(5, "chat:other:m1:0", ms(14)),
        )
        val plan = PalaceDateRepair.plan(nodes, zone) { conv, msg -> if (conv == "c1" && msg == "m1") ms(13, 23) else null }
        assertEquals(listOf(PalaceDateRepair.Fix(1, ms(14), ms(13))), plan.fixes)
        assertEquals(2, plan.missing)
        assertEquals(1, plan.ok)
    }

    @Test
    fun `origin parsing`() {
        assertEquals("c" to "m", PalaceDateRepair.parseOrigin("chat:c:m:3"))
        assertEquals(null, PalaceDateRepair.parseOrigin("manual:abc"))
    }
}
