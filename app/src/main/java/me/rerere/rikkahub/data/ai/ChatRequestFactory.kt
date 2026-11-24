package me.rerere.rikkahub.data.ai

import android.content.Context
import me.rerere.rikkahub.ui.components.sticker.ImageStickers
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.agent.AgentHooks
import me.rerere.ai.agent.AgentRequest
import me.rerere.ai.agent.RetryPolicy
import me.rerere.ai.agent.StepRequest
import me.rerere.ai.agent.withRetry
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.ChatToolFactory
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.DrivesTransformer
import me.rerere.rikkahub.data.ai.transformers.GapTailTransformer
import me.rerere.rikkahub.data.ai.transformers.InnerMonologueTransformer
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.QqRecentTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.SpeakingStyle
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.WorkspaceReminderTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transformThinkTags
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import me.rerere.rikkahub.data.repository.CoreProfileRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.repository.memoryId
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatRequestFactory"

/**
 * 一轮对话的请求上下文，按会话固定的配置解析一次。
 *
 * 正常对话和压缩用同一份上下文组装请求，系统提示词、工具定义、参数和 sessionId 都相同，
 * 压缩请求的前缀才能和上一轮对话一致，命中提示词缓存。
 */
class ChatTurnContext(
    val settings: Settings,
    val assistant: Assistant,
    val model: Model,
    val providerSetting: ProviderSetting,
    val conversationId: Uuid,
    val conversationSystemPrompt: String?,
    val workspaceCwd: String?,
    val tools: List<Tool>,
    /** 门牌，两种窗口都常驻 */
    val plates: String = "",
)

