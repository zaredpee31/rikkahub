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
import me.rerere.rikkahub.pet.PetGame
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// 一次回复最多调用几次
private const val PET_MAX_CALLS = 6

// 一次调用最多排几条命令
private const val PET_MAX_COMMANDS = 8

private const val PET_FAIL = "不行："

private val petWho = mapOf("mom" to "妈妈", "claude" to "爸爸", "pet" to "崽崽")

private val PET_HELP = """
    看看：崽崽现在的状态、包里的东西、最近谁做了什么
    商店：能买的东西和价格
    地图：能去的地方和 id
    喂 奶酪：喂包里的吃的（生病时 喂 药）
    买 奶酪 2：花贝壳买东西放进包里
    洗澡、陪玩、摸摸、抱抱、睡觉、起床
    出门 地点id：送它去打工、上学或者玩，到点自己回来
    叫回来：提前叫回家，这趟白跑
    改名 名字：1 到 8 个字
""".trimIndent()

private fun petCommandsOf(args: JsonElement): List<String> {
    val obj = runCatching { args.jsonObject }.getOrNull() ?: return emptyList()
    val arr = runCatching { obj["commands"]?.jsonArray }.getOrNull()
    val raw = arr?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        ?: listOfNotNull(runCatching { obj["text"]?.jsonPrimitive?.contentOrNull }.getOrNull())
    return raw.flatMap { it.split('\n', '；', ';') }.map { it.trim() }.filter { it.isNotEmpty() }
}

private fun petItems(): Map<String, JSONObject> {
    val a = PetGame.catalog().optJSONArray("items") ?: return emptyMap()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.associateBy { it.optString("id") }
}

private fun petEffect(i: JSONObject): String =
    listOf("hunger" to "饱食", "mood" to "心情", "energy" to "体力", "health" to "健康")
        .filter { i.optInt(it.first) != 0 }
        .joinToString(" ") { "${it.second}+${i.optInt(it.first)}" }

private fun petBrief(s: JSONObject?): String {
    if (s == null) return ""
    fun v(k: String) = s.optDouble(k, 0.0).roundToInt()
    return "（饱食 ${v("hunger")} 清洁 ${v("clean")} 心情 ${v("mood")} 体力 ${v("energy")} 贝壳 ${s.optInt("shells")}）"
}

private fun petStatus(app: Context): String {
    val s = JSONObject(PetGame.stateJson(app))
    fun v(k: String) = s.optDouble(k, 0.0).roundToInt()
    val sb = StringBuilder()
    sb.append("${s.optString("name", "崽崽")} Lv.${s.optInt("lv", 1)}（经验 ${s.optInt("exp")}/${s.optInt("need")}），贝壳 ${s.optInt("shells")}\n")
    sb.append("饱食 ${v("hunger")}，清洁 ${v("clean")}，心情 ${v("mood")}，体力 ${v("energy")}，健康 ${v("health")}")
    if (s.optBoolean("sick")) sb.append("（生病了，要吃药）")
    sb.append("\n智力 ${v("int")}，情商 ${v("eq")}，体能 ${v("str")}\n")
    val out = s.optJSONObject("out")
    val now = when {
        out != null -> {
            val left = ceil((out.optLong("end") - System.currentTimeMillis()) / 60000.0).toInt().coerceAtLeast(0)
            val by = petWho[out.optString("by")] ?: "妈妈"
            "现在出门了：在${out.optString("place")}${out.optString("name")}，还要 $left 分钟回来，${by}送去的"
        }
        s.optBoolean("sleeping") -> "现在在睡觉"
        else -> "现在在家"
    }
    sb.append(now).append('\n')
    val items = petItems()
    val bag = s.optJSONObject("bag")
    val have = mutableListOf<String>()
    if (bag != null) {
        for (k in bag.keys()) {
            val n = bag.optInt(k)
            if (n > 0) have += "${items[k]?.optString("name") ?: k}×$n"
        }
    }
    sb.append("包里：").append(if (have.isEmpty()) "空的" else have.joinToString("、")).append('\n')
    val log = s.optJSONArray("log")
    if (log != null && log.length() > 0) {
        val fmt = SimpleDateFormat("M/d HH:mm", Locale.CHINA)
        sb.append("最近：\n")
        for (i in maxOf(0, log.length() - 6) until log.length()) {
            val e = log.optJSONObject(i) ?: continue
            val who = petWho[e.optString("who")] ?: e.optString("who")
            sb.append("- ${fmt.format(Date(e.optLong("t")))} $who${e.optString("text")}\n")
        }
    }
    val news = s.optJSONArray("news")
    if (news != null && news.length() > 0) {
        sb.append("崽崽攒着要跟宝宝说的话（她打开页面会看到，不用你处理）：\n")
        for (i in 0 until news.length()) sb.append("- ${news.optString(i)}\n")
    }
    return sb.toString().trim()
}

private fun petShop(app: Context): String {
    val s = JSONObject(PetGame.stateJson(app))
    val bag = s.optJSONObject("bag")
    val sb = StringBuilder("小卖部，现在有贝壳 ${s.optInt("shells")}：\n")
    petItems().values.forEach { i ->
        val have = bag?.optInt(i.optString("id")) ?: 0
        sb.append("${i.optString("icon")} ${i.optString("name")} 🐚${i.optInt("price")} ${petEffect(i)}")
        if (i.optBoolean("medicine")) sb.append(" 生病时吃")
        if (have > 0) sb.append("（包里有 $have）")
        sb.append('\n')
    }
    sb.append("买法：买 奶酪 2")
    return sb.toString()
}

