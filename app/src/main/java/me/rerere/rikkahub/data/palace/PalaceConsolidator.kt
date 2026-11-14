package me.rerere.rikkahub.data.palace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.companion.CompanionRepository
import me.rerere.rikkahub.data.ai.MemoryConsolidationRun
import me.rerere.rikkahub.data.ai.MemoryConsolidationWorker
import me.rerere.rikkahub.data.ai.PendingMemoryConversation
import me.rerere.rikkahub.data.ai.UsageRecorder
import me.rerere.rikkahub.data.ai.UsageSource
import me.rerere.rikkahub.data.ai.UsageTag
import me.rerere.rikkahub.data.ai.backgroundTextGenerationParams
import me.rerere.rikkahub.data.ai.tools.dialogueMessages
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.memoryId
import kotlin.uuid.Uuid

private const val TAG = "PalaceConsolidator"

/** 一次自动整理最多跑几批，剩下的下次再接着整理 */
private const val MAX_CHUNKS_PER_RUN = 3
private const val DEEP_LINK_CANDIDATES = 8

/**
 * 消息页的记忆整理：按水位把聊天记录交给模型提取成记忆，分房间存进宫殿，再连链接、做巩固。
 *
 * 只看消息页。普通窗口不进来，手动整理也不扫普通窗口的历史。
 * 每批的记忆和水位在同一个事务里写；一批失败就停，水位停在它前面，下次从这里重来。
 */
