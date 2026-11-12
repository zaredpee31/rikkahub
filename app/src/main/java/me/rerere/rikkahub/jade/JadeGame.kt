package me.rerere.rikkahub.jade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/*
 * 赌石存档和文字命令（v23）。
 * 存档和页面同一份：页面原本存在浏览器里，现在由 JadePage 转到这个文件；我用 run() 读写同一个文件。
 * 格式就是页面自己的格式，另外多几项只有我用：aiCut（我正在切哪块）、aiNpc（谁在跟我抢、劝我）、
 * claudeCur / claudeLast（我这趟和上一趟的账）、claudeSeen（上次看到第几条切石记录）、lastActionAt。
 * 页面不认这些字段，会原样带着存回来。
 * v23：整料一刀切切涨了多给 15%；珍藏柜可以送去拍卖。规则和页面里 aoi-v23 那段保持一致。
 */
object JadeGame {
    /** 宝宝正开着赌石页面时为 true，这时我不动存档 */
    @Volatile
    var pageOpen = false

    private val lock = Any()
    private const val LIMIT = 4 * 1024 * 1024
    private const val BROKEN = "赌石存档暂时无法读取，原数据没有修改。"
    const val PAGE_BUSY = "宝宝正在页面上玩，先不动，等她退出页面再来。"

    private val TIER_NAMES = JadeEngine.TIER_NAMES
    private val RIND_COLORS = listOf("黄沙皮", "黑乌沙", "白盐沙", "红蜡壳", "青皮", "灰皮", "褐皮", "铁锈皮")
    private val RIND_FINE = listOf("极细", "细腻", "中等", "粗糙", "极粗")
    private val RIND_CRACK = listOf("无裂", "少量细裂", "明显裂纹", "满布裂绺")
    private val RIND_SONG = listOf("无松花", "星点松花", "片状松花", "满布松花")
    private val RIND_MANG = listOf("无蟒带", "细蟒带", "明显蟒带", "缠绕蟒带")
    private val ZHONG_NAMES = listOf("豆种", "糯种", "糯冰种", "冰种", "高冰种", "玻璃种")
    private val COLOR_NAMES = listOf("无色", "淡绿", "晴水绿", "阳绿", "浓绿", "满绿", "帝王绿", "黄翡", "蓝水", "紫罗兰", "春带彩")
    private val TIER_WORDS = listOf("垮了", "小亏", "小赚", "大涨", "真爆！")

    private val UNCLE_LINES = listOf(
        "「小姑娘，听大爷一句劝——这石头皮糙得跟砂纸似的，里面能有啥？十赌九输，放下吧。」大爷摇着蒲扇，一脸看透世事的表情。",
        "「这块？我看就是块砖头料！切开怕是全是白碴子，连个色都见不着。」大爷说得斩钉截铁，唾沫星子都飞出来了。",
        "「年轻人不听老人言呐……这种料子我见得多了，没一个能涨的。」大爷叹着气，仿佛已经看见了结局。",
        "「就这皮壳？松花蟒带一样拿不出手，纯纯交学费的货。」大爷背着手，头摇得像拨浪鼓。",
    )
    private val SHILL_LINES = listOf(
        "「哎哟！这块可了不得，刚才还有两个围着看呢，你再犹豫就被别人抢走啦！」旁边一个人凑过来，热情得有点过头。",
        "「这皮壳、这松花，一看就是老坑的货！切开不涨，我跟你姓！」他拍着胸脯保证，眼睛却直往店主那边瞟。",
        "「妹子你眼光是真毒，这批货里就数这块最亮眼，拿下准没错！」他笑得比店主还要开心。",
        "「这种货色，平时一上架就没了，也就你运气好赶上。」他压低声音，一副只告诉你一个人的样子。",
    )
    private val BOSS_INTROS = listOf(
        "一个挺着啤酒肚的挤了进来，围着石头转了两圈，眼睛越来越亮。",
        "旁边一个戴金链子的放下茶杯，慢慢踱了过来，盯着石头舍不得挪眼。",
        "一个夹着皮包的本来都要出门了，瞥见这块石头又折了回来。",
    )

    private val HELP = "打开：看本金、天数、手里的石头，还有宝宝最近切了什么。\n" +
        "去 铺子名：看货架，例如“去 垃圾铺”。\n" +
        "刷新（也可以说进货）：付费给当前铺子换一批货。\n" +
        "打灯：看当前货架每块石头顶、腰、松花处（没有松花就是底下）的透光，照到和别处不一样的地方会提示。\n" +
        "买 1 到 买 8 / 买 镇店：买石头，可能碰到抢、大爷劝、托儿吹，按提示说“跟”“要”或“不要了”。\n" +
        "拿 号码：手里有好几块时选一块来切，号码看“打开”。\n" +
        "切 / 擦窗 顶上（腰上、松花处、底下）/ 磨：处理手里的石头，每块只能擦一次窗。\n" +
        "没擦过没磨过的整料直接切，切涨了多给 15%；擦窗和磨的报价按看到的种色估，好就明显高过买入价，差就狠压。\n" +
        "卖 / 收藏：卖掉，或者把切开的小赚以上的好料收进珍藏柜。\n" +
        "看珍藏柜 / 卖 柜里1 / 拍卖 柜里1：看，按估值卖，或者交估值 3% 的上拍费送去拍卖（可能拍过估值，可能低于估值，也可能流拍）。\n" +
        "切满 5 块进入下一天，各档铺子自动补货。\n" +
        "手里没石头、所有铺子都买不起时，可以说“领取800”。"