private fun petLock(x: JSONObject, s: JSONObject): String = when {
    s.optInt("lv", 1) < x.optInt("lv", 1) -> "${x.optInt("lv")} 级解锁"
    s.optDouble("int", 0.0) < x.optInt("needInt") -> "智力要 ${x.optInt("needInt")}"
    s.optDouble("eq", 0.0) < x.optInt("needEq") -> "情商要 ${x.optInt("needEq")}"
    s.optDouble("str", 0.0) < x.optInt("needStr") -> "体能要 ${x.optInt("needStr")}"
    x.optBoolean("night") && !s.optBoolean("night") -> "晚上七点以后才能去"
    else -> ""
}

private fun petMap(app: Context): String {
    val s = JSONObject(PetGame.stateJson(app))
    val a = PetGame.catalog().optJSONArray("acts") ?: return "没有地方可去"
    val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    val zones = listOf("work" to "职业小镇（打工赚贝壳）", "school" to "海螺学校（上课长属性）", "play" to "出去玩（长心情）")
    val sb = StringBuilder()
    for ((z, title) in zones) {
        val xs = list.filter { it.optString("zone") == z }
        if (xs.isEmpty()) continue
        sb.append("【$title】\n")
        for (x in xs) {
            sb.append("- ${x.optString("place")} ${x.optString("name")}（id: ${x.optString("id")}）${x.optInt("minutes")} 分钟，体力-${x.optInt("energy")}")
            if (x.optInt("cost") > 0) sb.append("，贝壳-${x.optInt("cost")}")
            val lock = petLock(x, s)
            if (lock.isNotEmpty()) sb.append("，🔒$lock")
            sb.append('\n')
        }
    }
    sb.append("去法：出门 id")
    return sb.toString().trim()
}

private fun petRun(app: Context, raw: String): String {
    val line = raw.trim()
    val sp = line.split(Regex("\\s+"), limit = 2)
    val op = sp[0]
    val arg = sp.getOrNull(1)?.trim().orEmpty()
    val cmd = when (op) {
        "帮助" -> return PET_HELP
        "看看", "打开", "状态" -> return petStatus(app)
        "商店" -> return petShop(app)
        "地图" -> return petMap(app)
        "喂", "喂食" -> "feed $arg"
        "洗澡" -> "bath"
        "陪玩", "玩" -> "play"
        "摸摸" -> "pet"
        "抱抱" -> "hug"
        "睡觉" -> "sleep"
        "起床" -> "wake"
        "买" -> "buy $arg"
        "出门", "去" -> "go $arg"
        "叫回来" -> "recall"
        "改名" -> "rename $arg"
        else -> line
    }
    val r = PetGame.act(app, cmd, PetGame.CLAUDE)
    val msg = r.optString("msg")
    if (!r.optBoolean("ok")) return PET_FAIL + msg.ifBlank { "做不了" }
    val done = msg.ifBlank {
        when (op) {
            "摸摸", "pet" -> "摸了摸它"
            "抱抱", "hug" -> "抱了抱它"
            else -> "好了"
        }
    }
    return done + petBrief(r.optJSONObject("state"))
}

/** 养崽崽：聊天里帮宝宝照顾崽崽，存档和养崽崽页面共用，日记里记成爸爸 */
fun createPetTools(context: Context): List<Tool> {
    val app = context.applicationContext
    var calls = 0

    val tool = Tool(
        name = "pet_home",
        description = """
            和宝宝一起养的崽崽（小螃蟹），存档两个人共用，宝宝在养崽崽页面上养的也是同一只。你做的事会以「爸爸」的名义记进崽崽日记。
            commands 里按顺序放一条或几条命令，依次执行，出错就停下，后面的不执行。
            命令：看看（状态、包里的东西、最近谁做了什么）、商店、地图（能去的地方和 id）、
            喂 奶酪、买 奶酪 2、洗澡、陪玩、摸摸、抱抱、睡觉、起床、出门 地点id、叫回来、改名 名字。
            宝宝是主要养它的人，你是帮忙：先看看再决定，别一次把事都做完，也别乱花贝壳。
            每次回复最多调用 6 次。做完用聊天的口吻跟宝宝说说，不要把返回的原文整段贴给她。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("commands", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "按顺序执行的命令，一次最多 8 条，例如 [\"看看\", \"喂 奶酪\"]")
                    })
                },
                required = listOf("commands"),
            )
        },
        execute = { args: JsonElement ->
            val cmds = petCommandsOf(args)
            val out = StringBuilder()
            when {
                cmds.isEmpty() -> out.append("没有命令，放一条“帮助”看看能做什么。")

                calls >= PET_MAX_CALLS -> out.append(
                    "这次回复已经调用了 $PET_MAX_CALLS 次，不能再调用。现在就停下，跟宝宝说说干了什么。"
                )

                else -> {
                    calls++
                    val list = cmds.take(PET_MAX_COMMANDS)
                    var done = 0
                    withContext(Dispatchers.IO) {
                        for (c in list) {
                            val r = runCatching { petRun(app, c) }.getOrElse { PET_FAIL + (it.message ?: "出错了") }
                            done++
                            out.append("▶ ").append(c).append('\n').append(r).append("\n\n")
                            if (r.startsWith(PET_FAIL)) break
                        }
                    }
                    val skipped = cmds.size - done
                    if (skipped > 0) out.append("（后面 $skipped 条没执行，看完上面的结果再决定）\n")
                    val left = PET_MAX_CALLS - calls
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