/** 把会话变成发给模型的请求：系统提示词、上下文裁剪、输入输出转换都在这里 */
class ChatRequestFactory(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val usageRecorder: UsageRecorder,
    private val chatToolFactory: ChatToolFactory,
    private val coreProfile: CoreProfileRepository,
    workspaceRepository: WorkspaceRepository,
) {
    private val inputTransformers: List<InputMessageTransformer> = listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        WorkspaceReminderTransformer(workspaceRepository),
        // 下面三个都往请求末尾那块里加（RequestTail.withTail），顺序：QQ 最近聊天 → 心情 → 独白要求
        QqRecentTransformer,
        GapTailTransformer, // 现在几点、距离她上一句多久（每轮都加在末尾那块）
        me.rerere.rikkahub.data.ai.transformers.MemoryRecallTransformer, // 记忆桶每句自动找
        DrivesTransformer,
        PillTailTransformer, // 她挂的药丸（[药丸:名]），只在她这句是最新一句时贴
        InnerMonologueTransformer, // 放最后，离开口最近
    )

    private val outputTransformers: List<OutputMessageTransformer> = listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
        DiceOutputTransformer,
    )

    /** 解析本轮的助手、模型、记忆和工具；MCP 服务名不合法时抛出 InvalidMcpServerNamesException */
    suspend fun createContext(settings: Settings, conversation: Conversation): ChatTurnContext {
        // 模型、思考级别、搜索、工具等以会话上固定的配置为准
        val assistant = settings.getAssistantOf(conversation)
        val model = settings.getChatModelOf(conversation)
            ?: throw IllegalStateException("No chat model selected")
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        return ChatTurnContext(
            settings = settings,
            assistant = assistant,
            model = model,
            providerSetting = provider,
            conversationId = conversation.id,
            conversationSystemPrompt = conversation.customSystemPrompt,
            workspaceCwd = conversation.workspaceCwd,
            plates = listOf(
                platesFor(assistant),
                me.rerere.rikkahub.data.ai.transformers.OmbreRecall.session(context, settings, assistant, conversation.id),
            ).filter { it.isNotBlank() }.joinToString("\n\n").also {
                me.rerere.rikkahub.companion.HomeLink.noteUse(conversation.id.toString(), assistant.enableMemory, it.length)
            },
            tools = chatToolFactory.createTools(
                settings = settings,
                assistant = assistant,
                model = model,
                workspaceCwd = conversation.workspaceCwd,
                conversationId = conversation.id,
            ),
        )
    }

    /**
     * 本轮交给 runAgent 的请求和钩子。[messages] 的最后一条是助手消息时从它继续，否则新建一条助手消息。
     * [processingStatus] 用来显示输入转换的进度。
     */
    fun createAgent(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?>,
    ): Pair<AgentRequest, AgentHooks> {
        val request = AgentRequest(
            provider = usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.CHAT, turn.assistant.id, turn.conversationId),
            ),
            providerSetting = turn.providerSetting,
            messages = messages,
            tools = turn.tools,
            stream = turn.assistant.streamOutput,
            retry = turn.settings.retryPolicy(),
        )
        val hooks = object : AgentHooks {
            // 照 SullyOS：一次回复只组一次请求（含「这一刻」那块），工具续发直接把这次的助手消息接在后面，
            // 不重跑输入变换器，「这一刻」不重做、不挪到最新工具结果后面（以前每步重贴，模型每步都像收到她一条新消息）
            private var base: StepRequest? = null
            private var baseSize = -1

            override suspend fun prepareStep(messages: List<UIMessage>): StepRequest {
                Log.i(TAG, "prepareStep: ${turn.model.id}")
                val current = messages.lastOrNull()?.takeIf { it.role == MessageRole.ASSISTANT }
                val history = if (current != null) messages.dropLast(1) else messages
                val built = base?.takeIf { baseSize == history.size }
                    ?: buildStep(turn, history, processingStatus).also {
                        base = it
                        baseSize = history.size
                    }
                if (current == null) return built
                // 照 SullyOS：写出来的 <think> 独白（没签名）剥掉；带签名的原生思考原样发回，工具续发时官方要求带上
                val cont = if (turn.model.innerMonologue) {
                    current.copy(parts = current.parts.filterNot { it is UIMessagePart.Reasoning && it.metadata == null })
                } else {
                    current
                }
                return built.copy(messages = built.messages + cont)
            }

            override suspend fun previewStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }

            override suspend fun finishStep(message: UIMessage, newPartsFrom: Int) =
                message.transformNewParts(newPartsFrom) {
                    it.transforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .visualTransforms(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                        .onGenerationFinish(outputTransformers, context, turn.model, turn.assistant, turn.settings)
                }
        }
        return request to hooks
    }

    /**
     * 生成压缩摘要。
     *
     * 请求按正常对话组装（系统提示词、工具定义、参数和 sessionId 都相同），只在末尾追加压缩指令，
     * 前缀与上一轮对话一致，可以命中提示词缓存，模型也能看到工具调用和结果。
     * 工具定义必须保留：它属于请求前缀，去掉或改动 tool_choice 都会让缓存失效。
     */
    suspend fun generateCompressionSummary(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        compressPrompt: String,
    ): String {
        val (requestMessages, params) = buildStep(turn, messages)
        // 压缩指令不经过输入转换器，模板转换器会把其中的花括号当成模板语法
        val result = withRetry(
            policy = turn.settings.retryPolicy(),
            onRetry = { Log.w(TAG, "Compression request failed, retrying in ${it.delay}", it.cause) },
        ) {
            usageRecorder.wrap(
                providerManager.getProviderByType(turn.providerSetting),
                UsageTag(UsageSource.COMPRESSION, turn.assistant.id, turn.conversationId),
            ).generateText(
                providerSetting = turn.providerSetting,
                messages = requestMessages + UIMessage.user(compressPrompt),
                // 助手的输出上限是给聊天回复定的，结构化摘要写不完会被截断；max_tokens 不属于请求前缀，改了不影响缓存
                params = params.copy(maxTokens = null),
            )
        }
        val summary = result.message.compressionSummaryOrNull()
            ?: error(context.getString(R.string.chat_page_compress_tool_call_response))
        return summary.ifBlank { error("Failed to generate compressed summary") }
    }

    /** 一步请求实际发出的消息和参数；正常对话和压缩共用，二者的请求前缀必须一致才能命中提示词缓存 */
    private suspend fun buildStep(
        turn: ChatTurnContext,
        messages: List<UIMessage>,
        processingStatus: MutableStateFlow<String?> = MutableStateFlow(null),
    ): StepRequest {
        val assistant = turn.assistant
        val requestMessages = buildList {
            val system = buildString {
                val effectiveSystemPrompt =
                    if (assistant.allowConversationSystemPrompt && !turn.conversationSystemPrompt.isNullOrBlank()) {
                        turn.conversationSystemPrompt
                    } else {
                        assistant.systemPrompt
                    }
                if (effectiveSystemPrompt.isNotBlank()) {
                    append(effectiveSystemPrompt)
                }

                // 骰子、语音、表情包的写法
                if (isNotEmpty()) appendLine()
                append(CHAT_SPECIAL_FORMAT_PROMPT)
                ImageStickers.names(context).takeIf { it.isNotEmpty() }?.let { names ->
                    appendLine()
                    append("[表情:名字] 是表情图。你也能发：单独一行写 [表情:名字]，名字从下面挑，一字不差，不用每条都发。可用名字：")
                    append(names.joinToString("、"))
                }

                // 记忆
                if (assistant.enableMemory) {
                    if (turn.plates.isNotBlank()) {
                        appendLine()
                        append(turn.plates)
                    }
                }
                // 工具prompt
                turn.tools.forEach { tool ->
                    appendLine()
                    append(tool.systemPrompt(turn.model, messages))
                }
            }
            if (system.isNotBlank()) {
                add(UIMessage.system(prompt = system).copy(isSynthetic = true))
            }
            addAll(messages.limitContext(assistant.contextMessageLimit))
        }.transforms(
            transformers = inputTransformers,
            context = context,
            model = turn.model,
            assistant = assistant,
            settings = turn.settings,
            processingStatus = processingStatus,
            workspaceCwd = turn.workspaceCwd,
        )
        if (turn.model.innerMonologue && !InnerMonologueTransformer.ruleIn(requestMessages)) {
            Log.w(TAG, "独白开着，请求里却没有独白规矩")
        }
        val params = TextGenerationParams(
            model = turn.model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = turn.tools,
            // 开口前先想一段：不强制关原生思考（5.5 不接受 OFF），保留助手设的档位；
            // 原生思考出来后 ThinkTagTransformer 照样认开头的 <think>
            reasoningLevel = assistant.reasoningLevel,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(turn.model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(turn.model.customBodies)
            },
            sessionId = turn.conversationId.toString(),
        )
        return StepRequest(requestMessages, params)
    }

    /** 常驻模块：关于小葵、关于我，所有窗口都常驻。读失败就不放，不让聊天失败 */
    private suspend fun platesFor(assistant: Assistant): String {
        if (!assistant.enableMemory) {
            SpeakingStyle.clear(assistant.id.toString())
            return ""
        }
        val plates = try {
            coreProfile.promptFor(assistant.memoryId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "memory plates failed", e)
            ""
        }
        // qq_recent 不放这里了：它一变人设就变、整段缓存断，挪到请求末尾那块（QqRecentTransformer）
        // 「我的说话方式」留在门牌里，不再拆到末尾（11-21 情绪改回原作者，没有浮现档了）
        return listOf(plates)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

}