    private val BUY = Regex("买\\s*([0-9]+|镇店)")
    private val TAKE = Regex("拿\\s*([0-9]+)")
    private val SELL_CAB = Regex("卖\\s*柜里\\s*([0-9]+)")
    private val AUCTION_CAB = Regex("拍卖\\s*柜里\\s*([0-9]+)")
    private val POINT_NAMES = mapOf("顶" to "顶上", "腰" to "腰上", "松花" to "松花处", "底" to "底下")
    private val RANKS = mapOf("暗" to 0, "弱" to 1, "中" to 2, "强" to 3)
    private val REMARKS = mapOf(
        "强true" to "底下多半有色，种也不差", "强false" to "种水不错，偏白肉",
        "中true" to "种和色都有些盼头，说不准", "中false" to "多半是白肉，种可能还行",
        "弱true" to "可能有层薄色，也可能是松花晃眼", "弱false" to "没什么盼头，也说不准是皮太厚",
        "暗false" to "皮厚或里面干，也有好货闷着",
    )
    private val CHOICES = mapOf(
        "new" to "切 / 擦窗 / 磨", "window" to "卖 / 切 / 磨", "grind1" to "卖 / 磨",
        "grind2" to "卖 / 磨", "revealed" to "卖 / 收藏",
    )

    // ================= 存档文件 =================

    private fun dir(c: Context) = File(c.applicationContext.filesDir, "jade")

    private fun saveFile(c: Context) = File(dir(c), "save.json")

    private fun histLen(o: JSONObject?) = o?.optJSONArray("history")?.length() ?: -1

    private fun readFile(c: Context): JSONObject? {
        val f = saveFile(c)
        if (!f.exists()) return null
        if (f.length() > LIMIT) error("存档过大")
        return JSONObject(f.readText(Charsets.UTF_8))
    }

