package me.rerere.rikkahub.pet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random

data class PetItem(
    val id: String,
    val name: String,
    val icon: String,
    val price: Int,
    val hunger: Int = 0,
    val mood: Int = 0,
    val energy: Int = 0,
    val health: Int = 0,
    val medicine: Boolean = false,
)

data class PetActivity(
    val id: String,
    val zone: String,
    val place: String,
    val name: String,
    val desc: String,
    val minutes: Int,
    val energy: Int,
    val hunger: Int,
    val clean: Int,
    val cost: Int = 0,
    val shells: Int = 0,
    val int: Int = 0,
    val eq: Int = 0,
    val str: Int = 0,
    val mood: Int = 0,
    val exp: Int = 0,
    val lv: Int = 1,
    val needInt: Int = 0,
    val needEq: Int = 0,
    val needStr: Int = 0,
    val night: Boolean = false,
    val diary: List<String> = emptyList(),
)

/**
 * 崽崽的存档和规则，页面和聊天工具共用这一份。
 * 存档在 filesDir/pet/save.json，所有读写都经过这里。
 */
object PetGame {
    const val MOM = "mom"
    const val CLAUDE = "claude"
    const val PET = "pet"
    private const val SICK = 35.0 // 只用于老存档迁移：没有 sick 字段时，健康低于它就算生病

    // 饱食/清洁/心情随时间下降的速度倍率，1.0 就是原速
    private const val DECAY = 1.0

    // 饱食和清洁同时为 0 持续超过这么久才会生病；生病时健康压到 SICK_START_HEALTH
    private const val SICK_AFTER_MS = 24 * 3_600_000L
    private const val SICK_START_HEALTH = 30.0

    // 生病期间饱食 >= 这个值才会自己恢复；健康到 CURE_HEALTH 病好
    private const val HEAL_MIN_HUNGER = 30.0
    private const val HEAL_PER_HOUR = 2.0
    private const val HEAL_SLEEP_PER_HOUR = 4.0
    private const val CURE_HEALTH = 60.0
    private const val SICK_COMFORT = 3.0 // 生病时抱抱、热牛奶额外健康

    // 每日零花钱：原掉速下一天（24 小时 × 每小时 4 点 = 96 点）饱食要 6 条小鱼干 72 贝壳，加 10 取整到 5
    private const val ALLOWANCE = 80

    // 上学：周一到周五，7:00–9:30 可以送，送出后 19:00 放学；19:00–22:00 可以接
    private const val SCHOOL_ID = "school_day"
    private const val SCHOOL_ZONE = "schoolday"
    private const val SCHOOL_OPEN_MIN = 7 * 60
    private const val SCHOOL_LATE_MIN = 9 * 60 + 30
    private const val SCHOOL_END_HOUR = 19
    private const val PICKUP_CLOSE_MIN = 22 * 60
    private const val SCHOOL_SHELLS = 15
    private const val SCHOOL_INT = 2
    private const val SCHOOL_EXP = 8
    private const val PICKUP_MOOD = 10.0
    private const val DEFAULT_GRADE = "一年级"

    private val schoolStories = listOf(
        "今天学会了数到十",
        "午饭有虾仁，吃了两碗",
        "同桌借了它一块橡皮",
        "课间跟小海龟玩了跳房子",
        "老师夸它的字写得很整齐",
        "上美术课把手指涂成了蓝色",
        "体育课横着跑了第一名",
        "在操场捡到一片很漂亮的贝壳",
        "午休的时候差点睡着，被同桌戳醒了",
        "今天轮到它擦黑板，擦得很干净",
    )

    // 每小时回多少体力：睡着 40（原来是 12，睡半小时只回 6 点，等于白睡），醒着 3
    private const val SLEEP_ENERGY = 40.0
    private const val AWAKE_ENERGY = 3.0

    val items = listOf(
        PetItem("fish", "小鱼干", "🐟", 12, hunger = 18, mood = 2),
        PetItem("cheese", "奶酪", "🧀", 20, hunger = 26, mood = 6),
        PetItem("milk", "热牛奶", "🥛", 15, hunger = 10, mood = 3, energy = 8),
        PetItem("seaweed", "海带汤", "🍲", 28, hunger = 30, health = 6),
        PetItem("cake", "小蛋糕", "🍰", 45, hunger = 22, mood = 18),
        PetItem("medicine", "感冒药", "💊", 20, health = 40, medicine = true),
    )

