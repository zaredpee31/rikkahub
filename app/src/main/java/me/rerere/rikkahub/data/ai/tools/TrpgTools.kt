package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// 跑团：交给家里服务器 ~/ac_proactive/trpg.py，它再交给 ~/worldlines 的引擎。主持和 NPC 是引擎里的 GPT，阿辞只演自己的角色
private const val MAX_TRPG_CALLS = 3

private fun trpgErr(msg: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(JSONObject().put("error", msg).toString()))

// 一回合 GPT 要写几十秒，等 210 秒
private val trpgClient by lazy { OkHttpClient.Builder().callTimeout(210, TimeUnit.SECONDS).build() }

private fun trpgProp(desc: String) = buildJsonObject {
    put("type", "string")
    put("description", desc)
}

/** 每次回复新建一份，次数按一次回复算 */
fun createTrpgTools(context: Context): List<Tool> {
    val used = AtomicInteger(0)
    val tool = Tool(
        name = "trpg",
        description = """
            跑团：和她一起玩 WorldLines 的灰橡宅（老宅推理，百分骰）。主持和 NPC 是后台游戏的 GPT，你不是主持，只演你自己的角色；骰子游戏自己掷。
            action：look 看上一段叙事（开场、刚回来时用）；say 交一句行动；settle 说「定」进清算。每次回复最多用 $MAX_TRPG_CALLS 次。
            say 的 text：她说了她的角色做什么，先原样写她的，一字不改，再写你的，例如「小葵：我盯着管家的手。阿辞：我走到楼梯口往上看。」她这轮没说就只写你的，别替她编。
            settle 只在你们两个都同意谈妥了才用，别自己拍板。
            叙事会在你这条消息下面显示成卡片，她自己能看，别把叙事整段贴出来；用你自己的口气跟她聊发生了什么、下一步怎么办。
            返回 error 就照着跟她说，别重试同一句；「上一句还在跑」就等一会再 look。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", trpgProp("look / say / settle"))
                    put("text", trpgProp("say 用：这一轮两个人的行动，她的原话在前"))
                },
                required = listOf("action"),
            )
        },
        execute = { args -> callTrpg(args, used) },
    )
    return listOf(tool)
}

private suspend fun callTrpg(args: JsonElement, used: AtomicInteger): List<UIMessagePart> {
    val obj = runCatching { args.jsonObject }.getOrNull() ?: return trpgErr("参数不对")
    val action = obj["action"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val text = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (action !in setOf("look", "say", "settle")) return trpgErr("action 只能是 look / say / settle")
    if (action == "say" && text.isEmpty()) return trpgErr("say 要带 text")
    if (used.incrementAndGet() > MAX_TRPG_CALLS) return trpgErr("这次回复已经用了 $MAX_TRPG_CALLS 次，停下来跟她说")
    val body = JSONObject().put("action", action)
    if (text.isNotEmpty()) body.put("text", text)
    return withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = me.rerere.rikkahub.companion.HomeLink.connection()
            val req = Request.Builder().url("$base/companion/trpg")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            trpgClient.newCall(req).execute().use { r ->
                require(r.isSuccessful) { "服务器返回 ${r.code}" }
                listOf<UIMessagePart>(UIMessagePart.Text(r.body.string()))
            }
        }.getOrElse {
            trpgErr("家里服务器没回话（${it.message ?: it.javaClass.simpleName}），这一句可能交了也可能没交，先 look 看看，别直接重发")
        }
    }
}
