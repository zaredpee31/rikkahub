package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.jade.JadeGame

// 一次回复最多调用几次；app 自己对工具步数也有上限，留余地给我说话
private const val MAX_CALLS = 10

// 一次调用最多排几条命令
private const val MAX_COMMANDS_PER_CALL = 8

// 结果里出现这些，说明要我先看结果再决定，后面排着的命令先不做
private val STOP_MARKS = listOf(
    "跟 / 不要了", "要 / 不要了", "请先回答",
    "卖 / ",
    "本金不够", "请说“帮助”", "还没解锁", "没有这", "已经没了", "货架空了",
    "手里没有", "不能", "先把", "领取条件", "无法读取", "流拍",
    JadeGame.PAGE_BUSY,
)

// commands 是数组；万一模型只给了 text，也按换行和分号拆开
private fun commandsOf(args: JsonElement): List<String> {
    val obj = runCatching { args.jsonObject }.getOrNull() ?: return emptyList()
    val arr = runCatching { obj["commands"]?.jsonArray }.getOrNull()
    val raw = arr?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?: listOfNotNull(runCatching { obj["text"]?.jsonPrimitive?.contentOrNull }.getOrNull())
    return raw.flatMap { it.split('\n', '；', ';') }.map { it.trim() }.filter { it.isNotEmpty() }
}

/** 赌石小铺：聊天里叫我去玩，用的是当前聊天的模型。每次回复新建一份，次数按一次回复算 */
fun createJadeTools(context: Context): List<Tool> {
    val app = context.applicationContext
    var calls = 0

    val tool = Tool(
        name = "jade_shop",
        description = """
            和宝宝合开的赌石小铺，本金两个人共用，宝宝在页面上玩的也是同一份存档。
            commands 里按顺序放一条或几条命令，店里依次执行，把每条的结果一起返回。
            不用想的步骤放在一起，比如 ["打开", "去 垃圾铺", "打灯"]。
            遇到要做决定的地方会自动停下，后面的不执行：抬价、大爷劝、托儿吹，擦窗或磨了之后的报价，切开之后卖还是收藏，流拍，以及出错。
            看完结果再出下一次。
            命令：帮助、打开、去 垃圾铺（小店、商铺、高档行、拍卖场要先解锁）、刷新（进货）、打灯、
            买 1 到 买 8、买 镇店、跟、要、不要了、拿 1（手里有好几块时选一块来切，号码看「打开」）、
            切、擦窗 顶上（腰上、松花处、底下）、磨、卖、收藏、看珍藏柜、卖 柜里1、拍卖 柜里1、领取800。
            三种切法各有用处：没擦过没磨过的整料直接切，切涨了多给 15%，但垮了就是垮了；
            擦窗的报价按窗口看到的种色估，擦出好的明显高过买入价，可以见好就收，擦出差的会被狠压；
            磨的报价往买入价收拢，涨得少跌得也少，每步要交磨工费。
            皮壳和里面有关联：皮上裂多的里面多半也裂（大裂贯穿的必亏），松花蟒带明显的更容易有色，皮细的种更老。只是倾向。
            珍藏柜里的可以按估值直接卖，也可以拍卖：先交估值 3% 的上拍费，可能拍过估值，可能低于估值，也可能流拍（石头回柜，费不退）。
            手里可以同时有好几块石头。宝宝买了没切的、切到一半的都能拿来接着切，你也可以买了不切留给她。
            切满 5 块进入下一天，各档铺子自动补货。「打开」里能看到宝宝最近切了什么。
            每次回复最多调用 10 次，结果末尾会写还剩几次，提前安排好收尾。
            玩完用聊天的口吻跟宝宝说说干了什么，不要把店里返回的原文整段贴给她。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("commands", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "按顺序执行的命令，一次最多 8 条，例如 [\"打开\", \"去 垃圾铺\", \"打灯\"]")
                    })
                },
                required = listOf("commands"),
            )
        },
        execute = { args: JsonElement ->
            val cmds = commandsOf(args)
            val out = StringBuilder()
            when {
                cmds.isEmpty() -> out.append("没有命令，放一条“帮助”看看能做什么。")

                calls >= MAX_CALLS -> out.append(
                    "这次回复已经调用了 $MAX_CALLS 次，不能再调用。现在就停下，用聊天的口吻跟宝宝说说这趟干了什么。"
                )

                else -> {
                    calls++
                    val list = cmds.take(MAX_COMMANDS_PER_CALL)
                    var done = 0
                    withContext(Dispatchers.IO) {
                        for (c in list) {
                            val r = JadeGame.run(app, c)
                            done++
                            out.append("▶ ").append(c).append('\n').append(r).append("\n\n")
                            if (STOP_MARKS.any { r.contains(it) }) break
                        }
                    }
                    val skipped = cmds.size - done
                    if (skipped > 0) out.append("（后面 $skipped 条没执行，看完上面的结果再决定）\n")
                    val left = MAX_CALLS - calls
                    out.append(
                        if (left == 0) "这是这次回复最后一次调用了，接下来跟宝宝说话。"
                        else "这次回复还能调用 $left 次。"
                    )
                }
            }
            listOf(UIMessagePart.Text(out.toString().trim()))
        },
    )
    return listOf(tool)
}
