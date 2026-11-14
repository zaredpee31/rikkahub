package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.rikkahub.data.model.MemoryRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LegacyMemoryParserTest {
    private val front = "---\nname: diary\ntitle: 日记\n---\n"

    private fun file(content: String, path: String = "/topics/diary.md") =
        MemoryFile(path = path, content = content, updatedAt = 0)

    private val diary = file(front + "- [stated] 第一条\n- [stated] 第二条\n  续行\n\n- [stated] 第三条")

    @Test
    fun `list items split into blocks with continuation lines attached`() {
        val blocks = LegacyMemoryParser.parse(diary)
        assertEquals(listOf("第一条", "第二条\n  续行", "第三条"), blocks.map { it.content })
        assertTrue(blocks.all { it.room == MemoryRoom.LIVING })
        assertTrue(blocks.all { it.origin.startsWith("legacy:/topics/diary.md:") })
    }

    @Test
    fun `resident files are not split`() {
        assertTrue(LegacyMemoryParser.parse(file("- a\n- b", MemoryFile.PROFILE_PATH)).isEmpty())
    }

    @Test
    fun `same text twice gets distinct origins`() {
        val blocks = LegacyMemoryParser.parse(file("- 一样\n- 一样"))
        assertEquals(2, blocks.map { it.origin }.toSet().size)
        assertTrue(blocks[1].origin.endsWith(":2"))
    }

    @Test
    fun `rewrite replaces only that block and keeps frontmatter and marker`() {
        val origin = LegacyMemoryParser.parse(diary)[1].origin
        val result = LegacyMemoryParser.rewrite(diary, origin, "改过的第二条")
        assertNotNull(result)
        assertEquals(front + "- [stated] 第一条\n- [stated] 改过的第二条\n\n- [stated] 第三条", result!!.content)
        val reparsed = LegacyMemoryParser.parse(file(result.content))
        assertEquals("改过的第二条", reparsed[1].content)
        assertEquals(reparsed[1].origin, result.origin)
    }

    @Test
    fun `rewrite strips a marker the user typed instead of doubling it`() {
        val origin = LegacyMemoryParser.parse(diary)[0].origin
        val result = LegacyMemoryParser.rewrite(diary, origin, "- [stated] 新的")!!
        assertTrue(result.content.contains("\n- [stated] 新的\n"))
        assertEquals("新的", result.text)
    }

    @Test
    fun `delete removes the block from the source`() {
        val origin = LegacyMemoryParser.parse(diary)[2].origin
        val result = LegacyMemoryParser.rewrite(diary, origin, null)!!
        assertNull(result.origin)
        assertEquals(front + "- [stated] 第一条\n- [stated] 第二条\n  续行\n", result.content)
        assertEquals(listOf("第一条", "第二条\n  续行"), LegacyMemoryParser.parse(file(result.content)).map { it.content })
    }

    @Test
    fun `rewrite that would split the block is rejected`() {
        val origin = LegacyMemoryParser.parse(diary)[0].origin
        try {
            LegacyMemoryParser.rewrite(diary, origin, "上半\n\n下半")
            fail("expected rejection")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun `paragraphs without frontmatter`() {
        val f = file("第一段\n\n第二段", "/topics/notes.md")
        val blocks = LegacyMemoryParser.parse(f)
        assertEquals(listOf("第一段", "第二段"), blocks.map { it.content })
        val result = LegacyMemoryParser.rewrite(f, blocks[0].origin, "新第一段")!!
        assertEquals("新第一段\n\n第二段", result.content)
    }

    @Test
    fun `unknown origin returns null`() {
        assertNull(LegacyMemoryParser.rewrite(diary, "legacy:/topics/diary.md:0000000000000000", "x"))
    }

    @Test
    fun `fenced code stays one block`() {
        val blocks = LegacyMemoryParser.parse(file("```\na\n\nb\n```\n- 后面", "/areas/x.md"))
        assertEquals(2, blocks.size)
        assertEquals("```\na\n\nb\n```", blocks[0].content)
        assertEquals(MemoryRoom.STUDY, blocks[0].room)
    }
}