    // 先写临时文件再改名，写到一半断电也不会坏档
    private fun write(file: File, text: String) {
        val d = file.parentFile ?: error("存档目录不存在")
        d.mkdirs()
        val tmp = File(d, ".save-" + System.nanoTime())
        try {
            tmp.outputStream().use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            if (!tmp.renameTo(file)) error("存档写不进去")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    // 覆盖前把上一版留一份；切石记录变少了（多半是点了重置存档）再单独留一份
    private fun store(c: Context, save: JSONObject, oldHist: Int) {
        val f = saveFile(c)
        if (oldHist >= 0 && f.exists()) runCatching {
            if (histLen(save) < oldHist) f.copyTo(File(dir(c), "save.before-reset.json"), overwrite = true)
            f.copyTo(File(dir(c), "save.bak.json"), overwrite = true)
        }
        write(f, save.toString())
    }

    // ================= 页面用的接口 =================

    /** 页面读存档。还没有存档时返回空字符串，页面会自己开一局 */
    fun storageGet(context: Context): String = synchronized(lock) {
        try {
            val save = readFile(context) ?: return@synchronized ""
            if (migrate(save)) store(context, save, histLen(save))
            save.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /** 页面写存档：整份覆盖，写之前先备份 */
    fun storageSet(context: Context, body: String): Boolean = synchronized(lock) {
        try {
            if (body.length > LIMIT) return@synchronized false
            val new = JSONObject(body)
            if (new.opt("money") !is Number) return@synchronized false
            val oldHist = runCatching { histLen(readFile(context)) }.getOrDefault(-1)
            store(context, new, oldHist)
            true
        } catch (e: Exception) {
            false
        }
    }

    // 旧版页面用的接口，新页面不用了，留着只为了旧的 JadePage 还能编译
    fun pageGet(context: Context): String = "{\"status\":410,\"body\":\"{}\"}"

    fun pagePost(context: Context, body: String): String = pageGet(context)

    // ================= 存档内容 =================

    // 旧版（game.py 那套）的存档补齐成页面的格式，钱和石头都留着
    private fun migrate(s: JSONObject): Boolean {
        var changed = false
        fun need(k: String, v: Any) {
            if (s.opt(k) !is Number) {
                s.put(k, v)
                changed = true
            }
        }
        need("money", 10000)
        need("maxWealth", s.optLong("money", 10000))
        need("tier", 0)
        need("unlockedTier", 0)
        need("day", 1)
        need("cutsToday", 0)
        if (s.optJSONObject("shops") == null) {
            s.put("shops", JSONObject())
            changed = true
        }
        for (k in listOf("owned", "cabinet", "history")) if (s.optJSONArray(k) == null) {
            s.put(k, JSONArray())
            changed = true
        }
        s.optJSONObject("activeCut")?.let { a ->
            if (!s.has("aiCut")) s.put(
                "aiCut", JSONObject()
                    .put("stoneId", a.opt("stoneId") ?: "")
                    .put("phase", a.optString("phase", "new"))
                    .put("offer", a.optLong("offer"))
                    .put("method", a.optString("method", "一刀切"))
                    .put("grindFees", a.optLong("grindFees"))
            )
        }
        for (k in listOf("activeCut", "pendingNpc", "torchReadings", "xiaojieVisits", "xiaokeCurrent", "xiaokeLast", "shelfVersion")) {
            if (s.has(k)) {
                s.remove(k)
                changed = true
            }
        }
        return changed
    }

    private fun newSave(): JSONObject = JSONObject()
        .put("money", 10000).put("maxWealth", 10000).put("tier", 0).put("unlockedTier", 0)
        .put("day", 1).put("cutsToday", 0).put("shops", JSONObject())
        .put("owned", JSONArray()).put("cabinet", JSONArray()).put("history", JSONArray())

    private fun objs(arr: JSONArray): List<JSONObject> = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

    private fun without(arr: JSONArray, id: String): JSONArray =
        JSONArray().also { out -> objs(arr).filter { it.optString("id") != id }.forEach { out.put(it) } }

    private fun shelfOf(save: JSONObject, tier: Int): JSONArray {
        val shops = save.getJSONObject("shops")
        return shops.optJSONArray(tier.toString()) ?: JSONArray().also { shops.put(tier.toString(), it) }
    }

    private fun setShelf(save: JSONObject, tier: Int, arr: JSONArray) {
        save.getJSONObject("shops").put(tier.toString(), arr)
    }

    private fun ordered(shelf: JSONArray): Pair<List<JSONObject>, List<JSONObject>> {
        val list = objs(shelf)
        return list.filter { !it.optBoolean("zhen") }.sortedBy { it.optInt("shelfNo", 99) } to list.filter { it.optBoolean("zhen") }
    }

    // 已解锁的铺子没货架就补一批（和页面的 ensureShops 一样，空货架不补）
    private fun ensureShops(save: JSONObject) {
        val shops = save.getJSONObject("shops")
        for (t in 0..save.getInt("unlockedTier").coerceIn(0, 4)) {
            if (shops.optJSONArray(t.toString()) == null) shops.put(t.toString(), JadeEngine.genShop(t))
        }
    }

    // 页面进的货没有编号，我给普通石头按顺序编 1 到 8，页面不认这个字段
    private fun ensureShelfNumbers(save: JSONObject) {
        val shops = save.getJSONObject("shops")
        for (k in shops.keys()) {
            val arr = shops.optJSONArray(k) ?: continue
            val normal = objs(arr).filter { !it.optBoolean("zhen") }
            val used = mutableSetOf<Int>()
            for (s in normal) {
                val num = (s.opt("shelfNo") as? Number)?.toInt()
                if (num != null && num in 1..JadeEngine.SHELF_SIZE && num !in used) used.add(num) else s.remove("shelfNo")
            }
            for (s in normal) {
                if (s.has("shelfNo")) continue
                val num = (1..JadeEngine.SHELF_SIZE).firstOrNull { it !in used } ?: break
                s.put("shelfNo", num)
                used.add(num)
            }
        }
    }

    private fun money(s: JSONObject) = s.getLong("money")

    private fun addMoney(s: JSONObject, d: Long) {
        s.put("money", money(s) + d)
        if (money(s) > s.optLong("maxWealth")) s.put("maxWealth", money(s))
    }

    private fun dayStr(s: JSONObject) = "第 ${s.optInt("day", 1)} 天"

    private fun roundStats() = JSONObject().put("played", false).put("cuts", 0).put("spent", 0).put("net", 0)

    private fun cur(save: JSONObject): JSONObject =
        save.optJSONObject("claudeCur") ?: roundStats().also { save.put("claudeCur", it) }

    private fun spendStat(save: JSONObject, x: Long) {
        val c = cur(save)
        c.put("played", true).put("spent", c.optLong("spent") + x).put("net", c.optLong("net") - x)
    }

    private fun earnStat(save: JSONObject, x: Long, cut: Boolean) {
        val c = cur(save)
        c.put("played", true).put("net", c.optLong("net") + x)
        if (cut) c.put("cuts", c.optLong("cuts") + 1)
    }

    // ================= 文字 =================

    private fun n(x: Long) = String.format(Locale.US, "%,d", x)

    private fun signed(x: Long) = String.format(Locale.US, "%+,d", x)

    private fun kg(st: JSONObject) = String.format(Locale.US, "%.1f", st.optDouble("weight"))

    private fun <T> choose(list: List<T>): T = list[Random.nextInt(list.size)]

    private fun zhongName(inner: JSONObject) = ZHONG_NAMES.getOrElse(inner.optInt("zhong")) { "?" }

    private fun colorName(inner: JSONObject) = COLOR_NAMES.getOrElse(inner.optInt("color")) { "?" }

    private fun flaws(inner: JSONObject): String {
        val crack = listOf("无裂", "有细裂", "大裂贯穿").getOrElse(inner.optInt("crack")) { "?" }
        val cotton = listOf("无棉", "少棉", "重棉").getOrElse(inner.optInt("cotton")) { "?" }
        val spot = if (inner.optInt("spot") != 0) " 有黑点" else ""
        return "$crack $cotton$spot"
    }

    private fun rindAttrs(st: JSONObject): String {
        val r = st.optJSONObject("rind") ?: return ""
        return listOf(
            RIND_COLORS.getOrElse(r.optInt("color")) { "?" }, "皮" + RIND_FINE.getOrElse(r.optInt("fine")) { "?" },
            RIND_CRACK.getOrElse(r.optInt("crack")) { "?" }, RIND_SONG.getOrElse(r.optInt("song")) { "?" },
            RIND_MANG.getOrElse(r.optInt("mang")) { "?" },
        ).joinToString(" ")
    }

    private fun detail(st: JSONObject) =
        "${st.optString("name")} ${kg(st)}kg ¥${n(st.getLong("price"))} 皮壳${st.optInt("S")}｜${rindAttrs(st)}"

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

    // ================= 命令 =================

    fun run(context: Context, command: String): String = synchronized(lock) {
        if (pageOpen) {
            PAGE_BUSY
        } else {
            try {
                runLocked(context, command)
            } catch (e: Exception) {
                BROKEN
            }
        }
    }

    private fun runLocked(c: Context, raw: String): String {
        var cmd = raw.trim().replace(Regex("\\s+"), " ")
        if (cmd == "一刀切") cmd = "切"
        if (cmd == "进货") cmd = "刷新"
        if (cmd == "帮助") return HELP

        val existing = readFile(c)
        val oldHist = histLen(existing)
        val save = existing ?: newSave()
        migrate(save)
        ensureShops(save)
        ensureShelfNumbers(save)
        val now = System.currentTimeMillis() / 1000
        val lastAt = save.optLong("lastActionAt")

        var result: String? = null
        save.optJSONObject("aiNpc")?.let { p ->
            val t = p.optInt("tier", -1)
            val valid = t == save.getInt("tier") &&
                objs(shelfOf(save, t)).any { it.optString("id") == p.optString("stoneId") }
            if (!valid) {
                save.remove("aiNpc")
            } else {
                result = if (cmd == "打开") openText(save, lastAt, now) + "\n⏳ " + npcReminder(p) else pending(save, p, cmd)
            }
        }
        val text = result ?: dispatch(save, cmd, lastAt, now)
        save.put("lastActionAt", now)
        store(c, save, oldHist)
        return text
    }

    private fun dispatch(save: JSONObject, cmd: String, lastAt: Long, now: Long): String {
        AUCTION_CAB.matchEntire(cmd)?.let { return auctionCabinet(save, it.groupValues[1].toIntOrNull() ?: 0) }
        SELL_CAB.matchEntire(cmd)?.let { return sellCabinet(save, it.groupValues[1].toIntOrNull() ?: 0) }
        BUY.matchEntire(cmd)?.let { return buy(save, it.groupValues[1]) }
        TAKE.matchEntire(cmd)?.let { return take(save, it.groupValues[1].toIntOrNull() ?: 0) }
        return when {
            cmd == "打开" -> openText(save, lastAt, now)
            cmd == "看珍藏柜" -> cabinetText(save)
            cmd.startsWith("去") && cmd.substring(1).isNotBlank() -> go(save, cmd.substring(1).trim())
            cmd == "刷新" -> restock(save)
            cmd == "打灯" -> torchText(save)
            cmd == "领取800" -> relief(save)
            cmd in setOf("切", "磨", "卖", "收藏") || cmd.startsWith("擦窗") -> {
                val t = target(save) ?: return noTarget(save)
                cut(save, t.first, t.second, cmd)
            }
            cmd in setOf("跟", "要", "不要了") -> "现在没人跟你抢，也没人劝。"
            else -> "请说“帮助”查看可用动作。"
        }
    }

    // 切开之后这块按多少钱算：整料一刀切有溢价时记在 aiCut.offer 里，没有就是石头自己的 value
    private fun revealedGot(st: JSONObject, a: JSONObject): Long {
        val o = a.optLong("offer")
        return if (o > 0) o else st.getLong("value")
    }

    private fun openText(save: JSONObject, lastAt: Long, now: Long): String {
        if (now - lastAt > 3600) {
            save.put("claudeLast", cur(save))
            save.put("claudeCur", roundStats())
        }
        val lines = mutableListOf(
            "💰 本金 ¥${n(money(save))}｜历史最高 ¥${n(save.optLong("maxWealth"))}｜${dayStr(save)} · 已切 ${save.optInt("cutsToday")}/5",
        )
        val last = save.optJSONObject("claudeLast")
        lines.add(
            "🪨 我上一趟：" + if (last == null || !last.optBoolean("played")) "还没玩过" else {
                val net = last.optLong("net")
                "切了${last.optLong("cuts")}块，花¥${n(last.optLong("spent"))}，" + (if (net >= 0) "净赚" else "净亏") + "¥" + n(abs(net))
            }
        )

        // 宝宝自上次我来过之后切的
        val hist = objs(save.getJSONArray("history"))
        val seen = if (save.has("claudeSeen")) save.optInt("claudeSeen").coerceIn(0, hist.size) else hist.size
        val hers = hist.drop(seen).filter { it.optString("by") != "claude" && !it.optBoolean("watched") }
        save.put("claudeSeen", hist.size)
        lines.add(
            "👀 宝宝来过：" + if (hers.isEmpty()) "这阵子没来切" else {
                val kept = hers.count { it.optBoolean("kept") }
                val sold = hers.filter { !it.optBoolean("kept") }
                val net = sold.sumOf { it.optLong("got") - it.optLong("price") }
                "切了${hers.size}块" +
                    (if (sold.isNotEmpty()) "，卖掉的" + (if (net >= 0) "净赚" else "净亏") + "¥" + n(abs(net)) else "") +
                    (if (kept > 0) "，收了${kept}件进柜" else "")
            }
        )

        val names = TIER_NAMES.take(save.getInt("unlockedTier") + 1).joinToString("、")
        lines.add("🏪 能去：$names｜现在在${TIER_NAMES.getOrElse(save.getInt("tier")) { "?" }}")

        val owned = objs(save.getJSONArray("owned"))
        val a = save.optJSONObject("aiCut")
        val aiId = a?.optString("stoneId")
        if (owned.isEmpty()) {
            lines.add("✋ 手里没有石头")
        } else {
            lines.add("✋ 手里 ${owned.size} 块：")
            owned.forEachIndexed { i, st ->
                val tags = mutableListOf<String>()
                if (st.optString("boughtBy") != "claude") tags.add("宝宝买的")
                if (st.optBoolean("windowed")) tags.add("已擦窗")
                if (st.optInt("grindStep") > 0) tags.add("磨到第${st.optInt("grindStep")}步")
                if (st.optString("id") == aiId && a != null) {
                    when (a.optString("phase")) {
                        "revealed" -> tags.add("切开了，估价¥${n(revealedGot(st, a))}，等卖或收藏")
                        "window", "grind1", "grind2" -> tags.add("报价¥${n(a.optLong("offer"))}")
                    }
                    tags.add("我正在切")
                }
                lines.add("   ${i + 1}. ${detail(st)}" + if (tags.isNotEmpty()) "（${tags.joinToString("，")}）" else "")
            }
        }
        lines.add("🎒 珍藏柜 ${save.getJSONArray("cabinet").length()} 件")
        return lines.joinToString("\n")
    }

    private fun shelfText(save: JSONObject, name: String): String {
        val tier = TIER_NAMES.indexOf(name)
        if (tier < 0) return "没有这家铺子。"
        if (tier > save.getInt("unlockedTier")) return "${name}还没解锁。"
        val fee = JadeEngine.RESTOCK_FEE[tier]
        val (normal, crown) = ordered(shelfOf(save, tier))
        if (normal.isEmpty() && crown.isEmpty()) return "${name}货架空了，切满 5 块进下一天会补货，也可以「刷新」¥${n(fee)}"
        val lines = mutableListOf("$name｜刷新¥${n(fee)}｜本金¥${n(money(save))}")
        normal.forEach { lines.add("${it.opt("shelfNo") ?: "·"}. ${detail(it)}" + if (it.optBoolean("windowed")) "｜已擦窗" else "") }
        crown.forEach { lines.add("👑 镇店石 ${detail(it)}") }
        return lines.joinToString("\n")
    }

    private fun go(save: JSONObject, name: String): String {
        val result = shelfText(save, name)
        val idx = TIER_NAMES.indexOf(name)
        if (idx >= 0 && idx <= save.getInt("unlockedTier")) save.put("tier", idx)
        return result
    }

    private fun restock(save: JSONObject): String {
        val tier = save.getInt("tier")
        val fee = JadeEngine.RESTOCK_FEE[tier]
        if (money(save) < fee) return "本金不够，刷新要¥${n(fee)}"
        addMoney(save, -fee)
        spendStat(save, fee)
        setShelf(save, tier, JadeEngine.genShop(tier))
        return "连夜进了一批新货\n" + shelfText(save, TIER_NAMES[tier])
    }

    private fun torchText(save: JSONObject): String {
        val tier = save.getInt("tier")
        val (normal, crown) = ordered(shelfOf(save, tier))
        if (normal.isEmpty() && crown.isEmpty()) return "货架空了，「刷新」要¥${n(JadeEngine.RESTOCK_FEE[tier])}"
        fun line(label: String, st: JSONObject): String {
            val pts = objs(JadeEngine.sampleLight(st))
            fun bright(p: JSONObject) = p.optString("level") != "暗" && p.optBoolean("colored")
            val best = pts.maxBy { (RANKS[it.optString("level")] ?: 0) * 2 + if (bright(it)) 1 else 0 }
            val remark = REMARKS[best.optString("level") + bright(best)].orEmpty()
            return label + " " + pts.joinToString(" ") { showPoint(it) } + "｜" + remark + hitSuffix(pts)
        }
        val lines = normal.map { line("${it.opt("shelfNo") ?: "·"}号", it) } + crown.map { line("👑", it) }
        return lines.joinToString("\n")
    }

    private fun revealedPending(save: JSONObject): Boolean {
        val a = save.optJSONObject("aiCut") ?: return false
        return a.optString("phase") == "revealed" &&
            objs(save.getJSONArray("owned")).any { it.optString("id") == a.optString("stoneId") }
    }

    private fun raiseInc(price: Long): Long =
        max(10L, (JadeEngine.jsRound(price * (0.10 + Random.nextDouble() * 0.10) / 10) * 10).toLong())

    private fun buy(save: JSONObject, sel: String): String {
        if (revealedPending(save)) return "先把切开的那块卖了或收藏，再买新的。"
        val tier = save.getInt("tier")
        val (normal, crown) = ordered(shelfOf(save, tier))
        val stone: JSONObject?
        val label: String
        if (sel == "镇店") {
            stone = crown.firstOrNull()
            label = "镇店石"
            if (stone == null) return "这档的镇店石已经没了。"
        } else {
            val num = sel.toIntOrNull() ?: 0
            stone = normal.firstOrNull { it.optInt("shelfNo") == num }
            label = "${num}号"
            if (stone == null) return "${num}号已经没了。"
        }
        val price = stone.getLong("price")
        if (money(save) < price) return "本金不够，要¥${n(price)}"
        // 和页面一样：没人来 70% / 抢石头的15% / 唱衰的大爷 10% / 吹捧的托儿 5%，和石头好坏无关
        val roll = Random.nextDouble()
        if (roll < 0.70) return purchase(save, stone, label, price)
        val p = JSONObject().put("stoneId", stone.getString("id")).put("tier", tier).put("label", label)
        if (roll < 0.85) {
            val r = Random.nextDouble()
            val inc = raiseInc(price)
            p.put("kind", "boss").put("total", if (r < 0.50) 1 else if (r < 0.85) 2 else 3).put("done", 1).put("price", price + inc)
            save.put("aiNpc", p)
            return "🧔${choose(BOSS_INTROS)}他直接加价 +¥${n(inc)}：「这块我出 ¥${n(price + inc)}，我要了！」跟 / 不要了"
        }
        val uncle = roll < 0.95
        p.put("kind", if (uncle) "uncle" else "shill").put("price", price)
        save.put("aiNpc", p)
        return (if (uncle) "👴" else "🗣️") + choose(if (uncle) UNCLE_LINES else SHILL_LINES) + " 还要吗？（要 / 不要了）"
    }

    private fun npcReminder(p: JSONObject): String =
        if (p.optString("kind") == "boss") "还在跟我抢${p.optString("label")}，现在出价¥${n(p.optLong("price"))}：跟 / 不要了"
        else "${p.optString("label")}还等我拿主意：要 / 不要了"

    private fun pending(save: JSONObject, p: JSONObject, cmd: String): String {
        val boss = p.optString("kind") == "boss"
        val ask = if (boss) "请先回答：跟 / 不要了。" else "请先回答：要 / 不要了。"
        if (cmd !in setOf("跟", "要", "不要了")) return ask
        val tier = p.getInt("tier")
        val shelf = shelfOf(save, tier)
        val stone = objs(shelf).first { it.optString("id") == p.optString("stoneId") }
        val label = p.optString("label")
        val price = p.getLong("price")
        if (cmd == "不要了") {
            save.remove("aiNpc")
            if (!boss) return "好，不买了。"
            // 石头归当场切开，我只围观
            val id = stone.getString("id")
            setShelf(save, tier, without(shelf, id))
            val inner = stone.getJSONObject("inner")
            val value = stone.getLong("value")
            val t = JadeEngine.outcomeOf(value.toDouble() / stone.getLong("price"), inner)
            save.getJSONArray("history").put(
                JSONObject().put("id", id).put("name", stone.optString("name")).put("date", dayStr(save))
                    .put("price", price).put("got", value).put("tier", t).put("inner", inner)
                    .put("method", "围观切石").put("kept", false).put("watched", true).put("by", "claude")
            )
            val diff = value - price
            return "🧔掏了¥${n(price)}把${label}抱走，当场一刀……${zhongName(inner)} ${colorName(inner)}｜${flaws(inner)}｜" +
                "" + (if (diff >= 0) "赚了" else "亏了") + "¥${n(abs(diff))}"
        }
        if (boss && cmd == "跟") {
            if (money(save) < price) return "本金不够，要¥${n(price)}"
            if (p.getInt("done") >= p.getInt("total")) {
                save.remove("aiNpc")
                return "🧔一摆手：「行行行，归你了！年轻人，有魄力。」\n" + purchase(save, stone, label, price)
            }
            val inc = raiseInc(price)
            p.put("done", p.getInt("done") + 1).put("price", price + inc)
            return "🧔哼了一声，又加 +¥${n(inc)}：「我出 ¥${n(price + inc)}！看你能跟到几时！」跟 / 不要了"
        }
        if (!boss && cmd == "要") {
            save.remove("aiNpc")
            return purchase(save, stone, label, price)
        }
        return ask
    }

    private fun purchase(save: JSONObject, stone: JSONObject, label: String, price: Long): String {
        if (money(save) < price) return "本金不够，要¥${n(price)}"
        addMoney(save, -price)
        spendStat(save, price)
        val tier = save.getInt("tier")
        val id = stone.getString("id")
        setShelf(save, tier, without(shelfOf(save, tier), id))
        stone.put("price", price)
        stone.remove("shelfNo")
        stone.put("boughtBy", "claude")
        val owned = save.getJSONArray("owned")
        owned.put(stone)
        save.put("aiCut", JSONObject().put("stoneId", id).put("phase", "new").put("grindFees", 0))
        val third = if (stone.getJSONObject("rind").optInt("song") != 0) "松花处" else "底下"
        return "买下 $label ${stone.optString("name")} ¥${n(price)}｜本金 ${n(money(save))}｜手里 ${owned.length()} 块\n" +
            "怎么切？切 / 擦窗 顶上·腰上·$third / 磨，也可以先留着给宝宝切"
    }

    // 拿起手里一块：按石头上留下的进度接着切，宝宝在页面上擦过窗、磨过的也认
    private fun takeStone(save: JSONObject, st: JSONObject): JSONObject {
        val step = st.optInt("grindStep")
        val a = JSONObject().put("stoneId", st.getString("id")).put("grindFees", 0)
        when {
            step >= 2 -> a.put("phase", "grind2").put("offer", JadeEngine.grindResult(st, 1).getLong("offer"))
                .put("method", "磨到一半卖出（第2步）")
            step == 1 -> a.put("phase", "grind1").put("offer", JadeEngine.grindResult(st, 0).getLong("offer"))
                .put("method", "磨到一半卖出（第1步）")
            st.optBoolean("windowed") -> {
                val saved = st.optLong("_winOffer")
                a.put("phase", "window").put("offer", if (saved > 0) saved else JadeEngine.grindResult(st, 1).getLong("offer"))
                    .put("method", "擦窗后窗口卖出")
            }
            else -> a.put("phase", "new")
        }
        save.put("aiCut", a)
        return a
    }

    private fun target(save: JSONObject): Pair<JSONObject, JSONObject>? {
        val owned = objs(save.getJSONArray("owned"))
        save.optJSONObject("aiCut")?.let { a ->
            val st = owned.firstOrNull { it.optString("id") == a.optString("stoneId") }
            if (st != null) return st to a
            save.remove("aiCut")
        }
        if (owned.size == 1) return owned[0] to takeStone(save, owned[0])
        return null
    }

    private fun noTarget(save: JSONObject): String {
        val k = save.getJSONArray("owned").length()
        return if (k == 0) "手里没有正在切的石头。" else "手里有 $k 块，不能直接切，先说「拿 1」到「拿 $k」选一块。"
    }

    private fun take(save: JSONObject, num: Int): String {
        val owned = objs(save.getJSONArray("owned"))
        if (num !in 1..owned.size) return "手里没有第 $num 块。"
        val st = owned[num - 1]
        val a = save.optJSONObject("aiCut")
        if (a != null && a.optString("stoneId") == st.optString("id")) {
            return "已经拿着${num}号了。接下来：" + (CHOICES[a.optString("phase")] ?: "")
        }
        if (revealedPending(save)) return "先把切开的那块卖了或收藏，再拿别的。"
        val na = takeStone(save, st)
        val phase = na.optString("phase")
        val offer = if (phase == "new") "" else "｜报价¥${n(na.optLong("offer"))}"
        return "拿起${num}号 ${detail(st)}$offer\n接下来：" + (CHOICES[phase] ?: "")
    }

    private fun cutTier(st: JSONObject, a: JSONObject): Int =
        if (a.has("tier")) a.optInt("tier")
        else JadeEngine.outcomeOf(revealedGot(st, a).toDouble() / st.getLong("price"), st.getJSONObject("inner"))

    private fun cut(save: JSONObject, st: JSONObject, a: JSONObject, cmd: String): String {
        val phase = a.optString("phase", "new")
        val price = st.getLong("price")
        when {
            cmd == "切" -> {
                if (phase == "revealed") return "这块已经切开了：卖 / 收藏"
                if (phase != "new" && phase != "window") return "已经磨开了，只能接着磨或者卖：卖 / 磨"
                return reveal(save, st, a, if (st.optBoolean("windowed")) "擦窗后一刀切" else "一刀切", "一刀下去……")
            }

            cmd.startsWith("擦窗") -> {
                if (st.optBoolean("windowed")) return "这块已经擦过窗了，不能再擦。"
                if (phase != "new") return "现在不能擦窗。"
                val hasSong = st.getJSONObject("rind").optInt("song") != 0
                val third = if (hasSong) "松花处" else "底下"
                val positions = mapOf("顶上" to "顶", "腰上" to "腰", third to if (hasSong) "松花" else "底")
                val pos = cmd.removePrefix("擦窗").trim()
                val point = positions[pos] ?: return "这块能擦：顶上、腰上、$third，不能擦「$pos」。"
                val r = JadeEngine.windowResult(st, point)
                val offer = r.getLong("offer")
                val li = r.getJSONObject("inner")
                st.put("winDir", r.getJSONObject("dir")).put("windowed", true).put("_winOffer", offer)
                a.put("phase", "window").put("offer", offer).put("method", "擦窗后窗口卖出")
                val songNote = if (point == "松花") "这一窗正擦在松花上，" else ""
                return "擦开$pos……${songNote}窗口这一小块是 ${zhongName(li)} ${colorName(li)}，只代表这一小块｜有人按窗口出价¥${n(offer)}（买入¥${n(price)}）\n卖 / 切 / 磨"
            }

            cmd == "磨" -> {
                if (phase == "revealed") return "这块已经切开了：卖 / 收藏"
                val step = when (phase) {
                    "new" -> 0
                    "window", "grind1" -> 1
                    else -> 2
                }
                val fee = JadeEngine.grindFee(price)
                if (money(save) < fee) return "本金不够，磨工费要¥${n(fee)}"
                addMoney(save, -fee)
                spendStat(save, fee)
                a.put("grindFees", a.optLong("grindFees") + fee)
                st.put("grindStep", max(st.optInt("grindStep"), step + 1))
                if (step == 2) return reveal(save, st, a, "慢慢磨", "磨透了（磨工费¥${n(fee)}）……")
                val r = JadeEngine.grindResult(st, step)
                val li = r.getJSONObject("inner")
                val offer = r.getLong("offer")
                a.put("phase", if (step == 0) "grind1" else "grind2").put("offer", offer)
                    .put("method", "磨到一半卖出（第${step + 1}步）")
                val seen = if (step == 0) "种水是${zhongName(li)}" else "颜色是${colorName(li)}"
                return "磨开第${step + 1}步（磨工费¥${n(fee)}）……磨口这一处$seen，只代表这一处｜有人出价¥${n(offer)}（买入¥${n(price)}）\n卖 / 磨"
            }

            cmd == "卖" -> return when (phase) {
                "window", "grind1", "grind2" -> settle(save, st, a.optLong("offer"), a.optString("method", "擦窗后窗口卖出"))
                "revealed" -> settle(save, st, revealedGot(st, a), a.optString("method", "一刀切"))
                else -> "还没切开，不能卖：先切、擦窗或者磨。"
            }

            cmd == "收藏" -> return if (phase == "revealed" && cutTier(st, a) >= 2) collect(save, st, a)
            else "这块不能收藏：只有一刀切开或磨到底、小赚以上的才能进珍藏柜。"
        }
        return "先把手里这块切完。"
    }

    // 全揭晓：小赚以上先等我决定卖还是收藏，垮和小亏直接按估价卖掉（和页面一样）。
    // v23：没擦过没磨过的整料直接一刀切，切涨了多给 15%
    private fun reveal(save: JSONObject, st: JSONObject, a: JSONObject, method: String, prefix: String): String {
        val price = st.getLong("price")
        val value = st.getLong("value")
        val inner = st.getJSONObject("inner")
        val whole = method == "一刀切" && !st.optBoolean("windowed") && st.optInt("grindStep") == 0
        val bonus = whole && value > price
        val got = if (bonus) JadeEngine.jsRound(value * 1.15).toLong() else value
        val finalMethod = if (bonus) "一刀切（整料 +15%）" else method
        val tier = JadeEngine.outcomeOf(got.toDouble() / price, inner)
        val diff = got - price - a.optLong("grindFees")
        val head = "$prefix${zhongName(inner)} ${colorName(inner)}｜${flaws(inner)}｜${TIER_WORDS[tier]}，估价¥${n(got)}（${signed(diff)}）" +
            if (bonus) "｜整料一刀切，多给了 15%" else ""
        if (tier >= 2) {
            a.put("phase", "revealed").put("offer", got).put("method", finalMethod).put("tier", tier)
            return "$head\n是好东西：卖 / 收藏"
        }
        return head + "\n" + settle(save, st, got, finalMethod)
    }

    private fun settle(save: JSONObject, st: JSONObject, got: Long, method: String): String {
        val price = st.getLong("price")
        val inner = st.getJSONObject("inner")
        val id = st.optString("id")
        val tier = JadeEngine.outcomeOf(got.toDouble() / price, inner)
        addMoney(save, got)
        save.put("owned", without(save.getJSONArray("owned"), id))
        save.remove("aiCut")
        save.getJSONArray("history").put(
            JSONObject().put("id", id).put("name", st.optString("name")).put("date", dayStr(save))
                .put("price", price).put("got", got).put("tier", tier).put("inner", inner)
                .put("method", method).put("kept", false).put("by", "claude")
        )
        earnStat(save, got, cut = true)
        val bk = bookkeeping(save)
        return "卖了¥${n(got)}（买入¥${n(price)}）｜本金 ${n(money(save))}$bk"
    }

    private fun collect(save: JSONObject, st: JSONObject, a: JSONObject): String {
        val method = a.optString("method", "一刀切")
        val id = st.optString("id")
        val got = revealedGot(st, a)
        val tier = cutTier(st, a)
        save.put("owned", without(save.getJSONArray("owned"), id))
        save.remove("aiCut")
        val item = JSONObject(st.toString())
        item.remove("_winOffer")
        item.put("value", got)
        item.put("keptDay", dayStr(save))
        item.put("keptMethod", method)
        val cabinet = save.getJSONArray("cabinet")
        cabinet.put(item)
        save.getJSONArray("history").put(
            JSONObject().put("id", id).put("name", st.optString("name")).put("date", dayStr(save))
                .put("price", st.getLong("price")).put("got", 0).put("tier", tier).put("inner", st.getJSONObject("inner"))
                .put("method", method).put("kept", true).put("by", "claude")
        )
        earnStat(save, 0, cut = true)
        val bk = bookkeeping(save)
        return "收进珍藏柜｜估值¥${n(got)}｜柜里现在 ${cabinet.length()} 件$bk"
    }

    // 每真正切开一块记一笔，满 5 块进入下一天，所有铺子补货
    private fun bookkeeping(save: JSONObject): String {
        save.put("cutsToday", save.optInt("cutsToday") + 1)
        var restocked = false
        if (save.optInt("cutsToday") >= 5) {
            save.put("cutsToday", 0)
            save.put("day", save.optInt("day", 1) + 1)
            save.put("shops", JSONObject())
            restocked = true
        }
        val unlocked = checkUnlock(save)
        ensureShops(save)
        ensureShelfNumbers(save)
        val sb = StringBuilder()
        if (restocked) sb.append("\n切满 5 块，进入第 ${save.optInt("day")} 天，各档铺子都补了新货")
        if (unlocked) sb.append("\n攒够钱了，新铺子「${TIER_NAMES[save.getInt("unlockedTier")]}」开门了")
        return sb.toString()
    }

    // 小店看历史最高身家，其余看手头的钱
    private fun checkUnlock(save: JSONObject): Boolean {
        val before = save.getInt("unlockedTier")
        while (save.getInt("unlockedTier") < 4) {
            val nt = save.getInt("unlockedTier") + 1
            val have = if (nt == 1) save.optLong("maxWealth") else money(save)
            if (have < JadeEngine.UNLOCK[nt]) break
            save.put("unlockedTier", nt)
        }
        return save.getInt("unlockedTier") > before
    }

    private fun auctionFee(value: Long): Long = max(20L, (JadeEngine.jsRound(value * 0.03 / 10) * 10).toLong())

    private fun cabinetText(save: JSONObject): String {
        val items = objs(save.getJSONArray("cabinet"))
        if (items.isEmpty()) return "珍藏柜是空的。"
        return items.mapIndexed { i, item ->
            val li = item.getJSONObject("inner")
            val value = item.getLong("value")
            "${i + 1}. ${item.optString("name")}｜${zhongName(li)} ${colorName(li)}｜${flaws(li)}｜估值¥${n(value)}" +
                "（买入¥${n(item.getLong("price"))}，${item.optString("keptDay")}收的，上拍费¥${n(auctionFee(value))}）"
        }.joinToString("\n")
    }

    private fun sellCabinet(save: JSONObject, index: Int): String {
        val cab = save.getJSONArray("cabinet")
        if (index < 1 || index > cab.length()) return "珍藏柜里没有这一件。"
        val item = cab.remove(index - 1) as JSONObject
        val value = item.getLong("value")
        addMoney(save, value)
        objs(save.getJSONArray("history")).firstOrNull { it.optString("id") == item.optString("id") && it.optBoolean("kept") }?.let {
            it.put("got", value).put("kept", false)
        }
        earnStat(save, value, cut = false)
        val unlocked = checkUnlock(save)
        ensureShops(save)
        ensureShelfNumbers(save)
        return "卖出柜里第${index}件「${item.optString("name")}」¥${n(value)}｜本金 ${n(money(save))}" +
            if (unlocked) "\n攒够钱了，新铺子「${TIER_NAMES[save.getInt("unlockedTier")]}」开门了" else ""
    }

    // 拍卖：和页面同一套概率。15% 流拍 / 35% 低于估值 / 37% 略高 / 10% 高出一截 / 3% 抢疯了
    private fun auctionCabinet(save: JSONObject, index: Int): String {
        val cab = save.getJSONArray("cabinet")
        if (index < 1 || index > cab.length()) return "珍藏柜里没有这一件。"
        val item = cab.getJSONObject(index - 1)
        val value = item.getLong("value")
        val name = item.optString("name")
        val fee = auctionFee(value)
        if (money(save) < fee) return "本金不够，上拍费要¥${n(fee)}"
        addMoney(save, -fee)
        spendStat(save, fee)
        fun u(a: Double, b: Double) = a + Random.nextDouble() * (b - a)
        val r = Random.nextDouble()
        val mult = when {
            r < 0.15 -> 0.0
            r < 0.50 -> u(0.7, 1.0)
            r < 0.87 -> u(1.0, 1.3)
            r < 0.97 -> u(1.3, 1.8)
            else -> u(1.8, 2.4)
        }
        val start = max(10L, (JadeEngine.jsRound(value * 0.6 / 10) * 10).toLong())
        val head = "「$name」上拍（上拍费¥${n(fee)}），起拍¥${n(start)}……"
        if (mult == 0.0) {
            return head + "喊了三遍没人举牌，流拍了。石头回了柜里，上拍费不退｜本金 ${n(money(save))}"
        }
        val hammer = max(start, (JadeEngine.jsRound(value * mult / 10) * 10).toLong())
        cab.remove(index - 1)
        addMoney(save, hammer)
        objs(save.getJSONArray("history")).firstOrNull { it.optString("id") == item.optString("id") && it.optBoolean("kept") }?.let {
            it.put("got", hammer).put("kept", false).put("method", it.optString("method") + " · 拍卖")
        }
        earnStat(save, hammer, cut = false)
        val unlocked = checkUnlock(save)
        ensureShops(save)
        ensureShelfNumbers(save)
        val diff = hammer - value
        return head + "¥${n(hammer)} 落槌，" +
            (if (diff >= 0) "比估值多拍了¥${n(diff)}" else "比估值少了¥${n(-diff)}，场子有点冷") +
            "｜本金 ${n(money(save))}" +
            if (unlocked) "\n攒够钱了，新铺子「${TIER_NAMES[save.getInt("unlockedTier")]}」开门了" else ""
    }

    private fun relief(save: JSONObject): String {
        val owned = save.getJSONArray("owned").length()
        val prices = (0..save.getInt("unlockedTier")).flatMap { t -> objs(shelfOf(save, t)).map { it.getLong("price") } }
        val m = money(save)
        val broke = owned == 0 && (if (prices.isEmpty()) m < 200 else m < prices.min())
        if (!broke) return "还没有达到领取条件：手里还有石头，或者钱还买得起货架上的石头。"
        addMoney(save, 800)
        return "铺子赊了800｜本金 ${n(money(save))}"
    }
}
