package me.rerere.rikkahub.data.palace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PalaceRulesTest {
    private val h = PalaceMath.HOUR_MS
    private val now = 1_800_000_000_000L

    @Test
    fun `rooms that never decay keep importance`() {
        for (room in listOf(PalaceRoom.SELF, PalaceRoom.ATTIC, PalaceRoom.WINDOWSILL)) {
            assertEquals(7.0, PalaceMath.effectiveImportance(7, room, now - 10_000 * h, now), 1e-9)
        }
    }

    @Test
    fun `living room decays to its floor`() {
        val oneHour = PalaceMath.effectiveImportance(10, PalaceRoom.LIVING, now - h, now)
        assertEquals(10 * 0.9972, oneHour, 1e-9)
        // 很久以前的只掉到 80%
        assertEquals(8.0, PalaceMath.effectiveImportance(10, PalaceRoom.LIVING, now - 1000 * h, now), 1e-9)
        assertEquals(9.0, PalaceMath.effectiveImportance(10, PalaceRoom.BEDROOM, now - 100_000 * h, now), 1e-9)
    }

    @Test
    fun `unknown room is scored as living room`() {
        assertEquals(PalaceRoom.weights(PalaceRoom.LIVING), PalaceRoom.weights("garage"))
        assertEquals(PalaceRoom.LIVING, PalaceRoom.normalize(null))
    }

    @Test
    fun `promotion rules`() {
        assertTrue(PalaceMath.shouldPromote(PalaceRoom.LIVING, 8, now, 0, now))
        assertFalse(PalaceMath.shouldPromote(PalaceRoom.LIVING, 6, now - 23 * h, 0, now))
        assertTrue(PalaceMath.shouldPromote(PalaceRoom.LIVING, 6, now - 24 * h, 0, now))
        assertTrue(PalaceMath.shouldPromote(PalaceRoom.LIVING, 2, now, 3, now))
        assertFalse(PalaceMath.shouldPromote(PalaceRoom.STUDY, 10, now, 9, now))
    }

    @Test
    fun `evicts lowest effective importance beyond capacity`() {
        val nodes = (1L..203L).map { PalaceMath.EvictCandidate(it, if (it <= 3) 1 else 5, now) }
        assertEquals(listOf(1L, 2L, 3L), PalaceMath.pickEvictions(nodes, now).sorted())
        assertTrue(PalaceMath.pickEvictions(nodes.take(200), now).isEmpty())
    }

    @Test
    fun `stale recency weight is redistributed`() {
        val base = PalaceMath.ScoreInput(PalaceRoom.LIVING, 10, now, now - 10_000 * h, 0, 1.0, 1.0)
        // 时近趋于 0，0.3 的份额平分给相似度和重要性：0.65 * 1 + 0.35 * 0.8
        assertEquals(0.65 + 0.35 * 0.8, PalaceMath.score(base.copy(createdAt = now - 10_000 * h), now), 1e-6)
        val fresh = base.copy(lastAccessedAt = now)
        assertEquals(0.5 + 0.3 + 0.2, PalaceMath.score(fresh, now), 1e-9)
    }

    @Test
    fun `familiarity grows slowly and caps`() {
        assertEquals(0.0, PalaceMath.familiarityBonus(1), 0.0)
        assertTrue(PalaceMath.familiarityBonus(2) > 0)
        assertEquals(1.0, PalaceMath.familiarityBonus(1_000_000), 1e-9)
    }

    @Test
    fun `priming boosts only nearby moods`() {
        val happy = PalaceMood.va("happy")
        assertEquals(1.3, PalaceMath.primingBoost(happy, happy), 1e-9)
        assertEquals(1.0, PalaceMath.primingBoost(PalaceMood.va("sad"), happy), 1e-9)
        assertEquals(1.0, PalaceMath.primingBoost(happy, null), 1e-9)
        assertEquals(1.0, PalaceMath.primingBoost(happy, EmotionVA.NEUTRAL), 1e-9)
    }

    @Test
    fun `emotional links need clear and close moods`() {
        assertEquals(0.55, PalaceMath.emotionalLinkStrength(PalaceMood.va("sad"), PalaceMood.va("sad")), 1e-9)
        assertEquals(0.0, PalaceMath.emotionalLinkStrength(PalaceMood.va("neutral"), PalaceMood.va("neutral")), 0.0)
        assertEquals(0.0, PalaceMath.emotionalLinkStrength(PalaceMood.va("happy"), PalaceMood.va("sad")), 0.0)
    }

    @Test
    fun `stored coordinates win over mood word`() {
        assertEquals(EmotionVA(0.1, -0.2), PalaceMood.va("happy", 0.1f, -0.2f).let { EmotionVA(Math.round(it.v * 10) / 10.0, Math.round(it.a * 10) / 10.0) })
        assertEquals(EmotionVA.NEUTRAL, PalaceMood.va("没听过的词"))
    }

    @Test
    fun `narrative spread prefers temporal links and skips seeds`() {
        val edges = listOf(
            PalaceMath.Edge(1, 2, PalaceLinkType.TEMPORAL, 0.5),
            PalaceMath.Edge(1, 3, PalaceLinkType.EMOTIONAL, 0.5),
            PalaceMath.Edge(1, 4, PalaceLinkType.TEMPORAL, 0.5),
        )
        val out = PalaceMath.spread(mapOf(1L to 1.0, 4L to 0.5), edges)
        assertEquals(listOf(2L, 3L), out.map { it.first })
        assertEquals(1.0 * 0.5 * 1.0 * 0.3, out[0].second, 1e-9)
        assertEquals(1.0 * 0.5 * 0.3 * 0.3, out[1].second, 1e-9)
    }

    @Test
    fun `spread is capped`() {
        val edges = (2L..20L).map { PalaceMath.Edge(1, it, PalaceLinkType.PERSON, 0.6) }
        assertEquals(PalaceMath.MAX_SPREAD, PalaceMath.spread(mapOf(1L to 1.0), edges).size)
    }

    @Test
    fun `cosine handles mismatched and zero vectors`() {
        assertEquals(1.0, PalaceMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(2f, 4f)), 1e-9)
        assertEquals(0.0, PalaceMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0.0)
        assertEquals(0.0, PalaceMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 2f)), 0.0)
    }

    @Test
    fun `plates and rooms`() {
        assertEquals(10, PalacePlate.capacity(PalacePlate.BEDROOM))
        assertEquals(8, PalacePlate.capacity(PalacePlate.STUDY))
        assertEquals(10, PalacePlate.capacity(PalacePlate.RECENT))
        assertEquals(7, PalaceRoom.all.size)
        assertEquals(200, PalaceRoom.capacity(PalaceRoom.LIVING))
        assertEquals(null, PalaceRoom.capacity(PalaceRoom.BEDROOM))
    }
}

class PalaceBm25Test {
    @Test
    fun `tokenizes chinese bigrams and latin words`() {
        assertEquals(listOf("明日", "日方", "方舟", "kpl"), PalaceBm25.tokenize("明日方舟KPL"))
        assertEquals(listOf("猫"), PalaceBm25.tokenize("猫。"))
        assertEquals(listOf("看", "ag", "比赛"), PalaceBm25.tokenize("看AG比赛"))
    }

    @Test
    fun `ranks matching documents and skips misses`() {
        val docs = listOf(
            1L to "她今天去看了AG的比赛，赢了很开心",
            2L to "晚饭吃了麻辣烫",
            3L to "AG 输了一局",
        )
        val result = PalaceBm25.search("AG比赛", docs)
        assertEquals(1L, result.first().first)
        assertTrue(result.none { it.first == 2L })
    }

    @Test
    fun `empty inputs give nothing`() {
        assertTrue(PalaceBm25.search("", listOf(1L to "x")).isEmpty())
        assertTrue(PalaceBm25.search("x", emptyList()).isEmpty())
    }
}
