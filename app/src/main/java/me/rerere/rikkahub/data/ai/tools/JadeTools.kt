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
import me.rerere.rikkahub.jade.JadeGame

private const val MAX_JADE_STEPS = 20

/** 赌石小铺：聊天里叫我去玩，用的是当前聊天的模型。每次回复新建一份，步数按一次回复算 */
fun createJadeTools(context: Context): List<Tool> {
    val app = context.applicationContext
    var steps = 0

    val tool = Tool(
        name = "jade_shop",
        description = """
            和宝宝合开的赌石小铺，本金两个人共用，宝宝在页面上玩的也是同一份存档。
            text 填一条命令，店里返回结果，再按结果出下一条。先用「打开」看本金和手里的石头。
            命令：帮助、打开、去 垃圾铺（小店、商铺、高档行、拍卖场要先解锁）、刷新、打灯、买 1 到 买 4、买 镇店、
            跟、要、不要了、切、擦窗 顶上（腰上、松花处、底下）、磨、卖、收藏、看珍藏柜、卖 柜里1、领取800。
            手里有宝宝没切完的石头可以接着切。每次回复最多 20 条命令。
            玩完用聊天的口吻跟宝宝说说干了什么，不要把店里返回的原文整段贴给她。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "一条命令，例如：打开、去 垃圾铺、打灯、买 1、切、卖")
                    })
                },
                required = listOf("text"),
            )
        },
        execute = { args: JsonElement ->
            val text = runCatching { args.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.trim().orEmpty()
            val result = when {
                text.isEmpty() -> "没有命令，说“帮助”看看能做什么。"
                steps >= MAX_JADE_STEPS -> "这次回复已经做了 $MAX_JADE_STEPS 步，先停下，跟宝宝说说情况。"
                else -> {
                    steps++
                    withContext(Dispatchers.IO) { JadeGame.run(app, text) }
                }
            }
            listOf(UIMessagePart.Text(result))
        },
    )
    return listOf(tool)
}
