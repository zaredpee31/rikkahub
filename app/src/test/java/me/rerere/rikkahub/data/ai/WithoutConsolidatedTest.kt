package me.rerere.rikkahub.data.ai

import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WithoutConsolidatedTest {
    private fun conv() = (1..6).map { if (it % 2 == 1) UIMessage.user("u$it") else UIMessage.assistant("a$it") }

    @Test
    fun `null or unknown id keeps everything`() {
        val list = conv()
        assertSame(list, list.withoutConsolidated(null))
        assertSame(list, list.withoutConsolidated("missing"))
    }

    @Test
    fun `drops up to and including the cutoff`() {
        val list = conv()
        val out = list.withoutConsolidated(list[3].id.toString())
        assertEquals(listOf(list[4], list[5]), out)
    }

    @Test
    fun `remainder never starts with an assistant message`() {
        val list = conv()
        val out = list.withoutConsolidated(list[2].id.toString())
        assertEquals(listOf(list[4], list[5]), out)
    }

    @Test
    fun `keeps the checkpoint summary before the cutoff`() {
        val list = conv().toMutableList()
        list[1] = list[1].copy(isContextCheckpoint = true)
        val out = list.withoutConsolidated(list[3].id.toString())
        assertEquals(listOf(list[1], list[4], list[5]), out)
    }
}
