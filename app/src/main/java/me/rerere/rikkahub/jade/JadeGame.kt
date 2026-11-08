package me.rerere.rikkahub.jade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/*
 * 赌石存档和文字命令，照 dushi-xiaopu 的 game.py / web.py 搬过来。
 * 网页（pageGet / pagePost）和我的工具（run）读写同一份存档，用同一把锁。
 */
object JadeGame {
    /** 宝宝正开着赌石页面时为 true，这时我不动存档 */
    @Volatile
    var pageOpen = false

    private val lock = Any()
    private const val LIMIT = 2 * 1024 * 1024
    private const val BROKEN = "赌石存档暂时无法读取，原数据没有修改。"
    const val PAGE_BUSY = "宝宝正在页面上玩，先不动，等她退出页面再来。"

    private val TIER_NAMES = listOf("垃圾铺", "小店", "商铺", "高档行", "拍卖场")
    private val FEES = longArrayOf(100, 300, 1000, 3000, 8000)
    private val RIND_COLORS = listOf("黄沙皮", "黑乌沙", "白盐沙", "红蜡壳", "青皮", "灰皮", "褐皮", "铁锈皮")
    private val RIND_FINE = listOf("极细", "细腻", "中等", "粗糙", "极粗")
    private val RIND_CRACK = listOf("无裂", "少量细裂", "明显裂纹", "满布裂绺")
    private val RIND_SONG = listOf("无松花", "星点松花", "片状松花", "满布松花")
    private val RIND_MANG = listOf("无蟒带", "细蟒带", "明显蟒带", "缠绕蟒带")
    private val ZHONG_NAMES = listOf("豆种", "糯种", "糯冰种", "冰种", "高冰种", "玻璃种")
    private val COLOR_NAMES = listOf("无色", "淡绿", "晴水绿", "阳绿", "浓绿", "满绿", "帝王绿", "黄翡", "蓝水", "紫罗兰", "春带彩")

    private val UNCLE_LINES = listOf(
        "「小姑娘，听大爷一句劝——这石头皮糙得跟砂纸似的，里面能有啥？十赌九输，放下吧。」大爷摇着蒲扇，一脸看透世事的表情。",
        "「这块？我看就是块砖头料！切开怕是全是白碴子，连个色都见不着。」大爷说得斩钉截铁，唾沫星子都飞出来了。",
        "「年轻人不听老人言呐……这种料子我见得多了，没一个能涨的。」大爷叹着气，仿佛已经看见了结局。",
        "「就这皮壳？松花蟒带一样拿不出手，纯纯交学费的货。」大爷背着手，头摇得像拨浪鼓。",
    )
    private val SHILL_LINES = listOf(
        "「哎哟！这块可了不得，刚才还有两个老板围着看呢，你再犹豫就被别人抢走啦！」旁边一个人凑过来，热情得有点过头。",
        "「这皮壳、这松花，一看就是老坑的货！切开不涨，我跟你姓！」他拍着胸脯保证，眼睛却直往店主那边瞟。",
        "「妹子你眼光是真毒，这批货里就数这块最亮眼，拿下准没错！」他笑得比店主还要开心。",
        "「这种货色，平时一上架就没了，也就你运气好赶上。」他压低声音，一副只告诉你一个人的样子。",
    )
    private val BOSS_INTROS = listOf(
        "一个挺着啤酒肚的老板挤了进来，围着石头转了两圈，眼睛越来越亮。",
        "旁边一个戴金链子的老板放下茶杯，慢慢踱了过来，盯着石头舍不得挪眼。",
        "一个夹着皮包的老板本来都要出门了，瞥见这块石头又折了回来。",
    )

    private val HELP = "打开：查看本金、统计和能去的铺子。\n去 铺子名：查看货架，例如“去 垃圾铺”。\n" +
        "刷新：付费刷新当前铺子。\n打灯：查看当前货架的打灯表现，照到和别处不一样的地方会提示。\n" +
        "买 号码 / 买 镇店：购买石头；遇到 NPC 时按提示说“跟”“要”或“不要了”。\n" +
        "切 / 擦窗 顶上、腰上或松花处、底下 / 磨：处理手里的石头，每块只能擦窗一次。\n" +
        "卖 / 收藏：处理切好的石头；看珍藏柜 / 卖 柜里1：查看或卖掉珍藏。\n" +
        "买不起所有可用货架上的石头且手里没有石头时，可说“领取800”获得800本金。"

    private val KNOWN = setOf("帮助", "打开", "刷新", "打灯", "跟", "要", "不要了", "切", "磨", "卖", "收藏", "看珍藏柜", "领取800")
    private val BUY = Regex("买\\s*([0-9]+|镇店)")
    private val SELL_CAB = Regex("卖\\s*柜里\\s*([0-9]+)")
    private val POINT_NAMES = mapOf("顶" to "顶上", "腰" to "腰上", "松花" to "松花处", "底" to "底下")
    private val RANKS = mapOf("暗" to 0, "弱" to 1, "中" to 2, "强" to 3)
    private val REMARKS = mapOf(
        "强true" to "底下多半有色，种也不差", "强false" to "种水不错，偏白肉",
        "中true" to "种和色都有些盼头，说不准", "中false" to "多半是白肉，种可能还行",
        "弱true" to "可能有层薄色，也可能是松花晃眼", "弱false" to "没什么盼头，也说不准是皮太厚",
        "暗false" to "皮厚或里面干，也有好货闷着",
    )
    private val MUTABLE = setOf(
        "money", "maxWealth", "tier", "unlockedTier", "shops", "owned", "cabinet", "history",
        "activeCut", "pendingNpc", "torchReadings", "xiaojieVisits", "shelfVersion"
    )