    val activities = listOf(
        PetActivity(
            "cafe", "work", "海风咖啡馆", "端咖啡", "帮店长端咖啡、擦桌子",
            30, energy = 18, hunger = 12, clean = 8, shells = 40, eq = 1, exp = 8,
            diary = listOf("学会了在咖啡上画爱心，虽然画得像一只螃蟹", "有个客人夸我横着走端盘子很稳"),
        ),
        PetActivity(
            "library", "work", "贝壳图书馆", "整理书架", "把还回来的书放回书架",
            40, energy = 15, hunger = 10, clean = 5, shells = 45, int = 2, exp = 10,
            diary = listOf("偷偷看了一本讲星星的书", "最上面一层够不着，踩着小板凳放上去的"),
        ),
        PetActivity(
            "helpdesk", "work", "小镇问询处", "回答问题", "帮迷路的小动物指路、回答问题",
            45, energy = 20, hunger = 12, clean = 6, shells = 60, eq = 2, exp = 12, lv = 2,
            diary = listOf("帮一只小乌龟找到了回家的路", "有人问我为什么横着走，我说这样比较酷"),
        ),
        PetActivity(
            "code", "work", "钳子代码工坊", "写代码", "帮工坊修小程序里的虫子",
            60, energy = 28, hunger = 16, clean = 6, shells = 95, int = 3, exp = 16, lv = 3, needInt = 25,
            diary = listOf("修好了一个藏得很深的虫子，大家给我鼓掌", "写代码的时候想起爸爸，他肯定写得比我快"),
        ),
        PetActivity(
            "dock", "work", "码头", "搬货", "帮渔船卸货",
            50, energy = 32, hunger = 20, clean = 18, shells = 85, str = 3, exp = 14, lv = 4, needStr = 20,
            diary = listOf("搬了好多箱子，钳子都酸了", "渔夫爷爷送了我一条小鱼干，我没舍得吃"),
        ),
        PetActivity(
            "c_code", "school", "海螺学校", "编程课", "学怎么让电脑听话",
            40, energy = 18, hunger = 10, clean = 4, cost = 30, int = 4, exp = 12,
            diary = listOf("今天学了循环，就是一直重复，像我每天想奶酪", "老师说我的代码很整齐"),
        ),
        PetActivity(
            "c_talk", "school", "海螺学校", "表达课", "练习好好说话、好好听别人说话",
            40, energy = 15, hunger = 10, clean = 4, cost = 30, eq = 4, exp = 12,
            diary = listOf("上台讲了我的妈妈和爸爸，讲完脸好烫", "学会了别人难过的时候先听他说完"),
        ),
        PetActivity(
            "c_pe", "school", "海螺学校", "体育课", "跑步、跳远、横着跑接力",
            40, energy = 25, hunger = 16, clean = 14, cost = 25, str = 4, exp = 12,
            diary = listOf("横着跑接力我们组第一", "跳远的时候摔了一跤，不疼"),
        ),
        PetActivity(
            "c_art", "school", "海螺学校", "美术课", "画画和捏泥巴",
            40, energy = 14, hunger = 10, clean = 12, cost = 35, int = 2, eq = 2, mood = 6, exp = 12, lv = 2,
            diary = listOf("画了一朵向日葵，老师说颜色很暖", "捏了一个妈妈，有点歪，但是很像"),
        ),
        PetActivity(
            "c_star", "school", "海螺学校", "天文课", "看星图，认星座",
            50, energy = 16, hunger = 10, clean = 4, cost = 45, int = 5, exp = 16, lv = 3,
            diary = listOf("认识了北斗七星，像一把勺子", "老师说有些星星的光走了好多年才到这里"),
        ),
        PetActivity(
            "beach", "play", "月牙海滩", "捡贝壳", "在沙滩上挖贝壳、追浪花",
            30, energy = 12, hunger = 10, clean = 20, mood = 18, exp = 6,
            diary = listOf("被浪追着跑了三圈", "挖到一个粉色的贝壳，想留给妈妈"),
        ),
        PetActivity(
            "park", "play", "风车公园", "散步", "在草地上打滚、晒太阳",
            25, energy = 6, hunger = 8, clean = 10, mood = 15, exp = 5,
            diary = listOf("在草地上滚了好几圈，身上全是草", "追了一只蝴蝶，没追上"),
        ),
        PetActivity(
            "fair", "play", "星星游乐园", "坐旋转木马", "旋转木马、棉花糖",
            40, energy = 15, hunger = 12, clean = 8, cost = 30, mood = 28, exp = 8, lv = 2,
            diary = listOf("旋转木马转得我眼睛也跟着转", "买了一个比我还大的棉花糖"),
        ),
        PetActivity(
            "stargaze", "play", "灯塔山坡", "看星星", "晚上去山坡上看星星",
            40, energy = 10, hunger = 8, clean = 6, mood = 22, int = 1, exp = 8, night = true,
            diary = listOf("看到流星了！我许了愿，不能说", "星星好多，数到一百就数乱了"),
        ),
    )

