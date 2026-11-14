package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingIdsTest {
    @Test
    fun `many marks before collecting are all delivered once`() = runTest {
        val pending = PendingIds()
        // 旧的 SharedFlow 容量 64，满了丢最旧的；这里标记 200 个库，每个两次
        val ids = (1..200).map { "id$it" }
        repeat(2) { ids.forEach(pending::mark) }
        val got = pending.flow.take(ids.size).toList()
        assertEquals(ids.toSet(), got.toSet())
        assertEquals(ids.size, got.size)
    }

    @Test
    fun `mark after a round is delivered in the next round`() = runTest {
        val pending = PendingIds()
        pending.mark("a")
        val got = mutableListOf<String>()
        pending.flow.take(2).collect {
            got += it
            if (it == "a") pending.mark("a")
        }
        assertEquals(listOf("a", "a"), got)
    }
}