    // ================= 存档文件 =================

    private fun saveFile(context: Context) = File(context.applicationContext.filesDir, "jade/save.json")

    private fun revision(raw: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }

    private fun readRaw(file: File): ByteArray {
        val raw = file.readBytes()
        if (raw.size > LIMIT) error("存档过大")
        return raw
    }

    private fun readSave(file: File): JSONObject = JSONObject(String(readRaw(file), Charsets.UTF_8))

    // 先写临时文件再改名，写到一半断电也不会坏档
    private fun write(file: File, data: JSONObject) {
        val dir = file.parentFile ?: error("存档目录不存在")
        dir.mkdirs()
        val tmp = File(dir, ".save-" + System.nanoTime())
        try {
            tmp.outputStream().use { out ->
                out.write((data.toString(2) + "\n").toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) error("存档写不进去")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun roundStats() = JSONObject().put("played", false).put("cuts", 0).put("spent", 0).put("net", 0)

    private fun newSave(): JSONObject = JSONObject()
        .put("money", 10000)
        .put("maxWealth", 10000)
        .put("tier", 0)
        .put("unlockedTier", 0)
        .put("shelfVersion", 1)
        .put("shops", JSONObject().put("0", JadeEngine.genShop(0)))
        .put("owned", JSONArray())
        .put("cabinet", JSONArray())
        .put("history", JSONArray())
        .put("xiaokeLast", roundStats())
        .put("xiaojieVisits", JSONObject().put("visited", false).put("cuts", 0).put("spent", 0).put("net", 0))

    // ================= 网页用的接口，和 web.py 一样 =================

    private fun resp(status: Int, body: JSONObject): String =
        JSONObject().put("status", status).put("body", body.toString()).toString()

    fun pageGet(context: Context): String = synchronized(lock) {
        try {
            val f = saveFile(context)
            if (!f.exists()) write(f, newSave())
            val raw = readRaw(f)
            val data = JSONObject(String(raw, Charsets.UTF_8))
            resp(200, JSONObject().put("ok", true).put("revision", revision(raw)).put("save", data))
        } catch (e: Exception) {
            resp(500, JSONObject().put("ok", false).put("error", "存档暂时无法读取"))
        }
    }

    fun pagePost(context: Context, body: String): String = synchronized(lock) {
        try {
            if (body.toByteArray().size > LIMIT) error("请求大小异常")
            val payload = JSONObject(body)
            if (payload.keys().asSequence().toSet() != setOf("revision", "save")) error("请求格式异常")
            val expected = payload.getString("revision")
            val new = payload.getJSONObject("save")
            val f = saveFile(context)
            val raw = readRaw(f)
            if (expected != revision(raw)) {
                resp(409, JSONObject().put("ok", false).put("error", "存档已更新，请重新打开"))
            } else {
                validate(JSONObject(String(raw, Charsets.UTF_8)), new)
                if (new.toString().toByteArray().size > LIMIT) error("存档过大")
                write(f, new)
                resp(200, JSONObject().put("ok", true).put("revision", revision(f.readBytes())))
            }
        } catch (e: Exception) {
            resp(400, JSONObject().put("ok", false).put("error", "保存失败，原存档未更改"))
        }
    }

    private fun isInt(v: Any?) = v is Int || v is Long

    private fun truthy(v: Any?): Boolean = when (v) {
        null, JSONObject.NULL -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty()
        is JSONObject -> v.length() > 0
        is JSONArray -> v.length() > 0
        else -> true
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a is JSONObject && b is JSONObject ->
            a.length() == b.length() && a.keys().asSequence().all { b.has(it) && same(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray ->
            a.length() == b.length() && (0 until a.length()).all { same(a.opt(it), b.opt(it)) }
        else -> a == b
    }

    private fun objs(arr: JSONArray): List<JSONObject> = (0 until arr.length()).map { arr.getJSONObject(it) }

    // 网页只能改这几项，别的字段原样带回来
    private fun validate(old: JSONObject, new: JSONObject) {
        for (k in old.keys()) {
            if (k in MUTABLE) continue
            if (!new.has(k) || !same(old.opt(k), new.opt(k))) error("其他字段被改动")
        }
        for (k in new.keys()) if (!old.has(k) && k !in MUTABLE) error("未知字段")
        for (k in listOf("money", "maxWealth", "tier", "unlockedTier", "shops", "owned", "cabinet", "history", "xiaojieVisits")) {
            if (!new.has(k)) error("缺少字段")
        }
        val money = new.get("money")
        if (!isInt(money) || (money as Number).toLong() < 0 || !isInt(new.get("maxWealth"))) error("金额异常")
        val tier = new.get("tier")
        val unlocked = new.get("unlockedTier")
        if (!isInt(tier) || !isInt(unlocked)) error("铺子状态异常")
        val t = (tier as Number).toInt()
        val u = (unlocked as Number).toInt()
        if (t < 0 || t > u || u > 4) error("铺子状态异常")
        val shops = new.get("shops") as? JSONObject ?: error("货架异常")
        for (k in shops.keys()) if (shops.get(k) !is JSONArray) error("货架异常")
        for (k in listOf("owned", "cabinet", "history")) if (new.get(k) !is JSONArray) error("石头状态异常")
        if (new.getJSONArray("owned").length() > 1) error("石头状态异常")

        val visit = new.get("xiaojieVisits") as? JSONObject ?: error("玩家统计异常")
        val previous = old.optJSONObject("xiaojieVisits") ?: JSONObject()
        if (visit.keys().asSequence().toSet() != setOf("visited", "cuts", "spent", "net") || visit.get("visited") !is Boolean) {
            error("玩家统计异常")
        }
        for (k in listOf("cuts", "spent", "net")) if (!isInt(visit.get(k))) error("玩家统计异常")
        if (visit.getLong("cuts") < 0 || visit.getLong("spent") < 0) error("玩家统计异常")
        if (previous.optBoolean("visited") &&
            (!visit.getBoolean("visited") || visit.getLong("cuts") < previous.optLong("cuts") ||
                visit.getLong("spent") < previous.optLong("spent"))
        ) error("玩家统计不能被网页清空")

        for (k in shops.keys()) {
            val shelf = shops.getJSONArray(k)
            for (i in 0 until shelf.length()) if (shelf.get(i) !is JSONObject) error("货架异常")
            val list = objs(shelf)
            val normal = list.filter { !truthy(it.opt("zhen")) }
            if (normal.size > 4 || list.count { truthy(it.opt("zhen")) } > 1) error("货架异常")
            val numbers = normal.map { it.opt("shelfNo") }
            if (numbers.any { !isInt(it) || (it as Number).toInt() !in 1..4 }) error("编号异常")
            if (numbers.map { (it as Number).toInt() }.toSet().size != numbers.size) error("编号异常")
        }
        val owned = new.getJSONArray("owned")
        if (owned.length() > 0) {
            val active = new.opt("activeCut") as? JSONObject ?: error("正在切的石头不匹配")
            if (!same(active.opt("stoneId"), owned.getJSONObject(0).opt("id"))) error("正在切的石头不匹配")
        } else if (truthy(new.opt("activeCut"))) {
            error("正在切的石头不存在")
        }
    }

    // ================= 我的文字命令，和 game.py 一样 =================

    fun run(context: Context, command: String): String = synchronized(lock) {
        if (pageOpen) {
            PAGE_BUSY
        } else {
            try {
                runLocked(saveFile(context), command)
            } catch (e: Exception) {
                BROKEN
            }
        }
    }

    private fun n(x: Long) = String.format(Locale.US, "%,d", x)

    private fun signed(x: Long) = String.format(Locale.US, "%+,d", x)

    private fun kg(st: JSONObject) = String.format(Locale.US, "%.1f", st.optDouble("weight"))

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun <T> choose(list: List<T>): T = list[Random.nextInt(list.size)]

    private fun raise(price: Long): Long =
        price + max(10L, (floor(price * (0.10 + Random.nextDouble() * 0.10) / 10 + 0.5) * 10).toLong())

    private fun current(save: JSONObject): JSONObject =
        save.optJSONObject("xiaokeCurrent") ?: roundStats().also { save.put("xiaokeCurrent", it) }

    private fun add(obj: JSONObject, key: String, delta: Long) {
        obj.put(key, obj.optLong(key) + delta)
    }

    private fun zhongName(inner: JSONObject) = ZHONG_NAMES.getOrElse(inner.optInt("zhong")) { "?" }

    private fun colorName(inner: JSONObject) = COLOR_NAMES.getOrElse(inner.optInt("color")) { "?" }

    private fun flaws(inner: JSONObject): String {
        val crack = listOf("无裂", "有细裂", "大裂贯穿").getOrElse(inner.optInt("crack")) { "?" }
        val cotton = listOf("无棉", "少棉", "重棉").getOrElse(inner.optInt("cotton")) { "?" }
        val spot = listOf("无黑点", "有黑点").getOrElse(inner.optInt("spot")) { "?" }
        return "$crack $cotton $spot"
    }

    private fun resultTier(ratio: Double, inner: JSONObject): Int = when {
        ratio >= 6 && inner.optInt("zhong") == 5 && inner.optInt("color") >= 5 -> 4
        ratio < 0.5 -> 0
        ratio < 1 -> 1
        ratio < 2.5 -> 2
        else -> 3
    }

    private fun rindAttrs(st: JSONObject): String {
        val r = st.getJSONObject("rind")
        return listOf(
            RIND_COLORS.getOrElse(r.optInt("color")) { "?" }, RIND_FINE.getOrElse(r.optInt("fine")) { "?" },
            RIND_CRACK.getOrElse(r.optInt("crack")) { "?" }, RIND_SONG.getOrElse(r.optInt("song")) { "?" },
            RIND_MANG.getOrElse(r.optInt("mang")) { "?" },
        ).joinToString(" ")
    }

    private fun shelfOf(save: JSONObject, tier: Int): JSONArray =
        save.getJSONObject("shops").optJSONArray(tier.toString()) ?: error("已解锁铺子缺少货架")

    private fun without(arr: JSONArray, id: String): JSONArray =
        JSONArray().also { out -> objs(arr).filter { it.optString("id") != id }.forEach { out.put(it) } }

    private fun ordered(shelf: JSONArray): Pair<List<JSONObject>, List<JSONObject>> {
        val list = objs(shelf)
        return list.filter { !it.optBoolean("zhen") }.sortedBy { it.optInt("shelfNo") } to list.filter { it.optBoolean("zhen") }
    }

    private fun ensureShelfNumbers(save: JSONObject) {
        val shops = save.getJSONObject("shops")
        for (k in shops.keys()) {
            val normal = objs(shops.getJSONArray(k)).filter { !it.optBoolean("zhen") }
            val used = mutableSetOf<Int>()
            for (s in normal) {
                if (!s.has("shelfNo")) continue
                val v = s.get("shelfNo")
                if (!isInt(v)) error("货架编号异常")
                val num = (v as Number).toInt()
                if (num !in 1..4 || num in used) error("货架编号异常")
                used.add(num)
            }
            for (s in normal) {
                if (s.has("shelfNo")) continue
                val num = (1..4).firstOrNull { it !in used } ?: error("货架普通石超过四块")
                s.put("shelfNo", num)
                used.add(num)
            }
        }
    }

    private fun runLocked(path: File, raw: String): String {
        var command = raw.trim()
        if (command == "一刀切") command = "切"
        val known = command in KNOWN || command.startsWith("擦窗") ||
            (command.startsWith("去") && command.substring(1).trim().isNotEmpty()) ||
            BUY.matchEntire(command) != null || SELL_CAB.matchEntire(command) != null
        if (command == "帮助" && !path.exists()) return HELP
        if (!known && !path.exists()) return "请说“帮助”查看可用动作。"

        val save = if (path.exists()) readSave(path) else newSave().also { write(path, it) }
        ensureShelfNumbers(save)
        if (!known && !truthy(save.opt("pendingNpc"))) return "请说“帮助”查看可用动作。"

        val now = System.currentTimeMillis() / 1000
        if (command == "打开" && !truthy(save.opt("pendingNpc"))) {
            val last = save.opt("lastActionAt")
            if (last is Number && now - last.toDouble() > 3600) {
                save.put("xiaokeLast", save.optJSONObject("xiaokeCurrent") ?: roundStats())
                save.put("xiaokeCurrent", roundStats())
            }
        }
        if (known) save.put("lastActionAt", now)
        current(save)

        if (truthy(save.opt("pendingNpc"))) {
            val (result, changed) = pending(save, command)
            if (known || changed) write(path, save)
            return result
        }
        if (command == "帮助") {
            write(path, save)
            return HELP
        }
        val (stone, active) = activeStone(save)
        if (command == "打开") {
            val result = openText(save, stone, active)
            if (save.getJSONObject("xiaojieVisits").optBoolean("visited")) {
                save.put("xiaojieVisits", JSONObject().put("visited", false).put("cuts", 0).put("spent", 0).put("net", 0))
            }
            write(path, save)
            return result
        }
        if (command == "看珍藏柜") {
            val result = cabinetText(save)
            write(path, save)
            return result
        }
        SELL_CAB.matchEntire(command)?.let { m ->
            val result = sellCabinet(save, m.groupValues[1].toIntOrNull() ?: 0)
            write(path, save)
            return result
        }
        if (stone != null && active != null) {
            val result = cut(save, stone, active, command)
            write(path, save)
            return result
        }
        if (command in setOf("切", "磨", "卖", "收藏") || command.startsWith("擦窗")) {
            write(path, save)
            return "手里没有正在切的石头。"
        }
        if (command.startsWith("去")) {
            val name = command.substring(1).trim()
            val result = shelfText(save, name)
            val idx = TIER_NAMES.indexOf(name)
            if (idx >= 0 && idx <= save.getInt("unlockedTier") && idx != save.getInt("tier")) save.put("tier", idx)
            write(path, save)
            return result
        }
        if (command == "领取800") {
            val unlocked = save.getInt("unlockedTier")
            val shops = save.getJSONObject("shops")
            val prices = (0..unlocked).flatMap { t ->
                shops.optJSONArray(t.toString())?.let { arr -> objs(arr).map { it.getLong("price") } } ?: emptyList()
            }
            val money = save.getLong("money")
            val eligible = if (prices.isNotEmpty()) money < prices.min() else money < 200
            if (!eligible) {
                write(path, save)
                return "还没有达到领取条件：手头的钱还能买到石头。"
            }
            save.put("money", money + 800)
            save.put("maxWealth", max(save.getLong("maxWealth"), save.getLong("money")))
            write(path, save)
            return "领到800本金｜本金 ${n(save.getLong("money"))}"
        }
        if (command == "刷新") {
            val tier = save.getInt("tier")
            val fee = FEES[tier]
            if (save.getLong("money") < fee) {
                write(path, save)
                return "本金不够，刷新要¥${n(fee)}"
            }
            val fresh = JadeEngine.genShop(tier)
            val cache = save.optJSONObject("torchReadings")
            if (cache != null) objs(shelfOf(save, tier)).forEach { cache.remove(it.optString("id")) }
            save.getJSONObject("shops").put(tier.toString(), fresh)
            save.put("money", save.getLong("money") - fee)
            val cur = current(save)
            cur.put("played", true)
            add(cur, "spent", fee)
            add(cur, "net", -fee)
            write(path, save)
            return shelfText(save, TIER_NAMES[tier])
        }
        val result = when {
            command == "打灯" -> torch(save).first
            command.startsWith("买") -> {
                val sel = BUY.matchEntire(command)?.groupValues?.get(1) ?: return "请说“帮助”查看可用动作。"
                buy(save, sel).first
            }
            else -> "当前没有需要回答的 NPC。"
        }
        write(path, save)
        return result
    }

    private fun stats(stat: JSONObject?, flag: String, empty: String): String {
        if (stat == null || !stat.optBoolean(flag)) return empty
        val net = stat.optLong("net")
        val profit = (if (net >= 0) "净赚" else "净亏") + "¥" + n(abs(net))
        return "切了${stat.optLong("cuts")}块，花¥${n(stat.optLong("spent"))}，$profit"
    }

    private fun openText(save: JSONObject, stone: JSONObject?, active: JSONObject?): String {
        val names = TIER_NAMES.take(save.getInt("unlockedTier") + 1).joinToString("、")
        val lines = mutableListOf(
            "💰 本金：${n(save.getLong("money"))}",
            "🪨 AI上次：" + stats(save.optJSONObject("xiaokeLast"), "played", "还没玩过"),
            "👀 玩家来过：" + stats(save.optJSONObject("xiaojieVisits"), "visited", "还没来过"),
        )
        if (stone != null && active != null) lines.add(currentDisplay(stone, active))
        lines.add("🎒 珍藏柜：${save.getJSONArray("cabinet").length()}件")
        lines.add("🏪 能去：$names")
        return lines.joinToString("\n")
    }

    private fun shelfText(save: JSONObject, name: String): String {
        val tier = TIER_NAMES.indexOf(name)
        if (tier < 0) return "没有这家铺子。"
        if (tier > save.getInt("unlockedTier")) return "${name}还没解锁。"
        val shelf = shelfOf(save, tier)
        val fee = FEES[tier]
        if (shelf.length() == 0) return "货架空了，刷新¥${n(fee)}"
        fun detail(st: JSONObject) =
            "${st.optString("name")} ${kg(st)}kg ¥${n(st.getLong("price"))} 皮壳${st.optInt("S")}｜${rindAttrs(st)}"
        val (normal, crown) = ordered(shelf)
        val lines = mutableListOf("$name ｜ 刷新¥${n(fee)}")
        normal.forEach { lines.add("${it.optInt("shelfNo")}. ${detail(it)}") }
        crown.forEach { lines.add("👑 镇店石 ${detail(it)}") }
        return lines.joinToString("\n")
    }

    private fun selected(save: JSONObject, selection: String): Pair<JSONObject?, String> {
        val (normal, crown) = ordered(shelfOf(save, save.getInt("tier")))
        if (selection == "镇店") return if (crown.isNotEmpty()) crown[0] to "镇店石" else null to ""
        val number = selection.toIntOrNull() ?: return null to ""
        return normal.firstOrNull { it.optInt("shelfNo") == number }?.let { it to "${number}号" } ?: (null to "")
    }

    private fun purchase(save: JSONObject, stone: JSONObject, label: String, price: Long): Pair<String, Boolean> {
        if (save.getLong("money") < price) return "本金不够，要¥${n(price)}" to false
        save.put("money", save.getLong("money") - price)
        val cur = current(save)
        cur.put("played", true)
        add(cur, "spent", price)
        add(cur, "net", -price)
        val key = save.getInt("tier").toString()
        val id = stone.getString("id")
        save.getJSONObject("shops").put(key, without(save.getJSONObject("shops").getJSONArray(key), id))
        val lights = save.optJSONObject("torchReadings")?.remove(id)
        stone.put("price", price)
        save.getJSONArray("owned").put(stone)
        save.put(
            "activeCut", JSONObject()
                .put("stoneId", stone.get("id")).put("phase", "new").put("grindFees", 0)
                .put("lights", lights ?: JSONObject.NULL)
        )
        save.remove("pendingNpc")
        val third = if (stone.getJSONObject("rind").optInt("song") != 0) "松花处" else "底下"
        return "买下 $label ¥${n(price)}｜本金 ${n(save.getLong("money"))}\n怎么切？一刀切 / 擦窗 顶上·腰上·$third / 慢慢磨" to true
    }

    private fun buy(save: JSONObject, selection: String): Pair<String, Boolean> {
        val (stone, label) = selected(save, selection)
        if (stone == null) {
            val num = selection.toIntOrNull()
            if (num != null && num in 1..4) return "${num}号已经没了" to false
            return "货架上没有这块石头。" to false
        }
        val price = stone.getLong("price")
        if (save.getLong("money") < price) return "本金不够，要¥${n(price)}" to false
        val roll = Random.nextDouble()
        if (roll < 0.70) return purchase(save, stone, label, price)
        val pending = JSONObject()
            .put("stone_id", stone.get("id")).put("tier", save.getInt("tier")).put("label", label).put("price", price)
        if (roll < 0.85) {
            val r = Random.nextDouble()
            pending.put("kind", "boss").put("total", if (r < 0.50) 1 else if (r < 0.85) 2 else 3).put("done", 1)
            pending.put("price", raise(price))
            save.put("pendingNpc", pending)
            return "🧔${choose(BOSS_INTROS)}他出价 ¥${n(pending.getLong("price"))}。跟 / 不要了" to true
        }
        val uncle = roll < 0.95
        pending.put("kind", if (uncle) "uncle" else "shill")
        save.put("pendingNpc", pending)
        val line = choose(if (uncle) UNCLE_LINES else SHILL_LINES)
        return (if (uncle) "👴" else "🗣️") + line + " 还要吗？（要 / 不要了）" to true
    }

    private fun pending(save: JSONObject, command: String): Pair<String, Boolean> {
        val p = save.getJSONObject("pendingNpc")
        val boss = p.optString("kind") == "boss"
        val ask = if (boss) "请先回答：跟 / 不要了。" else "请先回答：要 / 不要了。"
        if (command !in setOf("跟", "要", "不要了")) return ask to false
        val tierKey = p.getInt("tier").toString()
        val shelf = save.getJSONObject("shops").getJSONArray(tierKey)
        val stone = objs(shelf).firstOrNull { same(it.opt("id"), p.opt("stone_id")) }
        if (stone == null || p.getInt("tier") != save.getInt("tier")) error("待处理石头不存在")
        val label = p.optString("label")
        if (command == "不要了") {
            save.remove("pendingNpc")
            if (!boss) return "好，不买了。" to true
            val id = stone.getString("id")
            save.getJSONObject("shops").put(tierKey, without(shelf, id))
            save.optJSONObject("torchReadings")?.remove(id)
            val inner = stone.getJSONObject("inner")
            val value = stone.getLong("value")
            val offered = p.getLong("price")
            val diff = value - offered
            val ratio = value.toDouble() / stone.getLong("price")
            save.getJSONArray("history").put(
                JSONObject().put("id", id).put("name", stone.optString("name")).put("date", today())
                    .put("price", offered).put("got", value).put("tier", resultTier(ratio, inner)).put("inner", inner)
                    .put("method", "围观老板切石").put("kept", false).put("watched", true)
            )
            val profit = (if (diff >= 0) "赚了" else "亏了") + "¥${n(abs(diff))}"
            return "🧔老板抱着${label}走了，当场一刀……${zhongName(inner)} ${colorName(inner)}｜${flaws(inner)}｜$profit" to true
        }
        if (boss && command == "跟") {
            val price = p.getLong("price")
            if (save.getLong("money") < price) return "本金不够，要¥${n(price)}" to false
            if (p.getInt("done") >= p.getInt("total")) return purchase(save, stone, label, price)
            p.put("done", p.getInt("done") + 1)
            p.put("price", raise(price))
            return "🧔老板又加价：「我出 ¥${n(p.getLong("price"))}！」跟 / 不要了" to true
        }
        if (!boss && command == "要") return purchase(save, stone, label, p.getLong("price"))
        return ask to false
    }

    private fun showPoint(p: JSONObject): String {
        val level = p.optString("level")
        return if (level != "暗") p.optString("name") + "·" + level + (if (p.optBoolean("colored")) "有色" else "无色")
        else p.optString("name") + "·暗"
    }

    private fun hitSuffix(points: List<JSONObject>): String {
        val hit = points.filter { it.optBoolean("hit") }.map { POINT_NAMES[it.optString("name")] ?: it.optString("name") }
        if (hit.isEmpty()) return ""
        val amount = when (hit.size) {
            1 -> "这处"
            2 -> "这两处"
            else -> "这三处"
        }
        return "（" + hit.joinToString("、") + amount + "跟别处不太一样）"
    }

    private fun mergeHits(old: JSONArray, fresh: JSONArray) {
        if (old.length() != 3 || fresh.length() != 3 ||
            (0 until 3).any { old.getJSONObject(it).optString("name") != fresh.getJSONObject(it).optString("name") }
        ) error("已有打灯点位不匹配")
        for (i in 0 until 3) old.getJSONObject(i).put("hit", fresh.getJSONObject(i).getBoolean("hit"))
    }

    private fun torch(save: JSONObject): Pair<String, Boolean> {
        val tier = save.getInt("tier")
        val shelf = shelfOf(save, tier)
        if (shelf.length() == 0) return "货架空了，刷新¥${n(FEES[tier])}" to false
        val cache = save.optJSONObject("torchReadings") ?: JSONObject().also { save.put("torchReadings", it) }
        val needs = objs(shelf).filter { s ->
            val c = cache.optJSONArray(s.getString("id"))
            c == null || objs(c).any { !it.has("hit") }
        }
        for (s in needs) {
            val fresh = JadeEngine.sampleLight(s)
            val id = s.getString("id")
            val old = cache.optJSONArray(id)
            if (old == null) cache.put(id, fresh) else mergeHits(old, fresh)
        }
        fun line(label: String, st: JSONObject): String {
            val points = objs(cache.getJSONArray(st.getString("id")))
            fun dark(p: JSONObject) = p.optString("level") == "暗"
            val best = points.maxBy { (RANKS[it.optString("level")] ?: 0) * 2 + if (!dark(it) && it.optBoolean("colored")) 1 else 0 }
            val remark = REMARKS[best.optString("level") + (!dark(best) && best.optBoolean("colored"))] ?: ""
            return label + " " + points.joinToString(" ") { showPoint(it) } + "｜" + remark + hitSuffix(points)
        }
        val (normal, crown) = ordered(shelf)
        val lines = normal.map { line("${it.optInt("shelfNo")}号", it) } + crown.map { line("👑", it) }
        return lines.joinToString("\n") to needs.isNotEmpty()
    }

    private fun activeStone(save: JSONObject): Pair<JSONObject?, JSONObject?> {
        val owned = save.getJSONArray("owned")
        if (owned.length() > 1) error("手里有多块旧石头，不能自动选择")
        if (owned.length() == 0) {
            if (truthy(save.opt("activeCut"))) error("正在切的石头不存在")
            return null to null
        }
        val stone = owned.getJSONObject(0)
        var active = save.optJSONObject("activeCut")
        if (active == null) {
            val lights = save.optJSONObject("torchReadings")?.opt(stone.getString("id"))
            active = JSONObject().put("stoneId", stone.get("id")).put("phase", "new").put("grindFees", 0)
                .put("lights", lights ?: JSONObject.NULL)
            save.put("activeCut", active)
            val cur = current(save)
            cur.put("played", true)
            add(cur, "spent", stone.getLong("price"))
            add(cur, "net", -stone.getLong("price"))
        }
        if (!same(active.opt("stoneId"), stone.opt("id"))) error("正在切的石头不匹配")
        return stone to active
    }

    private fun lightsText(stone: JSONObject, active: JSONObject): String {
        val existing = active.optJSONArray("lights")
        val points: JSONArray
        if (existing == null || existing.length() == 0 || objs(existing).any { !it.has("hit") }) {
            val fresh = JadeEngine.sampleLight(stone)
            points = if (existing != null && existing.length() > 0) {
                mergeHits(existing, fresh)
                existing
            } else fresh
            active.put("lights", points)
        } else {
            points = existing
        }
        val list = objs(points)
        return list.joinToString(" ") { showPoint(it) } + hitSuffix(list)
    }

    private fun currentDisplay(stone: JSONObject, active: JSONObject): String {
        val lines = mutableListOf(
            "🔪 正在切：${stone.optString("name")} ${kg(stone)}kg ¥${n(stone.getLong("price"))}｜${rindAttrs(stone)}",
            "   打灯：" + lightsText(stone, active),
        )
        val phase = active.optString("phase")
        active.optJSONObject("window")?.let { w ->
            val li = w.getJSONObject("inner")
            lines.add("   已擦窗${w.optString("position")}：局部 ${zhongName(li)} ${colorName(li)}｜窗口报价¥${n(w.getLong("offer"))}")
        }
        if (phase == "grind1" || phase == "grind2") {
            val li = active.getJSONObject("local")
            var visible = zhongName(li)
            if (phase == "grind2") visible += " " + colorName(li)
            lines.add("   已磨：局部 $visible｜磨口报价¥${n(active.getLong("offer"))}")
        }
        if (phase == "revealed") {
            val li = stone.getJSONObject("inner")
            lines.add("   已切开：${zhongName(li)} ${colorName(li)}｜${flaws(li)}｜估价¥${n(stone.getLong("value"))}")
        }
        val choices = mapOf(
            "new" to "切 / 擦窗 / 磨", "window" to "卖 / 切 / 磨", "grind1" to "卖 / 磨",
            "grind2" to "卖 / 磨", "revealed" to "卖 / 收藏",
        )
        lines.add("   接下来：" + (choices[phase] ?: ""))
        return lines.joinToString("\n")
    }

    private fun refreshUnlocked(save: JSONObject) {
        val thresholds = longArrayOf(0, 15000, 20000, 50000, 100000)
        while (save.getInt("unlockedTier") < 4) {
            val next = save.getInt("unlockedTier") + 1
            val balance = if (next == 1) save.getLong("maxWealth") else save.getLong("money")
            if (balance < thresholds[next]) break
            val shops = save.getJSONObject("shops")
            if (!shops.has(next.toString())) shops.put(next.toString(), JadeEngine.genShop(next))
            save.put("unlockedTier", next)
        }
    }

    private fun finish(save: JSONObject, stone: JSONObject, active: JSONObject, keep: Boolean): String {
        val phase = active.optString("phase")
        if (keep && phase != "revealed") return "这块还没有完全切开，不能收藏。"
        if (phase !in setOf("window", "grind1", "grind2", "revealed")) return "先把这块石头切开或擦窗、磨一磨。"
        val offer = if (phase == "revealed") stone.getLong("value") else active.getLong("offer")
        val received = if (keep) 0L else offer
        val cur = current(save)
        cur.put("played", true)
        add(cur, "cuts", 1)
        add(cur, "net", received)
        if (keep) {
            val item = JSONObject(stone.toString())
            item.put("value", offer)
            item.put("keptDay", today())
            item.remove("_winOffer")
            item.put("keptMethod", active.optString("method", "一刀切"))
            save.getJSONArray("cabinet").put(item)
        } else {
            save.put("money", save.getLong("money") + offer)
            save.put("maxWealth", max(save.getLong("maxWealth"), save.getLong("money")))
        }
        val inner = stone.getJSONObject("inner")
        val ratio = offer.toDouble() / stone.getLong("price")
        save.getJSONArray("history").put(
            JSONObject().put("id", stone.get("id")).put("name", stone.optString("name")).put("date", today())
                .put("price", stone.getLong("price")).put("got", received).put("tier", resultTier(ratio, inner))
                .put("inner", inner).put("method", active.optString("method", "切石")).put("kept", keep)
                .put("grindFees", active.optLong("grindFees"))
        )
        save.put("owned", JSONArray())
        save.remove("activeCut")
        save.optJSONObject("torchReadings")?.remove(stone.optString("id"))
        if (!keep) refreshUnlocked(save)
        return "${if (keep) "收藏" else "卖出"} ¥${n(offer)}｜本金 ${n(save.getLong("money"))}"
    }

    private fun cabinetText(save: JSONObject): String {
        val items = objs(save.getJSONArray("cabinet"))
        if (items.isEmpty()) return "珍藏柜是空的。"
        return items.mapIndexed { i, item ->
            val li = item.getJSONObject("inner")
            "${i + 1}. ${item.optString("name")}｜${zhongName(li)} ${colorName(li)}｜${flaws(li)}｜估值¥${n(item.getLong("value"))}"
        }.joinToString("\n")
    }

    private fun sellCabinet(save: JSONObject, index: Int): String {
        val cabinet = save.getJSONArray("cabinet")
        if (index < 1 || index > cabinet.length()) return "珍藏柜里没有这一件。"
        val item = cabinet.remove(index - 1) as JSONObject
        val value = item.getLong("value")
        save.put("money", save.getLong("money") + value)
        save.put("maxWealth", max(save.getLong("maxWealth"), save.getLong("money")))
        objs(save.getJSONArray("history")).firstOrNull { same(it.opt("id"), item.opt("id")) && it.optBoolean("kept") }?.let {
            it.put("got", value)
            it.put("kept", false)
        }
        val cur = current(save)
        cur.put("played", true)
        add(cur, "net", value)
        refreshUnlocked(save)
        return "卖出柜里$index ¥${n(value)}｜本金 ${n(save.getLong("money"))}"
    }

    private fun cut(save: JSONObject, stone: JSONObject, active: JSONObject, command: String): String {
        val phase = active.optString("phase")
        val price = stone.getLong("price")
        val value = stone.getLong("value")
        if (command == "切") {
            if (phase != "new" && phase != "window") return "接下来：" + if (phase == "revealed") "卖 / 收藏" else "卖 / 磨"
            active.put("phase", "revealed").put("method", "一刀切").put("offer", value)
            val li = stone.getJSONObject("inner")
            val diff = value - price - active.optLong("grindFees")
            return "一刀下去……${zhongName(li)} ${colorName(li)}｜${flaws(li)}｜估价¥${n(value)}（${signed(diff)}）\n卖 / 收藏"
        }
        if (command.startsWith("擦窗")) {
            if (stone.optBoolean("windowed") || truthy(active.opt("window"))) return "这块石头已经擦过窗了。"
            val hasSong = stone.getJSONObject("rind").optInt("song") != 0
            val third = if (hasSong) "松花处" else "底下"
            val positions = mapOf("顶上" to "顶", "腰上" to "腰", third to if (hasSong) "松花" else "底")
            val position = command.substring(2).trim()
            if (phase != "new") return "现在不能擦窗。"
            val point = positions[position] ?: return "这块能擦：顶上、腰上、$third"
            val result = JadeEngine.windowResult(stone, point)
            val offer = result.getLong("offer")
            val li = result.getJSONObject("inner")
            stone.put("winDir", result.getJSONObject("dir"))
            stone.put("windowed", true)
            stone.put("_winOffer", offer)
            active.put("phase", "window")
                .put("window", JSONObject().put("position", position).put("inner", li).put("offer", offer))
                .put("offer", offer)
                .put("method", "擦窗后窗口卖出")
            return "擦开$position……局部 ${zhongName(li)} ${colorName(li)}｜窗口报价¥${n(offer)}\n卖 / 切 / 磨"
        }
        if (command == "磨") {
            if (phase !in setOf("new", "window", "grind1", "grind2")) return "接下来：卖 / 收藏"
            val step = when (phase) {
                "new" -> 0
                "window", "grind1" -> 1
                else -> 2
            }
            val fee = max(20L, min(500L, (floor(price * 0.01 / 10 + 0.5) * 10).toLong()))
            if (save.getLong("money") < fee) return "本金不够，磨工费要¥${n(fee)}"
            val result = if (step == 2) null else JadeEngine.grindResult(stone, step)
            save.put("money", save.getLong("money") - fee)
            add(active, "grindFees", fee)
            stone.put("grindStep", max(stone.optInt("grindStep"), step + 1))
            val cur = current(save)
            cur.put("played", true)
            add(cur, "spent", fee)
            add(cur, "net", -fee)
            if (result == null) {
                active.put("phase", "revealed").put("method", "慢慢磨").put("offer", value)
                val li = stone.getJSONObject("inner")
                val diff = value - price - active.optLong("grindFees")
                return "磨透了……${zhongName(li)} ${colorName(li)}｜${flaws(li)}｜估价¥${n(value)}（${signed(diff)}）\n卖 / 收藏"
            }
            val li = result.getJSONObject("inner")
            val offer = result.getLong("offer")
            active.put("phase", if (step == 0) "grind1" else "grind2")
                .put("local", li)
                .put("offer", offer)
                .put("method", "磨到一半卖出（第${step + 1}步）")
            val visible = if (step == 0) zhongName(li) else colorName(li)
            return "磨开第${step + 1}步……局部 $visible｜磨口报价¥${n(offer)}\n卖 / 磨"
        }
        if (command == "卖") return finish(save, stone, active, false)
        if (command == "收藏") return finish(save, stone, active, true)
        return "先把手里这块切完。"
    }
}