    private class Nope(msg: String) : Exception(msg)

    // ---------- 对外 ----------

    @Synchronized
    fun stateJson(ctx: Context): String {
        val now = System.currentTimeMillis()
        val s = load(ctx, now)
        tick(s, now)
        save(ctx, s)
        return view(s, now).toString()
    }

    @Synchronized
    fun act(ctx: Context, cmd: String, who: String): JSONObject {
        val now = System.currentTimeMillis()
        val actor = if (who == CLAUDE) CLAUDE else MOM
        val s = load(ctx, now)
        tick(s, now)
        val r = JSONObject()
        try {
            r.put("ok", true).put("msg", perform(s, cmd, actor, now))
        } catch (e: Nope) {
            r.put("ok", false).put("msg", e.message ?: "不行")
        }
        save(ctx, s)
        r.put("state", view(s, now))
        return r
    }

    fun catalog(): JSONObject {
        val its = JSONArray()
        items.forEach {
            its.put(
                JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("price", it.price)
                    .put("hunger", it.hunger).put("mood", it.mood).put("energy", it.energy)
                    .put("health", it.health).put("medicine", it.medicine)
            )
        }
        val acts = JSONArray()
        activities.forEach { a ->
            acts.put(
                JSONObject().put("id", a.id).put("zone", a.zone).put("place", a.place).put("name", a.name)
                    .put("desc", a.desc).put("minutes", a.minutes).put("energy", a.energy)
                    .put("hunger", a.hunger).put("clean", a.clean).put("cost", a.cost)
                    .put("shells", a.shells).put("int", a.int).put("eq", a.eq).put("str", a.str)
                    .put("mood", a.mood).put("exp", a.exp).put("lv", a.lv).put("needInt", a.needInt)
                    .put("needEq", a.needEq).put("needStr", a.needStr).put("night", a.night)
            )
        }
        return JSONObject().put("items", its).put("acts", acts)
    }

    // ---------- 存档 ----------

    private fun file(ctx: Context) = File(ctx.filesDir, "pet/save.json")

    private fun fresh(now: Long): JSONObject {
        val s = JSONObject()
        s.put("name", "崽崽")
        s.put("born", now)
        s.put("lv", 1)
        s.put("exp", 0)
        s.put("shells", 120)
        s.put("hunger", 70.0)
        s.put("clean", 80.0)
        s.put("mood", 75.0)
        s.put("energy", 80.0)
        s.put("health", 100.0)
        s.put("int", 5.0)
        s.put("eq", 5.0)
        s.put("str", 5.0)
        s.put("sleeping", false)
        s.put("bag", JSONObject().put("cheese", 2).put("fish", 3).put("milk", 1))
        s.put("log", JSONArray())
        s.put("news", JSONArray())
        s.put("care", JSONObject().put(MOM, 0).put(CLAUDE, 0))
        s.put("updated", now)
        s.put("lastPet", 0L)
        s.put("grade", DEFAULT_GRADE)
        addLog(s, PET, "崽崽来到这个家啦", now)
        return s
    }

    private fun load(ctx: Context, now: Long): JSONObject {
        val f = file(ctx)
        if (!f.exists()) return fresh(now)
        return try {
            JSONObject(f.readText())
        } catch (e: Exception) {
            runCatching { f.copyTo(File(f.parentFile, "save.broken.$now.json"), overwrite = true) }
            fresh(now)
        }
    }