/**
 * 取出压缩请求回复里的摘要正文。
 *
 * 指令要求不调用工具，模型仍可能发起调用。这时附带的文本只是"我先看看文件"之类的说明，
 * 当成摘要保存会让之后的请求丢掉全部历史，所以返回 null 交给调用方报错。
 * 压缩请求不经过输出转换器，正文开头的 <think> 思考内容也要在这里剥掉，否则会存进摘要。
 */
internal fun UIMessage.compressionSummaryOrNull(): String? {
    if (getTools().isNotEmpty()) return null
    return listOf(this)
        .transformThinkTags(now = Clock.System.now(), generationFinished = true)
        .single()
        .toText()
        .trim()
}

/**
 * 输出转换只处理这一步新生成的 part：之前步骤的内容已经处理过，再处理一遍会让正则这类改写叠加，
 * 也能让每一步开头的 <think> 都被识别出来。
 */
internal suspend fun UIMessage.transformNewParts(
    newPartsFrom: Int,
    transform: suspend (List<UIMessage>) -> List<UIMessage>,
): UIMessage {
    val transformed = transform(listOf(copy(parts = parts.drop(newPartsFrom)))).single()
    return transformed.copy(parts = parts.take(newPartsFrom) + transformed.parts)
}

/** 标题、建议、记忆整理这类后台请求的参数：只带模型自己的自定义请求头和请求体 */
internal fun backgroundTextGenerationParams(
    model: Model,
    sessionId: Uuid,
    reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
): TextGenerationParams = TextGenerationParams(
    model = model,
    reasoningLevel = reasoningLevel,
    customHeaders = model.customHeaders,
    customBody = model.customBodies,
    sessionId = sessionId.toString(),
)

internal fun Settings.retryPolicy() =
    if (networkSetting.enableAutoRetry) RetryPolicy.NETWORK else RetryPolicy.NONE

/** 聊天里的几种特殊消息的写法，所有会话的请求都附上。 */
private val CHAT_SPECIAL_FORMAT_PROMPT = """
<特殊消息>
对方发来的消息里可能有：
- [语音:秒数]文字：她按住说话发的语音，后面是转写出来的文字；括号里的语气是从她声音里听出来的，和她平时比。
- [骰子:点数]：她摇了一次骰子，点数 1 到 6。
- [崽崽:表情名]：崽崽表情包。
你想发语音时，单独一行写 [语音]要说的话，会显示成语音条，她点开会念出来；想摇骰子就单独一行写 [骰子]，点数由系统随机。都用半角符号，不用每次都用。
</特殊消息>
""".trim()

/** 模型写的 [骰子] 在生成完时换成真的点数，之后模型和她看到的都是同一个数。 */
private object DiceOutputTransformer : OutputMessageTransformer {
    private val bare = Regex("""\[骰子\]""")
    override suspend fun transform(
        ctx: me.rerere.rikkahub.data.ai.transformers.TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> = messages.map { message ->
        if (message.role != MessageRole.ASSISTANT) return@map message
        message.copy(parts = message.parts.map { part ->
            if (part is UIMessagePart.Text && bare.containsMatchIn(part.text))
                part.copy(text = bare.replace(part.text) { "[骰子:${kotlin.random.Random.nextInt(1, 7)}]" })
            else part
        })
    }
}
