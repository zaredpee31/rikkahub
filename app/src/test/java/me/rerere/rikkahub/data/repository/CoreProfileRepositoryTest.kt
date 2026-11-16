package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.db.dao.MemoryPlateDAO
import me.rerere.rikkahub.data.db.entity.MemoryFileEntity
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlateDAO : MemoryPlateDAO {
    val rows = MutableStateFlow<List<MemoryPlateEntity>>(emptyList())
    override fun getPlatesFlow(assistantId: String): Flow<List<MemoryPlateEntity>> =
        rows.map { all -> all.filter { it.assistantId == assistantId } }
    override suspend fun getPlates(assistantId: String) = rows.value.filter { it.assistantId == assistantId }
    override suspend fun getPlate(assistantId: String, kind: String) =
        rows.value.firstOrNull { it.assistantId == assistantId && it.kind == kind }
    override suspend fun upsert(plate: MemoryPlateEntity) {
        rows.value = rows.value.filterNot { it.assistantId == plate.assistantId && it.kind == plate.kind } + plate
    }
    override suspend fun deleteOfAssistant(assistantId: String) {
        rows.value = rows.value.filterNot { it.assistantId == assistantId }
    }
}

class CoreProfileRepositoryTest {
    private val plates = FakePlateDAO()
    private val memoryDao = FakeMemoryDAO()
    private val memory = MemoryRepository(memoryDao)
    private val repo = CoreProfileRepository(plates, memory) { 1000L }

    private suspend fun putFile(path: String, content: String) =
        memoryDao.upsertFile(MemoryFileEntity(assistantId = "a", path = path, content = content, updatedAt = 1))

    @Test
    fun `setCore trims and core reads both blocks`() = runTest {
        repo.setCore("a", CoreProfileRepository.USER, "  她喜欢茶  ")
        repo.setCore("a", CoreProfileRepository.SELF, "说话简短")
        assertEquals("她喜欢茶" to "说话简短", repo.core("a"))
        assertEquals("" to "", repo.core("other"))
    }

    @Test
    fun `setCore rejects unknown kind`() = runTest {
        val result = runCatching { repo.setCore("a", "room", "x") }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `prompt is empty when both blank and has only core blocks otherwise`() {
        assertEquals("", CoreProfileRepository.prompt(" ", ""))
        val text = CoreProfileRepository.prompt("她的事", "我的约定")
        assertTrue(text.startsWith("<memory_plates>") && text.endsWith("</memory_plates>"))
        assertTrue(text.contains("<plate name=\"关于小葵\" room=\"user\">\n她的事\n</plate>"))
        assertTrue(text.contains("<plate name=\"关于你\" room=\"self\">\n我的约定\n</plate>"))
        assertTrue(!text.contains("卧室") && !text.contains("书房") && !text.contains("最近"))
    }

    @Test
    fun `seeds from legacy files once when empty and keeps the files`() = runTest {
        putFile(MemoryFile.PROFILE_PATH, "她叫小葵")
        putFile(MemoryFile.PREFERENCES_PATH, "我叫阿辞")
        repo.seedFromLegacyFiles("a")
        assertEquals("她叫小葵" to "我叫阿辞", repo.core("a"))
        assertEquals(2, memory.getFiles("a").size)

        // 之后用户改了，再调用不会被旧文件覆盖
        repo.setCore("a", CoreProfileRepository.USER, "改过的")
        repo.seedFromLegacyFiles("a")
        assertEquals("改过的", repo.core("a").first)
    }

    @Test
    fun `does not seed when a block already has content`() = runTest {
        repo.setCore("a", CoreProfileRepository.SELF, "已有")
        putFile(MemoryFile.PROFILE_PATH, "旧的")
        repo.seedFromLegacyFiles("a")
        assertEquals("" to "已有", repo.core("a"))
    }

    @Test
    fun `seeds only the block whose file exists, ignores other files`() = runTest {
        putFile(MemoryFile.PREFERENCES_PATH, "只有这个")
        putFile("/other.md", "别的")
        assertTrue(repo.promptFor("a").contains("只有这个"))
        assertEquals("" to "只有这个", repo.core("a"))
    }

    @Test
    fun `no files and no plates gives empty prompt`() = runTest {
        assertEquals("", repo.promptFor("a"))
        assertEquals(0, plates.rows.value.size)
    }
}