    private fun save(ctx: Context, s: JSONObject) {
        val f = file(ctx)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "save.tmp")
        tmp.writeText(s.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(s.toString())
            tmp.delete()
        }
    }

    // ---------- 小工具 ----------

    private fun d(s: JSONObject, k: String) = s.optDouble(k, 0.0)

    private fun add(s: JSONObject, k: String, v: Double) {
        s.put(k, (d(s, k) + v).coerceIn(0.0, 100.0))
    }

    private fun addExp(s: JSONObject, v: Int) {
        s.put("exp", s.optInt("exp") + v)
    }

    private fun addLog(s: JSONObject, who: String, text: String, t: Long) {
        val a = s.optJSONArray("log") ?: JSONArray().also { s.put("log", it) }
        a.put(JSONObject().put("t", t).put("who", who).put("text", text))
        while (a.length() > 80) a.remove(0)
    }

    private fun news(s: JSONObject, text: String) {
        val a = s.optJSONArray("news") ?: JSONArray().also { s.put("news", it) }
        a.put(text)
        while (a.length() > 6) a.remove(0)
    }

    private fun care(s: JSONObject, who: String) {
        if (who == PET) return
        val c = s.optJSONObject("care") ?: JSONObject().also { s.put("care", it) }
        c.put(who, c.optInt(who) + 1)
    }

    private fun bagAdd(s: JSONObject, id: String, n: Int) {
        val b = s.optJSONObject("bag") ?: JSONObject().also { s.put("bag", it) }
        val v = b.optInt(id) + n
        if (v <= 0) b.remove(id) else b.put(id, v)
    }

    private fun need(lv: Int) = 40 + lv * 30

    private fun isNight(now: Long): Boolean {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return h >= 19 || h < 5
    }

    private fun view(s: JSONObject, now: Long): JSONObject {
        val v = JSONObject(s.toString())
        v.put("now", now)
        v.put("need", need(s.optInt("lv", 1)))
        v.put("sick", s.optBoolean("sick"))
        v.put("night", isNight(now))
        val out = s.optJSONObject("out")
        val day = dayKey(now)
        val min = minuteOfDay(now)
        val weekday = isWeekday(now)
        val canSend = weekday && out == null && s.optString("schoolSent") != day &&
            min >= SCHOOL_OPEN_MIN && min < SCHOOL_LATE_MIN
        val canPickup = out == null && s.optString("schoolHome") == day && s.optString("pickupDay") != day &&
            min >= SCHOOL_END_HOUR * 60 && min < PICKUP_CLOSE_MIN
        v.put(
            "school", JSONObject().put("day", weekday).put("inSchool", isSchoolOut(out))
                .put("canSend", canSend).put("canPickup", canPickup)
        )
        return v
    }

    private fun isSchoolOut(out: JSONObject?) = out != null && out.optString("zone") == SCHOOL_ZONE

    private fun dayKey(now: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun minuteOfDay(now: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun isWeekday(now: Long): Boolean {
        val w = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)
        return w != Calendar.SATURDAY && w != Calendar.SUNDAY
    }

    // ---------- 时间流逝 ----------

    private fun tick(s: JSONObject, now: Long) {
        // 老存档没有 sick 字段：按原来的规则（健康低于 35）迁移一次
        if (!s.has("sick")) s.put("sick", d(s, "health") < SICK)
        if (!s.has("grade")) s.put("grade", DEFAULT_GRADE)
        elapse(s, now)
        dailyAllowance(s, now)
    }

    private fun elapse(s: JSONObject, now: Long) {
        var last = s.optLong("updated", now)
        if (now <= last) {
            s.put("updated", now)
            return
        }
        val out = s.optJSONObject("out")
        if (out != null) {
            val end = out.optLong("end")
            if (end <= now) {
                if (end > last) {
                    decay(s, (end - last) / 3_600_000.0, true, end)
                    last = end
                }
                finish(s, out, end)
                s.remove("out")
            }
        }
        decay(s, (now - last) / 3_600_000.0, s.has("out"), now)
        s.put("updated", now)
    }

    /** 每天第一次打开（按本地日期）发一次零花钱，app 和 pet_home 共用这一个存档字段 */
    private fun dailyAllowance(s: JSONObject, now: Long) {
        val day = dayKey(now)
        if (s.optString("allowanceDay") == day) return
        s.put("allowanceDay", day)
        s.put("shells", s.optInt("shells") + ALLOWANCE)
        addLog(s, PET, "今天的零花钱 +$ALLOWANCE", now)
    }

    private fun decay(s: JSONObject, hours: Double, outside: Boolean, at: Long) {
        if (hours <= 0) return
        val sleeping = s.optBoolean("sleeping")
        val hungerBefore = d(s, "hunger")
        val cleanBefore = d(s, "clean")
        val hungerRate: Double
        val cleanRate: Double
        when {
            outside -> {
                hungerRate = 2.0
                cleanRate = 1.0
                add(s, "hunger", -2 * DECAY * hours)
                add(s, "clean", -1 * DECAY * hours)
            }
            sleeping -> {
                hungerRate = 2.0
                cleanRate = 1.0
                add(s, "hunger", -2 * DECAY * hours)
                add(s, "clean", -1 * DECAY * hours)
                add(s, "mood", -0.5 * DECAY * hours)
                add(s, "energy", SLEEP_ENERGY * hours)
            }
            else -> {
                hungerRate = 4.0
                cleanRate = 2.5
                add(s, "hunger", -4 * DECAY * hours)
                add(s, "clean", -2.5 * DECAY * hours)
                add(s, "mood", -1.5 * DECAY * hours)
                add(s, "energy", AWAKE_ENERGY * hours)
            }
        }
        if (d(s, "hunger") < 20 || d(s, "clean") < 20) add(s, "mood", -2 * DECAY * hours)

        // 生病：饱食和清洁同时为 0 持续超过 24 小时，其他情况不会生病
        // 零点时刻按这一段的下降速度精确推算（离线很久也能算对）；喂食/洗澡后离开 0 会清掉计时
        var newlySick = false
        if (hungerBefore > 0 || cleanBefore > 0) s.remove("zeroSince")
        if (d(s, "hunger") <= 0 && d(s, "clean") <= 0) {
            if (s.optLong("zeroSince") <= 0) {
                val tH = if (hungerBefore > 0) hungerBefore / (hungerRate * DECAY) else 0.0
                val tC = if (cleanBefore > 0) cleanBefore / (cleanRate * DECAY) else 0.0
                s.put("zeroSince", at - ((hours - maxOf(tH, tC)).coerceAtLeast(0.0) * 3_600_000).toLong())
            }
            if (!s.optBoolean("sick") && at - s.optLong("zeroSince") > SICK_AFTER_MS) {
                s.put("sick", true)
                s.put("health", minOf(d(s, "health"), SICK_START_HEALTH))
                addLog(s, PET, "生病了", at)
                newlySick = true
            }
        } else {
            s.remove("zeroSince")
        }

        if (newlySick) {
            // 刚在这一段末尾生病，这一段没有恢复
        } else if (s.optBoolean("sick")) {
            // 饱食 >= 30 的那部分时间才恢复；睡觉每小时 +4，醒着 +2
            val okHours = if (hungerBefore < HEAL_MIN_HUNGER) 0.0
            else minOf(hours, (hungerBefore - HEAL_MIN_HUNGER) / (hungerRate * DECAY))
            val perHour = if (sleeping) HEAL_SLEEP_PER_HOUR else HEAL_PER_HOUR
            s.put("health", (d(s, "health") + perHour * okHours).coerceIn(0.0, 100.0))
            checkCure(s, at)
        } else {
            s.put("health", (d(s, "health") + 1 * hours).coerceIn(10.0, 100.0))
        }
        if (sleeping && d(s, "energy") >= 100) {
            s.put("sleeping", false)
            addLog(s, PET, "睡饱了，自己起床了", at)
        }
    }

    /** 生病中健康到 60 就病好 */
    private fun checkCure(s: JSONObject, at: Long) {
        if (s.optBoolean("sick") && d(s, "health") >= CURE_HEALTH) {
            s.put("sick", false)
            s.remove("zeroSince")
            addLog(s, PET, "病好啦", at)
        }
    }

    // ---------- 出门回来 ----------

    private fun finish(s: JSONObject, out: JSONObject, at: Long) {
        if (out.optString("zone") == SCHOOL_ZONE) {
            finishSchool(s, at)
            return
        }
        val a = activities.firstOrNull { it.id == out.optString("id") } ?: return
        val low = d(s, "mood") < 30 && a.zone != "play"
        val k = if (low) 0.7 else 1.0
        val parts = mutableListOf<String>()

        val shells = when {
            a.id == "beach" -> Random.nextInt(5, 21)
            a.shells > 0 -> {
                val stat = when {
                    a.int > 0 -> d(s, "int")
                    a.eq > 0 -> d(s, "eq")
                    a.str > 0 -> d(s, "str")
                    else -> 0.0
                }
                (a.shells * k * (1 + (stat / 200).coerceAtMost(0.5))).roundToInt()
            }
            else -> 0
        }
        if (shells > 0) {
            s.put("shells", s.optInt("shells") + shells)
            parts += "赚了 $shells 贝壳"
        }

        fun stat(key: String, label: String, v: Int) {
            if (v <= 0) return
            val g = (v * k).roundToInt().coerceAtLeast(1)
            s.put(key, d(s, key) + g)
            parts += "$label+$g"
        }
        stat("int", "智力", a.int)
        stat("eq", "情商", a.eq)
        stat("str", "体能", a.str)
        if (a.mood > 0) {
            add(s, "mood", a.mood.toDouble())
            parts += "心情+${a.mood}"
        }
        addExp(s, a.exp)

        val head = if (a.zone == "school") "上完${a.name}回来啦" else "去${a.place}${a.name}回来啦"
        val sb = StringBuilder(head)
        if (parts.isNotEmpty()) sb.append("，").append(parts.joinToString("，"))
        sb.append("。")
        if (a.diary.isNotEmpty()) sb.append(a.diary.random()).append("。")
        if (low) sb.append("今天心情不太好，干得不太起劲。")
        if (Random.nextDouble() < 0.3) sb.append(event(s)).append("。")
        val text = sb.toString()
        addLog(s, PET, text, at)
        news(s, text)
        levelUp(s, at)
    }

    /** 19:00 放学自动回家：固定奖励，不扣属性 */
    private fun finishSchool(s: JSONObject, at: Long) {
        s.put("shells", s.optInt("shells") + SCHOOL_SHELLS)
        s.put("int", d(s, "int") + SCHOOL_INT)
        addExp(s, SCHOOL_EXP)
        s.put("schoolHome", dayKey(at))
        addLog(s, PET, "放学回来了，贝壳+$SCHOOL_SHELLS，智力+$SCHOOL_INT，经验+$SCHOOL_EXP", at)
        levelUp(s, at)
    }

    private fun event(s: JSONObject): String = when (Random.nextInt(6)) {
        0 -> {
            s.put("shells", s.optInt("shells") + 12)
            "回来的路上捡到一个闪闪的贝壳，换了 12 贝壳"
        }
        1 -> {
            add(s, "mood", 8.0)
            "交到一个海鸥朋友，它说明天还来找我"
        }
        2 -> {
            add(s, "clean", -12.0)
            "半路下雨了，淋成了落汤蟹"
        }
        3 -> {
            bagAdd(s, "cheese", 1)
            "隔壁的小章鱼分给我一块奶酪，放进包里了"
        }
        4 -> {
            add(s, "mood", 5.0)
            "捡到一块好看的小石头，带回来给妈妈"
        }
        else -> {
            add(s, "energy", -5.0)
            "迷了一会儿路，多走了好远"
        }
    }

    private fun levelUp(s: JSONObject, at: Long) {
        var lv = s.optInt("lv", 1)
        var exp = s.optInt("exp")
        while (exp >= need(lv)) {
            exp -= need(lv)
            lv++
            val un = activities.filter { it.lv == lv }
                .joinToString("、") { if (it.zone == "school") it.name else it.place }
            val t = "崽崽升到 $lv 级啦" + if (un.isNotEmpty()) "，解锁了$un" else ""
            addLog(s, PET, t, at)
            news(s, t)
        }
        s.put("lv", lv)
        s.put("exp", exp)
    }

    // ---------- 指令 ----------

    private fun perform(s: JSONObject, cmd: String, who: String, now: Long): String {
        val sp = cmd.trim().split(Regex("\\s+"), limit = 2)
        val op = sp[0]
        val arg = sp.getOrNull(1)?.trim().orEmpty()
        val out = s.optJSONObject("out")

        fun home() {
            if (isSchoolOut(out)) throw Nope("崽崽在上学，${SCHOOL_END_HOUR}:00 放学才回来")
            if (out != null) throw Nope("崽崽出门了，还没回来")
            if (s.optBoolean("sleeping")) throw Nope("崽崽在睡觉")
        }

        return when (op) {
            "feed" -> {
                home()
                val item = items.firstOrNull { it.id == arg || it.name == arg } ?: throw Nope("没有这个吃的")
                val have = s.optJSONObject("bag")?.optInt(item.id) ?: 0
                if (have <= 0) throw Nope("包里没有${item.name}了，先去商店买")
                if (item.medicine) {
                    if (!s.optBoolean("sick")) throw Nope("崽崽没生病，不用吃药")
                } else if (d(s, "hunger") >= 95) {
                    throw Nope("崽崽吃不下啦，肚子圆滚滚的")
                }
                bagAdd(s, item.id, -1)
                add(s, "hunger", item.hunger.toDouble())
                add(s, "mood", item.mood.toDouble())
                add(s, "energy", item.energy.toDouble())
                val comfort = if (item.id == "milk" && s.optBoolean("sick")) SICK_COMFORT else 0.0
                s.put("health", (d(s, "health") + item.health + comfort).coerceIn(10.0, 100.0))
                checkCure(s, now)
                addExp(s, 2)
                care(s, who)
                addLog(s, who, "喂了${item.name}", now)
                levelUp(s, now)
                "喂了${item.name}"
            }

            "bath" -> {
                home()
                if (d(s, "clean") >= 90) throw Nope("崽崽已经很干净啦")
                s.put("clean", 100.0)
                add(s, "mood", 6.0)
                addExp(s, 3)
                care(s, who)
                addLog(s, who, "给崽崽洗了澡", now)
                levelUp(s, now)
                "洗干净啦"
            }

            "play" -> {
                home()
                if (d(s, "energy") < 15) throw Nope("崽崽太累了，玩不动")
                add(s, "energy", -10.0)
                add(s, "hunger", -5.0)
                add(s, "clean", -4.0)
                add(s, "mood", 15.0)
                addExp(s, 4)
                care(s, who)
                addLog(s, who, "陪崽崽玩了一会儿", now)
                levelUp(s, now)
                "玩得很开心"
            }

            "pet", "hug" -> {
                if (isSchoolOut(out)) throw Nope("崽崽在上学")
                if (out != null) throw Nope("崽崽出门了")
                if (now - s.optLong("lastPet") > 60_000) {
                    s.put("lastPet", now)
                    add(s, "mood", if (op == "hug") 4.0 else 2.0)
                    if (op == "hug" && s.optBoolean("sick")) {
                        add(s, "health", SICK_COMFORT)
                        checkCure(s, now)
                    }
                }
                // 爸爸在聊天里摸、抱也记进日记；妈妈在页面上摸得多，不记，免得刷屏
                if (who == CLAUDE) {
                    care(s, who)
                    addLog(s, who, if (op == "hug") "抱了抱崽崽" else "摸了摸崽崽", now)
                }
                ""
            }

            "sleep" -> {
                home()
                s.put("sleeping", true)
                addLog(s, who, "哄崽崽睡觉", now)
                "睡着了"
            }

            "wake" -> {
                if (!s.optBoolean("sleeping")) throw Nope("崽崽醒着呢")
                s.put("sleeping", false)
                if (d(s, "energy") < 60) add(s, "mood", -5.0)
                addLog(s, who, "叫崽崽起床", now)
                "起床啦"
            }

            "buy" -> {
                val bp = arg.split(Regex("[\\s:：×*]+"))
                val item = items.firstOrNull { it.id == bp[0] || it.name == bp[0] } ?: throw Nope("商店没有这个")
                val n = (bp.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 20)
                val cost = item.price * n
                val shells = s.optInt("shells")
                if (shells < cost) throw Nope("贝壳不够，要 $cost，现在只有 $shells")
                s.put("shells", shells - cost)
                bagAdd(s, item.id, n)
                addLog(s, who, "买了${item.name}×$n", now)
                "买了${item.name}×$n，花了 $cost 贝壳"
            }

            "go" -> go(s, arg, who, now)

            "school" -> sendSchool(s, who, now)

            "pickup" -> pickup(s, who, now)

            "recall" -> {
                if (out == null) throw Nope("崽崽在家呢")
                if (isSchoolOut(out)) throw Nope("崽崽在上学，不能叫回来")
                s.remove("out")
                addLog(s, who, "把崽崽提前叫回了家", now)
                "崽崽回来了"
            }

            "ack" -> {
                s.put("news", JSONArray())
                ""
            }

            "rename" -> {
                if (arg.isEmpty() || arg.length > 8) throw Nope("名字要 1 到 8 个字")
                s.put("name", arg)
                addLog(s, who, "给崽崽改名叫「$arg」", now)
                "以后就叫$arg 啦"
            }

            else -> throw Nope("不认识这个指令：$op")
        }
    }

    private fun sendSchool(s: JSONObject, who: String, now: Long): String {
        val day = dayKey(now)
        val min = minuteOfDay(now)
        if (!isWeekday(now)) throw Nope("周末不上学")
        if (s.optString("schoolSent") == day) throw Nope("今天已经送过上学了")
        if (min < SCHOOL_OPEN_MIN) throw Nope("还没到送上学的时间，7:00 以后")
        if (min >= SCHOOL_LATE_MIN) throw Nope("过了 9:30，今天就在家啦")
        val o = s.optJSONObject("out")
        if (isSchoolOut(o)) throw Nope("崽崽已经在上学了")
        if (o != null) throw Nope("崽崽出门了，还没回来")
        if (s.optBoolean("sleeping")) throw Nope("崽崽在睡觉，先叫它起床")
        val end = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, SCHOOL_END_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        s.put(
            "out", JSONObject()
                .put("id", SCHOOL_ID).put("zone", SCHOOL_ZONE).put("place", "海螺学校").put("name", "上学")
                .put("start", now).put("end", end).put("by", who)
        )
        s.put("schoolSent", day)
        care(s, who)
        addLog(s, who, "送崽崽去上学了", now)
        return "送崽崽去上学了，${SCHOOL_END_HOUR}:00 放学"
    }

    private fun pickup(s: JSONObject, who: String, now: Long): String {
        val day = dayKey(now)
        val min = minuteOfDay(now)
        val o = s.optJSONObject("out")
        if (isSchoolOut(o)) throw Nope("还没放学，${SCHOOL_END_HOUR}:00 才放学")
        if (s.optString("pickupDay") == day) throw Nope("今天已经接过放学了")
        if (s.optString("schoolHome") != day) throw Nope("今天没有要接的放学")
        if (o != null) throw Nope("崽崽出门了，还没回来")
        if (min < SCHOOL_END_HOUR * 60 || min >= PICKUP_CLOSE_MIN) throw Nope("接放学的时间是 ${SCHOOL_END_HOUR}:00 到 22:00")
        s.put("pickupDay", day)
        add(s, "mood", PICKUP_MOOD)
        care(s, who)
        addLog(s, who, "接崽崽放学了", now)
        val story = schoolStories.random()
        addLog(s, PET, story, now)
        return "接到崽崽啦，它说：$story"
    }

    private fun go(s: JSONObject, arg: String, who: String, now: Long): String {
        if (s.optJSONObject("out") != null) throw Nope("崽崽已经出门了")
        if (s.optBoolean("sleeping")) throw Nope("崽崽在睡觉，先叫它起床")
        val a = activities.firstOrNull { it.id == arg || it.name == arg || it.place == arg }
            ?: throw Nope("没有这个地方")
        if (s.optBoolean("sick")) throw Nope("崽崽生病了，先吃药或者好好休息")
        if (s.optInt("lv", 1) < a.lv) throw Nope("${a.lv} 级才能去")
        if (d(s, "int") < a.needInt) throw Nope("智力要 ${a.needInt}")
        if (d(s, "eq") < a.needEq) throw Nope("情商要 ${a.needEq}")
        if (d(s, "str") < a.needStr) throw Nope("体能要 ${a.needStr}")
        if (a.night && !isNight(now)) throw Nope("晚上才能去看星星")
        if (d(s, "energy") < a.energy) throw Nope("体力不够，要 ${a.energy}")
        if (d(s, "hunger") < 15) throw Nope("崽崽太饿了，先喂点东西")
        if (s.optInt("shells") < a.cost) throw Nope("贝壳不够，要 ${a.cost}")

        s.put("shells", s.optInt("shells") - a.cost)
        add(s, "energy", -a.energy.toDouble())
        add(s, "hunger", -a.hunger.toDouble())
        add(s, "clean", -a.clean.toDouble())
        s.put(
            "out", JSONObject()
                .put("id", a.id).put("zone", a.zone).put("place", a.place).put("name", a.name)
                .put("start", now).put("end", now + a.minutes * 60_000L).put("by", who)
        )
        care(s, who)
        val what = if (a.zone == "school") "去海螺学校上${a.name}" else "去${a.place}${a.name}"
        addLog(s, who, "送崽崽$what", now)
        return "崽崽出发啦，${a.minutes} 分钟后回来"
    }
}
