package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Test

class GapTailTransformerTest {

    @Test
    fun `formatGapZh covers each range`() {
        assertEquals("不到 1 分钟", formatGapZh(0))
        assertEquals("不到 1 分钟", formatGapZh(59))
        assertEquals("不到 1 分钟", formatGapZh(-5))
        assertEquals("1 分钟", formatGapZh(60))
        assertEquals("59 分钟", formatGapZh(59 * 60 + 59))
        assertEquals("1 小时", formatGapZh(3600))
        assertEquals("3 小时 12 分钟", formatGapZh(3 * 3600 + 12 * 60))
        assertEquals("23 小时 59 分钟", formatGapZh(24 * 3600 - 1))
        assertEquals("1 天", formatGapZh(24 * 3600))
        assertEquals("2 天 5 小时", formatGapZh(2 * 86400 + 5 * 3600 + 20 * 60))
    }

    @Test
    fun `gapLine writes Beijing time and where the last message was`() {
        // 2026-11-20 22:15 北京时间 = 14:15 UTC，周五
        val now = java.time.ZonedDateTime.of(2026, 11, 20, 14, 15, 0, 0, java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli()
        val threeH12 = now - (3 * 3600 + 12 * 60) * 1000L
        assertEquals(
            "现在 2026-11-20 22:15（周五），距离她上一句过去 3 小时 12 分钟（上一句在 AC）",
            gapLine(now, threeH12, lastInQq = false),
        )
        assertEquals(
            "现在 2026-11-20 22:15（周五），距离她上一句过去 不到 1 分钟（上一句在 QQ）",
            gapLine(now, now - 20_000L, lastInQq = true),
        )
        assertEquals("现在 2026-11-20 22:15（周五）", gapLine(now, null, lastInQq = false))
    }
}