class PalaceConsolidator(
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val repository: PalaceRepository,
    private val companionRepository: CompanionRepository,
    private val settingsStore: SettingsStore,
) : MemoryConsolidationWorker {

    private fun idKey(conversationId: Uuid) = "wl:$conversationId:id"
    private fun timeKey(conversationId: Uuid) = "wl:$conversationId:time"

    private suspend fun companionAssistant(conversation: Conversation): Assistant? {
        if (!companionRepository.isCompanionId(conversation.id)) return null
        return settingsStore.awaitLoaded().getAssistantById(conversation.assistantId)?.takeIf { it.enableMemory }
    }

    private fun messagesOf(conversation: Conversation): List<PalaceMessage> {
        val zone = TimeZone.currentSystemDefault()
        return conversation.dialogueMessages()
            .filterNot { it.isContextCheckpoint }
            .mapNotNull { m ->
                val text = m.toText().trim()
                if (text.isEmpty()) null
                else PalaceMessage(m.id.toString(), m.role == MessageRole.USER, text, m.createdAt.toInstant(zone).toEpochMilliseconds())
            }
    }

    private suspend fun waterline(scope: String, conversation: Conversation): Pair<String?, Long> {
        val id = repository.meta(scope, idKey(conversation.id))
        val time = repository.meta(scope, timeKey(conversation.id))?.toLongOrNull()
        if (id != null || time != null) return id to (time ?: 0L)
        // 第一次：旧整理器整理过的部分已经在旧记忆里、搬进宫殿了，从它停下的地方开始
        return null to (companionRepository.getConsolidatedAt(conversation.id) ?: 0L)
    }

    private suspend fun bufferOf(scope: String, conversation: Conversation): List<PalaceMessage> {
        val (id, time) = waterline(scope, conversation)
        return PalaceWaterline.bufferOf(messagesOf(conversation), id, time)
    }

    override suspend fun pendingOf(conversation: Conversation): PendingMemoryConversation? = withContext(Dispatchers.IO) {
        val assistant = companionAssistant(conversation) ?: return@withContext null
        val scope = assistant.memoryId
        repository.ensureMigrated(scope)
        val buffer = bufferOf(scope, conversation)
        if (buffer.size < PalaceWaterline.DEFAULT_THRESHOLD) return@withContext null
        PendingMemoryConversation(
            conversationId = conversation.id,
            memoryId = scope,
            turns = buffer.size,
            automatic = assistant.autoConsolidateMemory,
        )
    }

    override suspend fun memoryIdsWithUpdates(): List<String> = withContext(Dispatchers.IO) {
        val companion = companionRepository.loadOrNull() ?: return@withContext emptyList()
        listOfNotNull(companionAssistant(companion)?.memoryId)
    }

    override suspend fun findPending(memoryId: String): List<PendingMemoryConversation> = withContext(Dispatchers.IO) {
        val companion = companionRepository.loadOrNull() ?: return@withContext emptyList()
        val assistant = companionAssistant(companion)?.takeIf { it.memoryId == memoryId } ?: return@withContext emptyList()
        val buffer = bufferOf(memoryId, companion)
        // 手动整理要能看到不够水位的缓冲，所以这里有一条就报
        if (buffer.isEmpty()) emptyList()
        else listOf(PendingMemoryConversation(companion.id, memoryId, buffer.size, assistant.autoConsolidateMemory))
    }

    override suspend fun markConsolidated(conversation: Conversation) {
        // 分叉出来的都是普通窗口，宫殿不整理普通窗口，不用记
    }

    /**
     * [fullReview] 是手动「整理」：缓冲里有多少处理多少，不留 15% 尾巴、不等水位，但最近 200 条照样不动。
     */
    override suspend fun consolidate(
        memoryId: String,
        conversationIds: Set<Uuid>,
        fullReview: Boolean,
    ): MemoryConsolidationRun? = withContext(Dispatchers.IO) {
        try {
            run(memoryId, conversationIds, fullReview)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logging.log(TAG, "consolidate $memoryId: $e")
            throw e
        }
    }

    private suspend fun run(scope: String, conversationIds: Set<Uuid>, drain: Boolean): MemoryConsolidationRun? {
        val settings = settingsStore.awaitLoaded()
        val companion = companionRepository.loadOrNull()?.takeIf { it.id in conversationIds || conversationIds.isEmpty() }
            ?: return null
        val assistant = companionAssistant(companion)?.takeIf { it.memoryId == scope } ?: return null
        repository.ensureMigrated(scope)

        val (lastId, lastTime) = waterline(scope, companion)
        val toProcess = PalaceWaterline.toProcess(messagesOf(companion), lastId, lastTime, drain = drain)

        val pm = settings.palaceModel(assistant)
        val provider = pm?.first?.findProvider(settings.providers)
        val userName = settings.displaySetting.userNickname.ifBlank { "小葵" }
        val selfName = assistant.name.ifBlank { "我" }

        val rooms = LinkedHashSet<String>()
        var advanced = false
        if (toProcess.isNotEmpty()) {
            val (model, reasoning) = pm ?: error("没有可用的整理模型")
            val prov = provider ?: error("Provider not found")
            val (userCore, selfCore) = repository.core(scope)
            val system = PalaceExtraction.systemPrompt(selfName, userName, userCore, selfCore)
            val newIds = ArrayList<Long>()
            val chunks = PalaceExtraction.chunks(toProcess).let { if (drain) it else it.take(MAX_CHUNKS_PER_RUN) }
            for (chunk in chunks) {
                val memories = extract(settings, model, prov, reasoning, system, chunk, userName, selfName)
                val last = chunk.last()
                val ids = repository.addExtracted(
                    scope = scope,
                    originPrefix = "chat:${companion.id}:${last.id}",
                    memories = memories,
                    meta = listOf(idKey(companion.id) to last.id, timeKey(companion.id) to last.time.toString()),
                )
                advanced = true
                newIds += ids
                memories.forEach { rooms += it.room }
            }
            if (newIds.isNotEmpty()) {
                runCatching { buildLinks(scope, newIds, settings, model, prov, reasoning) }
                    .onFailure { if (it is CancellationException) throw it; Logging.log(TAG, "links: $it") }
            }
            val (promoted, evicted) = repository.consolidate(scope)
            if (promoted > 0) rooms += PalaceRoom.BEDROOM
            if (evicted > 0) rooms += PalaceRoom.ATTIC
        }

        // 认知消化：每 50 轮一次，手动整理时直接做。失败不影响上面已经存好的记忆，下次再试
        val plates = LinkedHashSet<String>()
        if (pm != null && provider != null) {
            try {
                val (r, p) = digest(scope, companion, settings, pm.first, provider, pm.second, selfName, userName, force = drain)
                rooms += r
                plates += p
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logging.log(TAG, "digest $scope: $e")
            }
        }

        if (toProcess.isEmpty() && rooms.isEmpty() && plates.isEmpty()) return null
        val paths = rooms.map { "palace:$it" } + plates.map { "palace:plate:$it" }
        return MemoryConsolidationRun(
            memoryId = scope,
            finishedAt = System.currentTimeMillis(),
            fullReview = drain,
            changedPaths = paths,
            titles = rooms.associate { "palace:$it" to PalaceRoom.label(it) } +
                plates.associate { "palace:plate:$it" to "门牌 · " + PalacePlate.label(it) },
            advancedConversations = if (advanced) setOf(companion.id) else emptySet(),
        )
    }

    private suspend fun llm(
        model: Model,
        provider: me.rerere.ai.provider.ProviderSetting,
        reasoning: ReasoningLevel,
        system: String,
        user: String,
    ): String = usageRecorder.wrap(providerManager.getProviderByType(provider), UsageTag(UsageSource.MEMORY))
        .generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.system(system), UIMessage.user(user)),
            params = backgroundTextGenerationParams(model, Uuid.random(), reasoning),
        ).message.toText()

    private fun me.rerere.rikkahub.data.db.entity.PalaceNodeEntity.toItem() =
        PalaceDigestion.Item(id, content, mood, importance, createdAt)

    /** 返回 (动过的房间, 动过的门牌)。不到时候、没有材料都返回空 */
    private suspend fun digest(
        scope: String,
        companion: Conversation,
        settings: Settings,
        model: Model,
        provider: me.rerere.ai.provider.ProviderSetting,
        reasoning: ReasoningLevel,
        selfName: String,
        userName: String,
        force: Boolean,
    ): Pair<Set<String>, Set<String>> {
        val stored = repository.meta(scope, PalaceRepository.META_LAST_DIGEST)?.toLongOrNull()
        // 第一次从搬家那天算起，不把搬进来的旧记忆当成新经历
        val since = stored ?: repository.meta(scope, PalaceRepository.META_MIGRATED)?.toLongOrNull() ?: System.currentTimeMillis()
        if (stored == null) repository.setMeta(scope, PalaceRepository.META_LAST_DIGEST, since.toString())
        val rounds = messagesOf(companion).count { it.fromUser && it.time > since }
        if (!force && rounds < PalaceDigestion.AUTO_ROUNDS) return emptySet<String>() to emptySet()

        val now = System.currentTimeMillis()
        val all = repository.allNodes(scope).filter { !it.archived }
        val material = PalaceDigestion.Material(
            attic = all.filter { it.room == PalaceRoom.ATTIC }
                .sortedWith(compareByDescending<me.rerere.rikkahub.data.db.entity.PalaceNodeEntity> { it.importance }.thenByDescending { it.createdAt })
                .take(PalaceDigestion.ATTIC_CAP).map { it.toItem() },
            sills = all.filter { it.room == PalaceRoom.WINDOWSILL }.sortedBy { it.createdAt }.map { it.toItem() },
            episodes = all.filter {
                (it.room == PalaceRoom.LIVING || it.room == PalaceRoom.BEDROOM) && it.recordedAt > since &&
                    it.digestedAt == 0L && it.source != PalaceSource.DIGESTION
            }.sortedByDescending { it.recordedAt }.take(PalaceDigestion.EPISODE_CAP).sortedBy { it.createdAt }.map { it.toItem() },
            context = all.filter { it.room == PalaceRoom.LIVING || it.room == PalaceRoom.BEDROOM }
                .sortedByDescending { it.createdAt }.take(PalaceDigestion.CONTEXT_FALLBACK).map { it.toItem() },
        )
        if (material.isEmpty) {
            repository.setMeta(scope, PalaceRepository.META_LAST_DIGEST, now.toString())
            return emptySet<String>() to emptySet()
        }

        val (userCore, selfCore) = repository.core(scope)
        val system = PalaceDigestion.systemPrompt(selfName, userName, selfCore, userCore, material, now)
        val actions = PalaceDigestion.parse(llm(model, provider, reasoning, system, "开始回想。"))
        val plan = PalaceDigestion.plan(material, actions)

        val plates = LinkedHashMap<String, List<me.rerere.rikkahub.data.db.entity.PalacePlateEntryEntity>>()
        for ((plate, subs) in plan.submissions) {
            val old = repository.plateEntries(scope).filter { it.plate == plate }
            val parsed = try {
                PalaceDigestion.parsePlateMerge(llm(model, provider, reasoning, PalaceDigestion.plateMergePrompt(plate, old, subs), "开始整理。"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logging.log(TAG, "plate merge $plate: $e")
                null
            }
            plates[plate] = PalaceDigestion.mergePlate(plate, old, subs, parsed, now)
        }
        repository.applyDigest(scope, plan, plates, now)

        val rooms = LinkedHashSet<String>()
        if (plan.resolve.isNotEmpty() || plan.fulfill.isNotEmpty()) rooms += PalaceRoom.BEDROOM
        if (plan.deepen.isNotEmpty() || plan.fade.isNotEmpty() || plan.disappoint.isNotEmpty() || plan.newAttic.isNotEmpty()) rooms += PalaceRoom.ATTIC
        if (plan.newSill.isNotEmpty() || plan.fulfill.isNotEmpty() || plan.disappoint.isNotEmpty()) rooms += PalaceRoom.WINDOWSILL
        return rooms to plates.keys
    }

    /** 调一次提取，解析不了（输出截断、不是 JSON）就再试一次，还不行就抛出，这批不推进 */
    private suspend fun extract(
        settings: Settings,
        model: Model,
        provider: me.rerere.ai.provider.ProviderSetting,
        reasoning: ReasoningLevel,
        system: String,
        chunk: List<PalaceMessage>,
        userName: String,
        selfName: String,
    ): List<ExtractedMemory> {
        val transcript = PalaceExtraction.transcript(chunk, userName, selfName)
        var lastError: Exception? = null
        repeat(2) {
            val reply = usageRecorder.wrap(providerManager.getProviderByType(provider), UsageTag(UsageSource.MEMORY))
                .generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.system(system), UIMessage.user("聊天记录：\n$transcript")),
                    params = backgroundTextGenerationParams(model, Uuid.random(), reasoning),
                ).message.toText()
            try {
                return PalaceExtraction.parse(reply, chunk)
            } catch (e: PalaceExtraction.ParseException) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("记忆提取失败")
    }

    /**
     * 时间链（24 小时内）和情绪链（情绪接近）按规则连，不调模型；
     * 因果、人物、隐喻交给模型在新记忆和最近 8 条旧记忆之间判断，失败了就只有规则链。
     */
    private suspend fun buildLinks(
        scope: String,
        newIds: List<Long>,
        settings: Settings,
        model: Model,
        provider: me.rerere.ai.provider.ProviderSetting,
        reasoning: ReasoningLevel,
    ) {
        val all = repository.liveNodes(scope)
        val newSet = newIds.toSet()
        val fresh = all.filter { it.id in newSet }
        val old = all.filter { it.id !in newSet }
        val edges = ArrayList<PalaceMath.Edge>()
        for ((i, n) in fresh.withIndex()) {
            val va = PalaceMood.va(n.mood, n.valence, n.arousal)
            for (o in old + fresh.drop(i + 1)) {
                if (PalaceMath.withinTemporalWindow(n.createdAt, o.createdAt)) edges += PalaceMath.Edge(n.id, o.id, PalaceLinkType.TEMPORAL, 0.3)
                val s = PalaceMath.emotionalLinkStrength(va, PalaceMood.va(o.mood, o.valence, o.arousal))
                if (s > 0) edges += PalaceMath.Edge(n.id, o.id, PalaceLinkType.EMOTIONAL, s)
            }
        }
        repository.addLinks(scope, edges)

        val candidates = old.sortedByDescending { it.createdAt }.take(DEEP_LINK_CANDIDATES)
        if (candidates.isEmpty() || fresh.isEmpty()) return
        val prompt = """
            你在分析记忆之间的深层关联。给你一组新记忆 [N*] 和一组旧记忆 [O*]，找出它们之间确实存在的关联，类型只有三种：
            - causal：一件事导致了另一件事
            - person：说到了同一个人
            - metaphor：事情不同，但情感模式相似
            只输出有关联的配对，JSON 数组：[{"from": "N0", "to": "O2", "type": "person", "strength": 0.6}]
            strength 在 0.3 到 0.8 之间。没有关联输出 []。只输出 JSON。
        """.trimIndent()
        val input = "新记忆：\n" + fresh.withIndex().joinToString("\n") { (i, n) -> "[N$i] (${n.room}, ${n.mood}): ${n.content.take(80)}" } +
            "\n\n旧记忆：\n" + candidates.withIndex().joinToString("\n") { (i, n) -> "[O$i] (${n.room}, ${n.mood}): ${n.content.take(80)}" }
        val reply = usageRecorder.wrap(providerManager.getProviderByType(provider), UsageTag(UsageSource.MEMORY))
            .generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.system(prompt), UIMessage.user(input)),
                params = backgroundTextGenerationParams(model, Uuid.random(), reasoning),
            ).message.toText()
        repository.addLinks(scope, parseDeepLinks(reply, fresh.map { it.id }, candidates.map { it.id }))
    }

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        internal fun parseDeepLinks(reply: String, fresh: List<Long>, old: List<Long>): List<PalaceMath.Edge> {
            val start = reply.indexOf('[')
            val end = reply.lastIndexOf(']')
            if (start < 0 || end < start) return emptyList()
            val array = runCatching { json.parseToJsonElement(reply.substring(start, end + 1)).jsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.trim()
                val from = s("from")?.removePrefix("N")?.toIntOrNull()?.let { fresh.getOrNull(it) } ?: return@mapNotNull null
                val to = s("to")?.removePrefix("O")?.toIntOrNull()?.let { old.getOrNull(it) } ?: return@mapNotNull null
                val type = s("type")?.takeIf { it in PalaceLinkType.deep } ?: return@mapNotNull null
                val strength = ((o["strength"] as? JsonPrimitive)?.doubleOrNull ?: 0.5).coerceIn(0.3, 0.8)
                PalaceMath.Edge(from, to, type, strength)
            }
        }
    }
}

/** 整理用的模型：记忆整理模型，没设就用快速模型，再没有就用助手的对话模型。提取不需要工具 */
private fun Settings.palaceModel(assistant: Assistant): Pair<Model, ReasoningLevel>? {
    memoryModelId?.let { findModelById(it) }?.let { return it.copy(tools = emptySet()) to memoryModelReasoningLevel }
    findModelById(fastModelId)?.let { return it.copy(tools = emptySet()) to fastModelReasoningLevel }
    return findModelById(assistant.chatModelId ?: chatModelId)?.let { it.copy(tools = emptySet()) to fastModelReasoningLevel }
}
