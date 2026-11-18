package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.db.dao.MemoryPlateDAO
import me.rerere.rikkahub.data.db.entity.MemoryPlateEntity
import me.rerere.rikkahub.data.model.MemoryFile

/**
 * 常驻模块：「关于小葵」(user)、「关于你」(self)、「关于家」(home)，存在 memory_plate 表，所有窗口都带上。
 * 不设长度上限，自动流程不碰。
 */
class CoreProfileRepository(
    private val plateDao: MemoryPlateDAO,
    private val memoryRepository: MemoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val USER = "user"
        const val SELF = "self"
        const val HOME = "home"
        const val SOURCE = "core"

        /** 拼成 system 里的常驻块；全空返回空串 */
        fun prompt(userCore: String, selfCore: String, homeCore: String = ""): String {
            val blocks = ArrayList<String>()
            if (userCore.isNotBlank()) blocks += "<plate name=\"关于小葵\" room=\"user\">\n${userCore.trim()}\n</plate>"
            if (selfCore.isNotBlank()) blocks += "<plate name=\"关于你\" room=\"self\">\n${selfCore.trim()}\n</plate>"
            if (homeCore.isNotBlank()) blocks += "<plate name=\"关于家\" room=\"home\">\n${homeCore.trim()}\n</plate>"
            if (blocks.isEmpty()) return ""
            return buildString {
                appendLine("<memory_plates>")
                appendLine("记忆宫殿的门牌：你早就知道的背景。不要主动提起，只在相关时自然影响你的反应。「关于你」是你自己的说话方式和约定，「关于小葵」是她的事，「关于家」是你能去的地方和家里有哪些功能。")
                blocks.forEach { appendLine(it) }
                append("</memory_plates>")
            }
        }
    }

    private val seedLock = Mutex()

    fun coreFlow(memoryId: String): Flow<List<MemoryPlateEntity>> = plateDao.getPlatesFlow(memoryId)

    /** (关于小葵, 关于你) */
    suspend fun core(memoryId: String): Pair<String, String> {
        val plates = plateDao.getPlates(memoryId)
        return plates.firstOrNull { it.kind == USER }?.content.orEmpty() to
            plates.firstOrNull { it.kind == SELF }?.content.orEmpty()
    }

    suspend fun setCore(memoryId: String, kind: String, content: String) {
        require(kind == USER || kind == SELF || kind == HOME)
        plateDao.upsert(MemoryPlateEntity(memoryId, kind, content.trim(), now(), SOURCE))
    }

    /** 删助手时一起删它自己的常驻模块；全局记忆库是大家共用的，不能删 */
    suspend fun deleteOf(memoryId: String) {
        require(memoryId != MemoryRepository.GLOBAL_MEMORY_ID)
        plateDao.deleteOfAssistant(memoryId)
    }

    /** 复制助手时把常驻模块复制过去 */
    suspend fun copy(from: String, to: String) {
        plateDao.getPlates(from).forEach { plateDao.upsert(it.copy(assistantId = to)) }
    }

    /** 聊天时注入的常驻块 */
    suspend fun promptFor(memoryId: String): String {
        seedFromLegacyFiles(memoryId)
        val plates = plateDao.getPlates(memoryId)
        fun of(kind: String) = plates.firstOrNull { it.kind == kind }?.content.orEmpty()
        return prompt(of(USER), of(SELF), of(HOME))
    }

    /**
     * 兜底：常驻模块两块都空，且 memory_file 里有 /profile.md（关于小葵）或 /preferences.md（关于你）时，
     * 把正文搬进来一次。只搬不删，旧文件不动。
     */
    suspend fun seedFromLegacyFiles(memoryId: String) = seedLock.withLock {
        if (plateDao.getPlates(memoryId).any { it.content.isNotBlank() }) return@withLock
        val files = memoryRepository.getFiles(memoryId).associateBy { it.path }
        val user = files[MemoryFile.PROFILE_PATH]?.displayBody?.trim().orEmpty()
        val self = files[MemoryFile.PREFERENCES_PATH]?.displayBody?.trim().orEmpty()
        if (user.isNotEmpty()) setCore(memoryId, USER, user)
        if (self.isNotEmpty()) setCore(memoryId, SELF, self)
    }
}
