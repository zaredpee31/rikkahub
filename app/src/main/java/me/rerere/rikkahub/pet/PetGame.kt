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

data class PetIngredient(val id: String, val name: String, val icon: String, val price: Int)

data class PetDish(
    val id: String,
    val name: String,
    val icon: String,
    val a: String,
    val b: String,
    val hunger: Int,
    val mood: Int = 0,
    val energy: Int = 0,
    val health: Int = 0,
    val int: Int = 0,
    val str: Int = 0,
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

/** 朋友：at 是常在的地方，出去玩的活动 id，或者 "school" */
data class PetFriend(val id: String, val name: String, val emoji: String, val species: String, val at: String)

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

    // ---------- 年级 / 考试 / 写作业的数值，都在这里调 ----------

    private val GRADE_NUMS = listOf("一", "二", "三", "四", "五", "六")
    private const val GRADE_MAX = 6
    private const val GRADUATED_GRADE = "毕业啦"

    // 上满这么多天学（放学回家算一天）考试；不及格后 schoolDays 设成 RETAKE_START_DAYS，再上到 EXAM_AT_DAYS 天补考
    private const val EXAM_AT_DAYS = 10
    private const val RETAKE_START_DAYS = 5

    // 及格线需要的智力：EXAM_NEED_BASE + EXAM_NEED_STEP * (年级 - 1)
    private const val EXAM_NEED_BASE = 10
    private const val EXAM_NEED_STEP = 15

    // 分数 = EXAM_BASE + (智力 - need) * EXAM_INT_K + 随机[-EXAM_RANDOM, EXAM_RANDOM] + 各种加减，限制在 0–100
    private const val EXAM_BASE = 60
    private const val EXAM_INT_K = 1.5
    private const val EXAM_RANDOM = 10
    private const val EXAM_GOOD_MOOD_AT = 60 // 心情 >= 这个值再加 EXAM_GOOD_MOOD_BONUS 分
    private const val EXAM_GOOD_MOOD_BONUS = 5
    private const val EXAM_LOW_ENERGY_AT = 20 // 体力 < 这个值扣 EXAM_LOW_ENERGY_PENALTY 分
    private const val EXAM_LOW_ENERGY_PENALTY = 10
    private const val EXAM_SICK_PENALTY = 10

    private const val EXAM_PASS = 60
    private const val EXAM_PASS_SHELLS = 30
    private const val EXAM_PASS_EXP = 20
    private const val EXAM_GREAT = 90
    private const val EXAM_GREAT_SHELLS = 30
    private const val EXAM_GREAT_MOOD = 10
    private const val EXAM_FAIL_MOOD = 5
    private const val EXAM_KEEP = 20 // 成绩单最多留几条

    // 陪写作业：HOMEWORK_FROM_HOUR:00 到 HOMEWORK_TO_HOUR:00，放学回来过才能写，一天一次
    private const val HOMEWORK_FROM_HOUR = 19
    private const val HOMEWORK_TO_HOUR = 22
    private const val HOMEWORK_INT = 2
    private const val HOMEWORK_MOOD = -2
    private const val HOMEWORK_ENERGY = -5
    private const val HOMEWORK_EXP = 3
    private const val HOMEWORK_MIN_ENERGY = 10

    // ---------- 朋友 / 体能 / 情商的数值，都在这里调 ----------

    private const val FRIEND_SCHOOL = "school"
    private const val FRIEND_SCHOOL_PLACE = "海螺学校操场"
    private const val FRIEND_SCHOOL_SPOT = "friend_school" // 找学校朋友玩时 out.id，地图上用它定位

    // 认识新朋友：出去玩回来 / 放学回家。概率 = BASE + 情商 × PER_EQ，不超过 MAX
    private const val MEET_PLAY_BASE = 0.15
    private const val MEET_PLAY_PER_EQ = 0.005
    private const val MEET_PLAY_MAX = 0.5
    private const val MEET_SCHOOL_BASE = 0.10
    private const val MEET_SCHOOL_PER_EQ = 0.003
    private const val MEET_SCHOOL_MAX = 0.35
    private const val FRIEND_START = 20
    private const val FRIEND_BEST_AT = 60 // 好感 >= 这个值算好朋友
    private const val FRIEND_PRESENT = 0.5 // 再去同一个地方时已认识的朋友在场的概率

    // 在场玩：好感 + min(PRESENT_MAX, PRESENT_BASE + 情商 / EQ_DIV)
    private const val PRESENT_BASE = 5
    private const val PRESENT_MAX = 10

    // 找朋友玩：30 分钟，免费，好感 + min(VISIT_MAX, VISIT_BASE + 情商 / EQ_DIV)
    private const val VISIT_MINUTES = 30
    private const val VISIT_ENERGY = 8
    private const val VISIT_HUNGER = 6
    private const val VISIT_CLEAN = 6
    private const val VISIT_MOOD = 15
    private const val VISIT_EQ = 1
    private const val VISIT_EXP = 5
    private const val VISIT_BASE = 10
    private const val VISIT_MAX = 15
    private const val FRIEND_EQ_DIV = 20.0
    private const val VISIT_FROM_HOUR = 7
    private const val VISIT_TO_HOUR = 21
    private const val VISIT_MIN_HUNGER = 15

    // 好朋友放学送小礼物：概率、贝壳数；一天最多收一次
    private const val GIFT_CHANCE = 0.2
    private const val GIFT_SHELLS = 5

    // 体能：出去玩和打工的体力消耗 × (1 - min(体能, STR_CAP) / STR_DIV)，取整，最少 1
    private const val STR_CAP = 80.0
    private const val STR_DIV = 200.0

    // 情商：打工的贝壳收入加 情商 / EQ_WORK_DIV %（取整）
    private const val EQ_WORK_DIV = 10

    private const val MS_HOUR = 3_600_000L
    private const val MS_DAY = 86_400_000L

    // 家长会：放学回家时，没有进行中的家长会、离上次（或第一次运行这个版本）满 MEETING_EVERY_DAYS 天，按 MEETING_CHANCE 发通知
    private const val MEETING_EVERY_DAYS = 7
    private const val MEETING_CHANCE = 0.15
    private const val MEETING_NOTICE_HOURS = 72 // 通知有效期，妈妈要在这之内去
    private const val MEETING_DAD_HOURS = 24 // 妈妈去了之后等爸爸多久
    private const val MEETING_MOOD = 8.0 // 妈妈、爸爸各加一次
    private const val MEETING_DAD_EQ = 1
    private const val MEETING_PRAISE_AT = 80 // 最近一次考试 >= 这个分数，妈妈到场时老师表扬
    private const val MEETING_PRAISE_SHELLS = 20
    private const val MEETING_HOMEWORK_OK = 3 // 距上次家长会写作业 >= 这么多次算"按时交"
    private const val MEETING_KEEP = 10

    // 运动会：放学回家时，没有待开的运动会、离上次满 SPORTS_EVERY_DAYS 天，按 SPORTS_CHANCE 定在往后第 SPORTS_AHEAD_WEEKDAYS 个工作日
    private const val SPORTS_EVERY_DAYS = 10
    private const val SPORTS_CHANCE = 0.12
    private const val SPORTS_AHEAD_WEEKDAYS = 2
    // 横着跑 50 米：体能 × SPORTS_STR_K + 体力 / SPORTS_ENERGY_DIV + 随机 0..SPORTS_RANDOM + 每个到场家长 SPORTS_PARENT_BONUS
    private const val SPORTS_STR_K = 1.2
    private const val SPORTS_ENERGY_DIV = 5.0
    private const val SPORTS_RANDOM = 20
    private const val SPORTS_PARENT_BONUS = 10
    private const val SPORTS_FIRST_AT = 70
    private const val SPORTS_SECOND_AT = 50
    private const val SPORTS_THIRD_AT = 40
    private const val SPORTS_FIRST_SHELLS = 30
    private const val SPORTS_FIRST_MOOD = 15.0
    private const val SPORTS_PLACE_SHELLS = 15 // 第二、三名
    private const val SPORTS_PLACE_MOOD = 10.0
    private const val SPORTS_NO_PLACE_MOOD = 5.0
    private const val SPORTS_STR = 2 // 不管名次
    private const val SPORTS_EXP = 10
    private const val SPORTS_EQ = 1 // 爸妈都来了，两人三脚
    private const val SPORTS_KEEP = 10

    private val meetingDadLines = listOf(
        "爸爸也来啦，崽崽今天上课老往门口看。",
        "爸爸来了崽崽特别高兴，回家多夸夸它。",
        "爸爸妈妈都来了，崽崽下课跟同学炫耀了好久。",
        "谢谢爸爸专门跑一趟，崽崽说爸爸做饭最好吃。",
    )

    val friends = listOf(
        PetFriend("momo", "墨墨", "🐙", "小章鱼", "beach"),
        PetFriend("manman", "慢慢", "🐢", "小海龟", "park"),
        PetFriend("xingxing", "星星", "⭐", "小海星", "stargaze"),
        PetFriend("tuantuan", "团团", "🦭", "小海豹", "fair"),
        PetFriend("shanshan", "闪闪", "🪼", "小水母", "aquarium"),
        PetFriend("keke", "壳壳", "🐚", "寄居蟹", FRIEND_SCHOOL),
        PetFriend("paopao", "泡泡", "🐡", "小河豚", FRIEND_SCHOOL),
        PetFriend("alan", "阿蓝", "🐳", "小鲸鱼", FRIEND_SCHOOL),
    )

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

    // ---------- 做饭 / 哄睡的数值，都在这里调 ----------

    // 晚饭时间 [DINNER_FROM, DINNER_TO) 做饭，心情再加 DINNER_MOOD
    private const val DINNER_FROM_HOUR = 19
    private const val DINNER_TO_HOUR = 21
    private const val DINNER_MOOD = 5

    // 菜里有爱吃的食材，心情再加 LOVE_MOOD；有不爱吃的，这道菜的心情加成作废（不扣）
    private const val LOVE_MOOD = 6
    private const val TASTE_LOVE_COUNT = 2
    private const val TASTE_HATE_COUNT = 1

    // 黑暗料理（配方表以外的组合）
    private const val DARK_HUNGER = 20
    private const val DARK_MOOD = -3

    // 一次最多买几份食材
    private const val FOOD_BUY_MAX = 20

    // 做饭的经验（和喂食一样）
    private const val COOK_EXP = 2

    // 哄睡：[COAX_FROM_HOUR:00 到次日 COAX_TO_HOUR:00) 点「睡觉」按哄睡算，心情再加 COAX_MOOD
    private const val COAX_FROM_HOUR = 21
    private const val COAX_TO_HOUR = 2
    private const val COAX_MOOD = 5

    private val coaxLines = listOf(
        "讲了一个小鱼的故事",
        "它说明天还想吃番茄炒蛋",
        "抱着小鸭子睡着了",
        "数到第三只小海龟就睡着了",
        "轻轻拍着它，钳子慢慢松开了",
        "它问月亮是不是也困了",
        "哼了一首摇篮曲，它跟着哼了两句",
        "它说今天最喜欢捡到的那个贝壳",
        "把被角掖好，它眯着眼笑了",
        "讲到一半它就睡着了",
        "它迷迷糊糊说了一句晚安",
        "说好明天早点起来看日出",
    )

    // 食材：买回来放冰箱（存档 fridge），不会坏
    val ingredients = listOf(
        PetIngredient("tomato", "番茄", "🍅", 4),
        PetIngredient("egg", "鸡蛋", "🥚", 3),
        PetIngredient("shrimp", "虾仁", "🦐", 8),
        PetIngredient("rice", "米饭", "🍚", 3),
        PetIngredient("veg", "青菜", "🥬", 3),
        PetIngredient("tofu", "豆腐", "🧈", 4),
        PetIngredient("whitefish", "鱼", "🐠", 8),
        PetIngredient("potato", "土豆", "🥔", 4),
    )

    // 配方（两样无顺序）
    val dishes = listOf(
        PetDish("tomato_egg", "番茄炒蛋", "🍳", "tomato", "egg", hunger = 35, mood = 8),
        PetDish("shrimp_egg", "虾仁滑蛋", "🦐", "shrimp", "egg", hunger = 35, mood = 6, int = 1),
        PetDish("egg_rice", "蛋炒饭", "🍛", "rice", "egg", hunger = 40, energy = 8),
        PetDish("shrimp_rice", "虾仁炒饭", "🍤", "shrimp", "rice", hunger = 40, str = 1),
        PetDish("fish_tofu", "鱼头豆腐汤", "🍲", "whitefish", "tofu", hunger = 35, health = 10),
        PetDish("veg_tofu", "青菜豆腐", "🥘", "veg", "tofu", hunger = 30, health = 6),
        PetDish("tomato_potato", "番茄土豆汤", "🥣", "tomato", "potato", hunger = 35, mood = 5),
    )

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
        PetActivity(
            "aquarium", "play", "珊瑚水族馆", "看鱼", "隔着玻璃看大鱼小鱼游来游去",
            40, energy = 10, hunger = 8, clean = 4, cost = 25, mood = 15, int = 2, exp = 8,
            diary = listOf("有一条大鱼贴着玻璃游过去，我跟着它走了半圈", "小鱼排着队转圈圈，我数不清到底有几条"),
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
        lastCook = null
        try {
            r.put("ok", true).put("msg", perform(s, cmd, actor, now))
            lastCook?.let { r.put("cook", it) }
        } catch (e: Nope) {
            r.put("ok", false).put("msg", e.message ?: "不行")
        }
        lastCook = null
        save(ctx, s)
        r.put("state", view(s, now))
        return r
    }

    // 刚做完的这道菜，给页面画结果卡片用；act 在 @Synchronized 里读写
    private var lastCook: JSONObject? = null

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
        val ings = JSONArray()
        ingredients.forEach {
            ings.put(JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("price", it.price))
        }
        // 菜的配方不放进目录，没做过的菜页面上只显示「？？？」
        return JSONObject().put("items", its).put("acts", acts).put("ingredients", ings)
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
        s.put("gradeNum", 1)
        s.put("schoolDays", 0)
        s.put("exams", JSONArray())
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
        v.remove("taste") // 口味不在界面上直接显示
        v.put("coax", isCoaxTime(now))
        v.put("dinner", isDinnerTime(now))
        v.put("recipeBook", recipeBook(s))
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
        // 写作业按钮：时间、在家、今天放学回来过、今天没写过；睡着/生病/体力不够点了再提示
        val canHomework = out == null && s.optString("schoolHome") == day && s.optString("homeworkDay") != day &&
            min >= HOMEWORK_FROM_HOUR * 60 && min < HOMEWORK_TO_HOUR * 60
        val graduated = s.optBoolean("graduated")
        v.put(
            "exam", JSONObject().put("graduated", graduated)
                .put("daysLeft", if (graduated) 0 else (EXAM_AT_DAYS - s.optInt("schoolDays")).coerceAtLeast(0))
                .put("retake", s.optBoolean("retake") && !graduated)
                .put("canHomework", canHomework)
        )
        // 体能折算后的体力消耗，界面和工具都显示这个
        val ec = JSONObject()
        activities.forEach { ec.put(it.id, actEnergy(s, it)) }
        v.put("energyCost", ec)
        v.put("friendEnergy", energyCost(s, VISIT_ENERGY))
        // 已认识的朋友：好感、常在哪、现在能不能去找 TA 玩（不能的话写原因）
        val known = s.optJSONObject("friends") ?: JSONObject()
        val fl = JSONArray()
        for (f in friends) {
            if (!known.has(f.id)) continue
            val why = friendWhy(s, f, now)
            fl.put(
                JSONObject().put("id", f.id).put("name", f.name).put("emoji", f.emoji).put("species", f.species)
                    .put("at", f.at).put("place", friendPlace(f)).put("affinity", known.optInt(f.id))
                    .put("best", known.optInt(f.id) >= FRIEND_BEST_AT)
                    .put("canVisit", why == null).put("why", why ?: "")
            )
        }
        v.put("friendList", fl)
        // 家长会：none / notice（有通知，妈妈还没去）/ mom（妈妈去过，等爸爸）；deadline 是这一段的截止时间
        val mt = s.optJSONObject("meeting")
        val mState = when (mt?.optString("state")) {
            "notice" -> "notice"
            "mom" -> "mom"
            else -> "none"
        }
        val mDeadline = when (mState) {
            "notice" -> (mt?.optLong("at") ?: 0L) + MEETING_NOTICE_HOURS * MS_HOUR
            "mom" -> (mt?.optLong("momAt") ?: 0L) + MEETING_DAD_HOURS * MS_HOUR
            else -> 0L
        }
        v.put("meetingInfo", JSONObject().put("state", mState).put("deadline", mDeadline))
        // 运动会：哪天、谁报了名、现在能不能去看（当天 7:00–19:00）
        val sp = s.optJSONObject("sports")
        if (sp != null) {
            val sday = sp.optString("day")
            val today = sday == day
            v.put(
                "sportsInfo", JSONObject().put("day", sday).put("label", dayLabel(dayAt(sday, 12))).put("today", today)
                    .put("mom", sp.optBoolean("mom")).put("dad", sp.optBoolean("dad"))
                    .put("open", today && min >= SCHOOL_OPEN_MIN && min < SCHOOL_END_HOUR * 60)
            )
        }
        return v
    }

    // ---------- 体能 / 朋友 ----------

    /** 体能折算后的体力消耗：base × (1 - min(体能, 80) / 200)，取整，最少 1 */
    private fun energyCost(s: JSONObject, base: Int): Int =
        (base * (1 - minOf(d(s, "str"), STR_CAP) / STR_DIV)).roundToInt().coerceAtLeast(1)

    /** 打工和出去玩按体能折算，上课（课外班）不动 */
    private fun actEnergy(s: JSONObject, a: PetActivity): Int =
        if (a.zone == "work" || a.zone == "play") energyCost(s, a.energy) else a.energy

    private fun friendsOf(s: JSONObject): JSONObject =
        s.optJSONObject("friends") ?: JSONObject().also { s.put("friends", it) }

    private fun friendPlace(f: PetFriend): String =
        if (f.at == FRIEND_SCHOOL) FRIEND_SCHOOL_PLACE else activities.firstOrNull { it.id == f.at }?.place ?: f.at

    private fun findFriend(key: String) = friends.firstOrNull { it.id == key || it.name == key }

    private fun addAffinity(s: JSONObject, f: PetFriend, v: Int) {
        val fr = friendsOf(s)
        fr.put(f.id, (fr.optInt(f.id) + v).coerceIn(0, 100))
    }

    /** 认识朋友、收到礼物的一句话，页面下次打开弹一次 toast */
    private fun friendNote(s: JSONObject, text: String) {
        val old = s.optString("friendNote")
        s.put("friendNote", if (old.isEmpty()) text else "${old}；${text}")
    }

    /**
     * 在 at 这个地方碰朋友：已认识的各 50% 在场，好感 + min(10, 5 + 情商/20)；
     * 没认识的按 chance 的概率认识一个，好感 20。返回要写进日记的句子。
     */
    private fun meetFriends(s: JSONObject, at: String, placeName: String, chance: Double): List<String> {
        val known = friendsOf(s)
        val here = friends.filter { it.at == at }
        val eq = d(s, "eq")
        val lines = mutableListOf<String>()
        for (f in here.filter { known.has(it.id) }) {
            if (Random.nextDouble() < FRIEND_PRESENT) {
                addAffinity(s, f, minOf(PRESENT_MAX, (PRESENT_BASE + eq / FRIEND_EQ_DIV).toInt()))
                lines += "遇到了${f.name}，一起玩了一会儿"
            }
        }
        val fresh = here.filter { !known.has(it.id) }
        if (fresh.isNotEmpty() && Random.nextDouble() < chance) {
            val f = fresh.random()
            known.put(f.id, FRIEND_START)
            lines += "在${placeName}认识了新朋友${f.name}（${f.species}）"
            friendNote(s, "认识了新朋友${f.name}${f.emoji}")
        }
        return lines
    }

    /** 放学时好朋友（学校里的、好感 >= 60）20% 送小礼物，贝壳 +5 或小鱼干各半，一天最多收一次 */
    private fun schoolGift(s: JSONObject, at: Long) {
        val day = dayKey(at)
        if (s.optString("giftDay") == day) return
        val known = friendsOf(s)
        val best = friends.filter { it.at == FRIEND_SCHOOL && known.optInt(it.id) >= FRIEND_BEST_AT }
        if (best.isEmpty() || Random.nextDouble() >= GIFT_CHANCE) return
        val f = best.random()
        s.put("giftDay", day)
        val what: String
        if (Random.nextBoolean()) {
            s.put("shells", s.optInt("shells") + GIFT_SHELLS)
            what = "${GIFT_SHELLS} 个贝壳"
        } else {
            bagAdd(s, "fish", 1)
            what = "一条小鱼干"
        }
        addLog(s, PET, "好朋友${f.name}送了崽崽${what}", at)
        friendNote(s, "崽崽收到${f.name}${f.emoji}送的${what}")
    }

    /** 现在能不能去找 f 玩；能返回 null，不能返回原因 */
    private fun friendWhy(s: JSONObject, f: PetFriend, now: Long): String? {
        if (!friendsOf(s).has(f.id)) return "还没认识 TA"
        val o = s.optJSONObject("out")
        if (isSchoolOut(o)) return "崽崽在上学"
        if (o != null) return "崽崽出门了"
        if (s.optBoolean("sleeping")) return "崽崽在睡觉"
        if (s.optBoolean("sick")) return "崽崽生病了"
        val min = minuteOfDay(now)
        if (min < VISIT_FROM_HOUR * 60 || min >= VISIT_TO_HOUR * 60) {
            return "${VISIT_FROM_HOUR}:00–${VISIT_TO_HOUR}:00 才能找朋友玩"
        }
        if (f.at == FRIEND_SCHOOL && isWeekday(now) && min < SCHOOL_END_HOUR * 60) {
            return "${f.name}在上课，${SCHOOL_END_HOUR}:00 以后再来"
        }
        if (s.optJSONObject("friendDay")?.optString(f.id) == dayKey(now)) return "今天已经找过${f.name}玩了"
        val en = energyCost(s, VISIT_ENERGY)
        if (d(s, "energy") < en) return "体力不够，要 $en"
        if (d(s, "hunger") < VISIT_MIN_HUNGER) return "崽崽太饿了"
        return null
    }

    private fun visitFriend(s: JSONObject, arg: String, who: String, now: Long): String {
        val f = findFriend(arg) ?: throw Nope("没有这个朋友")
        friendWhy(s, f, now)?.let { throw Nope(it) }
        add(s, "energy", -energyCost(s, VISIT_ENERGY).toDouble())
        add(s, "hunger", -VISIT_HUNGER.toDouble())
        add(s, "clean", -VISIT_CLEAN.toDouble())
        val fd = s.optJSONObject("friendDay") ?: JSONObject().also { s.put("friendDay", it) }
        fd.put(f.id, dayKey(now))
        val place = friendPlace(f)
        s.put(
            "out", JSONObject()
                .put("id", if (f.at == FRIEND_SCHOOL) FRIEND_SCHOOL_SPOT else f.at)
                .put("zone", "play").put("place", place).put("name", "找${f.name}玩").put("friend", f.id)
                .put("start", now).put("end", now + VISIT_MINUTES * 60_000L).put("by", who)
        )
        care(s, who)
        addLog(s, who, "送崽崽去${place}找${f.name}玩", now)
        return "崽崽出发去找${f.name}啦，${VISIT_MINUTES} 分钟后回来"
    }

    /** 找朋友玩回来：好感 + min(15, 10 + 情商/20)，心情、情商、经验 */
    private fun finishVisit(s: JSONObject, out: JSONObject, at: Long) {
        val f = friends.firstOrNull { it.id == out.optString("friend") } ?: return
        val gain = minOf(VISIT_MAX, (VISIT_BASE + d(s, "eq") / FRIEND_EQ_DIV).toInt())
        addAffinity(s, f, gain)
        add(s, "mood", VISIT_MOOD.toDouble())
        s.put("eq", d(s, "eq") + VISIT_EQ)
        addExp(s, VISIT_EXP)
        val text = "去${friendPlace(f)}找${f.name}玩回来啦，跟${f.name}玩得很开心。" +
            "好感+${gain}，心情+${VISIT_MOOD}，情商+${VISIT_EQ}，经验+${VISIT_EXP}"
        addLog(s, PET, text, at)
        news(s, text)
        levelUp(s, at)
    }

    private fun isSchoolOut(out: JSONObject?) = out != null && out.optString("zone") == SCHOOL_ZONE

    private fun isDinnerTime(now: Long): Boolean {
        val m = minuteOfDay(now)
        return m >= DINNER_FROM_HOUR * 60 && m < DINNER_TO_HOUR * 60
    }

    private fun isCoaxTime(now: Long): Boolean {
        val m = minuteOfDay(now)
        return m >= COAX_FROM_HOUR * 60 || m < COAX_TO_HOUR * 60
    }

    private fun ingName(id: String) = ingredients.firstOrNull { it.id == id }?.name ?: id

    private fun dishEffects(d: PetDish): String =
        listOf("饱食" to d.hunger, "心情" to d.mood, "体力" to d.energy, "健康" to d.health, "智力" to d.int, "体能" to d.str)
            .filter { it.second != 0 }
            .joinToString(" ") { "${it.first}+${it.second}" }

    /** 菜谱：7 道菜按配方表顺序，没做过的只有 known=false，不泄露配方和效果 */
    private fun recipeBook(s: JSONObject): JSONObject {
        val rec = s.optJSONObject("recipes") ?: JSONObject()
        val arr = JSONArray()
        for (d in dishes) {
            val r = rec.optJSONObject(d.id)
            val e = JSONObject().put("id", d.id)
            if (r == null || r.optInt("n") <= 0) {
                e.put("known", false)
            } else {
                e.put("known", true).put("name", d.name).put("icon", d.icon)
                    .put("ing", "${ingName(d.a)}+${ingName(d.b)}").put("eff", dishEffects(d))
                    .put("react", r.optString("react", "normal")).put("n", r.optInt("n"))
            }
            arr.put(e)
        }
        return JSONObject().put("dishes", arr).put("dark", s.optInt("darkCount"))
    }

    private fun fridgeAdd(s: JSONObject, id: String, n: Int) {
        val f = s.optJSONObject("fridge") ?: JSONObject().also { s.put("fridge", it) }
        val v = f.optInt(id) + n
        if (v <= 0) f.remove(id) else f.put(id, v)
    }

    private fun findIngredient(key: String) = ingredients.firstOrNull { it.id == key || it.name == key }

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
        // 第四期：年级。老存档只有 grade 字符串，没有 gradeNum 就按 1 补；grade 字符串跟着 gradeNum 同步
        if (!s.has("gradeNum")) s.put("gradeNum", 1)
        if (!s.has("schoolDays")) s.put("schoolDays", 0)
        if (!s.has("exams")) s.put("exams", JSONArray())
        syncGrade(s)
        // 第五期：朋友。老存档没有 friends 就是空
        if (!s.has("friends")) s.put("friends", JSONObject())
        // 第三期：冰箱、菜谱、口味。口味首次进入这个版本时随机定一次，之后不变
        if (!s.has("fridge")) s.put("fridge", JSONObject())
        if (!s.has("recipes")) s.put("recipes", JSONObject())
        if (!s.has("taste")) {
            val ids = ingredients.map { it.id }.shuffled()
            s.put(
                "taste", JSONObject()
                    .put("love", JSONArray(ids.take(TASTE_LOVE_COUNT)))
                    .put("hate", JSONArray(ids.drop(TASTE_LOVE_COUNT).take(TASTE_HATE_COUNT)))
            )
        }
        // 第七期：家长会、运动会。计时从第一次运行这个版本开始算
        if (!s.has("meetingClock")) s.put("meetingClock", now)
        if (!s.has("sportsClock")) s.put("sportsClock", now)
        checkMeeting(s, now)
        elapse(s, now)
        checkSports(s, now)
        dailyAllowance(s, now)
        startBonus(s, now)
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

    /** 一次性启动资金 1000 贝壳，存档里记 startBonus 防重复 */
    private fun startBonus(s: JSONObject, now: Long) {
        if (s.optBoolean("startBonus")) return
        s.put("startBonus", true)
        s.put("shells", s.optInt("shells") + 1000)
        addLog(s, CLAUDE, "给了一笔启动资金 +1000", now)
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
        if (out.has("friend")) {
            finishVisit(s, out, at)
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
                val base = a.shells * k * (1 + (stat / 200).coerceAtMost(0.5))
                // 情商：打工的贝壳收入再加 情商/10 %（取整）
                if (a.zone == "work") {
                    (base * (100 + (d(s, "eq") / EQ_WORK_DIV).toInt()) / 100.0).roundToInt()
                } else {
                    base.roundToInt()
                }
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
        if (a.zone == "play") {
            val chance = minOf(MEET_PLAY_MAX, MEET_PLAY_BASE + d(s, "eq") * MEET_PLAY_PER_EQ)
            val fx = meetFriends(s, a.id, a.place, chance)
            if (fx.isNotEmpty()) sb.append(fx.joinToString("，")).append("。")
        }
        if (low) sb.append("今天心情不太好，干得不太起劲。")
        if (Random.nextDouble() < 0.3) sb.append(event(s)).append("。")
        val text = sb.toString()
        addLog(s, PET, text, at)
        news(s, text)
        levelUp(s, at)
    }

    private fun gradeNumOf(s: JSONObject) = s.optInt("gradeNum", 1).coerceIn(1, GRADE_MAX)

    /** grade 字符串跟着 gradeNum / graduated 走，界面读 grade */
    private fun syncGrade(s: JSONObject) {
        val g = if (s.optBoolean("graduated")) GRADUATED_GRADE else "${GRADE_NUMS[gradeNumOf(s) - 1]}年级"
        s.put("grade", g)
    }

    /** 19:00 放学自动回家：固定奖励，不扣属性；放学天数 +1，满 EXAM_AT_DAYS 天这次回家就考试 */
    private fun finishSchool(s: JSONObject, at: Long) {
        s.put("shells", s.optInt("shells") + SCHOOL_SHELLS)
        s.put("int", d(s, "int") + SCHOOL_INT)
        addExp(s, SCHOOL_EXP)
        s.put("schoolHome", dayKey(at))
        val sb = StringBuilder("放学回来了，贝壳+$SCHOOL_SHELLS，智力+$SCHOOL_INT，经验+$SCHOOL_EXP")
        val exam = !s.optBoolean("graduated")
        if (exam) {
            val days = s.optInt("schoolDays") + 1
            s.put("schoolDays", days)
            if (days == EXAM_AT_DAYS - 1) sb.append("。明天要考试了")
        }
        // 学校里的朋友：新认识的最多一个，已认识的 50% 在场
        val chance = minOf(MEET_SCHOOL_MAX, MEET_SCHOOL_BASE + d(s, "eq") * MEET_SCHOOL_PER_EQ)
        val fx = meetFriends(s, FRIEND_SCHOOL, "海螺学校", chance)
        if (fx.isNotEmpty()) sb.append("。").append(fx.joinToString("，"))
        addLog(s, PET, sb.toString(), at)
        schoolGift(s, at)
        if (exam && s.optInt("schoolDays") >= EXAM_AT_DAYS) takeExam(s, at)
        settleSports(s, at)
        maybeMeetingNotice(s, at)
        maybeSports(s, at)
        levelUp(s, at)
    }

    // ---------- 家长会 / 运动会 ----------

    private fun addNote(s: JSONObject, key: String, text: String) {
        val old = s.optString(key)
        s.put(key, if (old.isEmpty()) text else "${old}；${text}")
    }

    /** "2026-11-21" 那天 hour 点整的时间戳；格式不对就当很久以前 */
    private fun dayAt(day: String, hour: Int): Long {
        val p = day.split("-").map { it.toIntOrNull() ?: 0 }
        if (p.size != 3) return 0L
        return Calendar.getInstance().apply {
            clear()
            set(p[0], p[1] - 1, p[2], hour, 0, 0)
        }.timeInMillis
    }

    /** 11 月 21 日（周五） */
    private fun dayLabel(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val w = "日一二三四五六"[c.get(Calendar.DAY_OF_WEEK) - 1]
        return "${c.get(Calendar.MONTH) + 1} 月 ${c.get(Calendar.DAY_OF_MONTH)} 日（周${w}）"
    }

    private fun lastExamScore(s: JSONObject): Int {
        val a = s.optJSONArray("exams") ?: return -1
        if (a.length() == 0) return -1
        return a.optJSONObject(a.length() - 1)?.optInt("score", -1) ?: -1
    }

    private fun maybeMeetingNotice(s: JSONObject, at: Long) {
        if (s.optJSONObject("meeting") != null) return
        if (at - s.optLong("meetingClock", at) < MEETING_EVERY_DAYS * MS_DAY) return
        if (Random.nextDouble() >= MEETING_CHANCE) return
        s.put("meeting", JSONObject().put("state", "notice").put("at", at))
        addLog(s, PET, "老师让我带回一张家长会通知", at)
        addNote(s, "meetingNote", "崽崽带回一张家长会通知，3 天内去开")
    }

    /** 通知 3 天没去就作废；妈妈去了 24 小时爸爸没来就自动结算。都不扣东西 */
    private fun checkMeeting(s: JSONObject, now: Long) {
        val m = s.optJSONObject("meeting") ?: return
        when (m.optString("state")) {
            "notice" -> {
                val end = m.optLong("at") + MEETING_NOTICE_HOURS * MS_HOUR
                if (now < end) return
                s.remove("meeting")
                s.put("meetingClock", end)
                addLog(s, PET, "这次家长会没去成", end)
            }
            "mom" -> {
                val end = m.optLong("momAt") + MEETING_DAD_HOURS * MS_HOUR
                if (now < end) return
                addLog(s, PET, "爸爸这次没赶上", end)
                settleMeeting(s, m, false, end)
                addNote(s, "meetingNote", "家长会结束了，爸爸这次没赶上")
            }
            else -> s.remove("meeting")
        }
    }

    /** 老师评语：成绩、在校、朋友三句 */
    private fun meetingComment(s: JSONObject): String {
        val score = lastExamScore(s)
        val a = when {
            score < 0 -> "还没到考试，上课很认真。"
            score >= EXAM_GREAT -> "考试特别棒。"
            score >= EXAM_PASS -> "成绩稳稳的。"
            else -> "考试有点吃力，回家多陪陪写作业。"
        }
        val hw = if (s.optInt("homeworkSince") >= MEETING_HOMEWORK_OK) "作业都按时交。" else "作业有时候忘带。"
        val b = "这个年级上了 ${s.optInt("schoolDays")} 天学，" + hw
        val known = friendsOf(s)
        val mates = friends.filter { it.at == FRIEND_SCHOOL && known.has(it.id) }
        val best = mates.maxByOrNull { known.optInt(it.id) }
        val c = when {
            best == null -> "下课喜欢一个人待着，可以鼓励它多交朋友。"
            mates.size >= 2 -> "跟同学处得很好，常和${best.name}一起玩。"
            else -> "交到了一个好朋友${best.name}。"
        }
        return a + b + c
    }

    private fun settleMeeting(s: JSONObject, m: JSONObject, dad: Boolean, at: Long) {
        val list = s.optJSONArray("meetings") ?: JSONArray().also { s.put("meetings", it) }
        val rec = JSONObject().put("day", dayKey(m.optLong("momAt", at)))
            .put("grade", m.optString("grade", s.optString("grade")))
            .put("comment", m.optString("comment")).put("mom", true).put("dad", dad)
        if (m.has("score")) rec.put("score", m.optInt("score"))
        list.put(rec)
        while (list.length() > MEETING_KEEP) list.remove(0)
        s.remove("meeting")
        s.put("homeworkSince", 0)
        s.put("lastMeeting", dayKey(at))
        s.put("meetingClock", at)
    }

    /** 家长会：妈妈（页面）先去，之后 24 小时内爸爸（pet_home）来 */
    private fun meeting(s: JSONObject, who: String, now: Long): String {
        val m = s.optJSONObject("meeting") ?: throw Nope("现在没有家长会，崽崽放学偶尔会带通知回来")
        val state = m.optString("state")
        if (who == CLAUDE) {
            if (state == "notice") throw Nope("家长会要妈妈先去，妈妈去过之后 ${MEETING_DAD_HOURS} 小时内爸爸再来")
            if (state != "mom") throw Nope("现在没有要参加的家长会")
            add(s, "mood", MEETING_MOOD)
            s.put("eq", d(s, "eq") + MEETING_DAD_EQ)
            val line = meetingDadLines.random()
            m.put("comment", m.optString("comment") + line)
            care(s, CLAUDE)
            addLog(s, CLAUDE, "去开家长会，爸爸也来了。老师说：$line", now)
            settleMeeting(s, m, true, now)
            return "爸爸也到了家长会，老师说：${line}心情+${MEETING_MOOD.toInt()}，情商+$MEETING_DAD_EQ"
        }
        if (state == "mom") throw Nope("妈妈已经去过了，在等爸爸来")
        if (state != "notice") throw Nope("现在没有家长会")
        val comment = meetingComment(s)
        val score = lastExamScore(s)
        m.put("state", "mom").put("momAt", now).put("comment", comment).put("grade", s.optString("grade"))
        if (score >= 0) m.put("score", score)
        add(s, "mood", MEETING_MOOD)
        care(s, MOM)
        addLog(s, MOM, "去开了家长会，老师说：$comment", now)
        var msg = "老师说：${comment}心情+${MEETING_MOOD.toInt()}"
        if (score >= MEETING_PRAISE_AT) {
            s.put("shells", s.optInt("shells") + MEETING_PRAISE_SHELLS)
            addLog(s, MOM, "老师表扬了，奖励了 $MEETING_PRAISE_SHELLS 贝壳", now)
            msg += "。老师表扬了，贝壳+$MEETING_PRAISE_SHELLS"
        }
        return msg + "。爸爸 ${MEETING_DAD_HOURS} 小时内也可以来"
    }

    private fun maybeSports(s: JSONObject, at: Long) {
        if (s.optJSONObject("sports") != null) return
        if (at - s.optLong("sportsClock", at) < SPORTS_EVERY_DAYS * MS_DAY) return
        if (Random.nextDouble() >= SPORTS_CHANCE) return
        val c = Calendar.getInstance().apply { timeInMillis = at }
        var n = 0
        while (n < SPORTS_AHEAD_WEEKDAYS) {
            c.add(Calendar.DAY_OF_MONTH, 1)
            if (isWeekday(c.timeInMillis)) n++
        }
        s.put("sports", JSONObject().put("day", dayKey(c.timeInMillis)).put("mom", false).put("dad", false))
        val label = dayLabel(c.timeInMillis)
        addLog(s, PET, "${label}要开运动会啦", at)
        addNote(s, "sportsNote", "${label}开运动会，那天记得送崽崽去上学")
    }

    /** 运动会那天 19:00 了还在，说明没去上学（上了学放学时已经结算掉） */
    private fun checkSports(s: JSONObject, now: Long) {
        val sp = s.optJSONObject("sports") ?: return
        val day = sp.optString("day")
        val end = dayAt(day, SCHOOL_END_HOUR)
        if (now < end) return
        s.remove("sports")
        s.put("lastSports", day)
        s.put("sportsClock", end)
        addLog(s, PET, "运动会没去成", end)
    }

    /** 放学回家时结算当天的运动会 */
    private fun settleSports(s: JSONObject, at: Long) {
        val sp = s.optJSONObject("sports") ?: return
        val day = dayKey(at)
        if (sp.optString("day") != day) return
        val mom = sp.optBoolean("mom")
        val dad = sp.optBoolean("dad")
        val parents = (if (mom) 1 else 0) + (if (dad) 1 else 0)
        val score = (
            d(s, "str") * SPORTS_STR_K + d(s, "energy") / SPORTS_ENERGY_DIV +
                Random.nextInt(SPORTS_RANDOM + 1) + SPORTS_PARENT_BONUS * parents
            ).roundToInt()
        val rank = when {
            score >= SPORTS_FIRST_AT -> 1
            score >= SPORTS_SECOND_AT -> 2
            score >= SPORTS_THIRD_AT -> 3
            else -> 0
        }
        val note: String
        when (rank) {
            1 -> {
                s.put("shells", s.optInt("shells") + SPORTS_FIRST_SHELLS)
                add(s, "mood", SPORTS_FIRST_MOOD)
                addLog(s, PET, "运动会横着跑 50 米拿了第一名！贝壳+$SPORTS_FIRST_SHELLS", at)
                note = "运动会横着跑 50 米第一名，贝壳+$SPORTS_FIRST_SHELLS"
            }
            2, 3 -> {
                val r = if (rank == 2) "第二名" else "第三名"
                s.put("shells", s.optInt("shells") + SPORTS_PLACE_SHELLS)
                add(s, "mood", SPORTS_PLACE_MOOD)
                addLog(s, PET, "运动会横着跑 50 米拿了${r}，贝壳+$SPORTS_PLACE_SHELLS", at)
                note = "运动会横着跑 50 米${r}，贝壳+$SPORTS_PLACE_SHELLS"
            }
            else -> {
                add(s, "mood", SPORTS_NO_PLACE_MOOD)
                addLog(s, PET, "跑完了，没拿名次，但是好开心", at)
                note = "运动会跑完了，没拿名次，但是很开心"
            }
        }
        s.put("str", d(s, "str") + SPORTS_STR)
        addExp(s, SPORTS_EXP)
        var full = "${note}，体能+$SPORTS_STR"
        if (mom && dad) {
            s.put("eq", d(s, "eq") + SPORTS_EQ)
            addLog(s, PET, "和爸爸妈妈一起跑了两人三脚", at)
            full += "，和爸爸妈妈跑了两人三脚，情商+$SPORTS_EQ"
        }
        addNote(s, "sportsNote", full)
        val list = s.optJSONArray("sportsHistory") ?: JSONArray().also { s.put("sportsHistory", it) }
        list.put(JSONObject().put("day", day).put("rank", rank).put("score", score).put("mom", mom).put("dad", dad))
        while (list.length() > SPORTS_KEEP) list.remove(0)
        s.remove("sports")
        s.put("lastSports", day)
        s.put("sportsClock", at)
    }

    /** 运动会当天 7:00–19:00 去看，妈妈爸爸各一次；崽崽得在学校 */
    private fun sports(s: JSONObject, who: String, now: Long): String {
        val sp = s.optJSONObject("sports") ?: throw Nope("最近没有运动会")
        val day = sp.optString("day")
        val label = dayLabel(dayAt(day, 12))
        if (dayKey(now) != day) throw Nope("运动会在${label}，当天 7:00–19:00 才能去看")
        val min = minuteOfDay(now)
        if (min < SCHOOL_OPEN_MIN || min >= SCHOOL_END_HOUR * 60) throw Nope("运动会 7:00–19:00 才能去看")
        val key = if (who == CLAUDE) "dad" else "mom"
        val whoName = if (who == CLAUDE) "爸爸" else "妈妈"
        if (sp.optBoolean(key)) throw Nope("${whoName}已经在运动会了，19:00 放学时出成绩")
        if (!isSchoolOut(s.optJSONObject("out"))) {
            throw Nope(
                if (min < SCHOOL_LATE_MIN) "崽崽还没去上学，先送它去学校才能参加运动会"
                else "崽崽今天没去上学，运动会去不成了"
            )
        }
        sp.put(key, true)
        care(s, who)
        addLog(s, who, "去学校看运动会，给崽崽加油", now)
        return "${whoName}到运动会了，19:00 放学时出成绩"
    }

    /** 考试：用放学回家这一刻的属性算分，及格升年级，不及格 schoolDays 设成 RETAKE_START_DAYS 等补考 */
    private fun takeExam(s: JSONObject, at: Long) {
        val g = gradeNumOf(s)
        val need = EXAM_NEED_BASE + EXAM_NEED_STEP * (g - 1)
        var raw = EXAM_BASE + (d(s, "int") - need) * EXAM_INT_K + Random.nextInt(-EXAM_RANDOM, EXAM_RANDOM + 1)
        if (d(s, "mood") >= EXAM_GOOD_MOOD_AT) raw += EXAM_GOOD_MOOD_BONUS
        if (d(s, "energy") < EXAM_LOW_ENERGY_AT) raw -= EXAM_LOW_ENERGY_PENALTY
        if (s.optBoolean("sick")) raw -= EXAM_SICK_PENALTY
        val score = raw.roundToInt().coerceIn(0, 100)
        val pass = score >= EXAM_PASS

        val exams = s.optJSONArray("exams") ?: JSONArray().also { s.put("exams", it) }
        exams.put(JSONObject().put("day", dayKey(at)).put("gradeNum", g).put("score", score).put("pass", pass))
        while (exams.length() > EXAM_KEEP) exams.remove(0)

        val note: String
        if (pass) {
            s.put("shells", s.optInt("shells") + EXAM_PASS_SHELLS)
            addExp(s, EXAM_PASS_EXP)
            s.put("retake", false)
            s.put("schoolDays", 0)
            val great = score >= EXAM_GREAT
            if (great) {
                s.put("shells", s.optInt("shells") + EXAM_GREAT_SHELLS)
                add(s, "mood", EXAM_GREAT_MOOD.toDouble())
            }
            val reward = if (great) EXAM_PASS_SHELLS + EXAM_GREAT_SHELLS else EXAM_PASS_SHELLS
            val head = if (great) "考了 $score 分，好开心" else "考了 $score 分，及格了"
            if (g >= GRADE_MAX) {
                s.put("graduated", true)
                syncGrade(s)
                addLog(s, PET, "$head，贝壳+$reward，经验+$EXAM_PASS_EXP", at)
                addLog(s, PET, "六年级毕业啦", at)
                note = "考了 $score 分，毕业啦"
            } else {
                s.put("gradeNum", g + 1)
                syncGrade(s)
                val up = "${GRADE_NUMS[g]}年级"
                addLog(s, PET, "$head，升到${up}，贝壳+$reward，经验+$EXAM_PASS_EXP", at)
                note = "考了 $score 分，升到${up}啦"
            }
        } else {
            add(s, "mood", -EXAM_FAIL_MOOD.toDouble())
            s.put("retake", true)
            s.put("schoolDays", RETAKE_START_DAYS)
            addLog(s, PET, "考了 $score 分，有点难过", at)
            note = "考了 $score 分，没及格，再上 ${EXAM_AT_DAYS - RETAKE_START_DAYS} 天学补考"
        }
        // 页面下次打开时弹一次 toast，弹完发 ackexam 清掉
        s.put("examNote", note)
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
                if (isCoaxTime(now)) {
                    // 21:00–02:00 按哄睡算：心情再加一点，日记里记谁哄的和一句睡前话
                    val line = coaxLines.random()
                    add(s, "mood", COAX_MOOD.toDouble())
                    addLog(s, who, "哄崽崽睡觉，$line", now)
                    "哄睡着了，$line"
                } else {
                    addLog(s, who, "哄崽崽睡觉", now)
                    "睡着了"
                }
            }

            "buyfood" -> {
                val bp = arg.split(Regex("[\\s:：×*]+"))
                val ing = findIngredient(bp[0]) ?: throw Nope("菜市场没有这个")
                val n = (bp.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, FOOD_BUY_MAX)
                val cost = ing.price * n
                val shells = s.optInt("shells")
                if (shells < cost) throw Nope("贝壳不够，要 $cost，现在只有 $shells")
                s.put("shells", shells - cost)
                fridgeAdd(s, ing.id, n)
                addLog(s, who, "买了${ing.name}×${n}放进冰箱", now)
                "买了${ing.name}×${n}放进冰箱，花了 $cost 贝壳"
            }

            "cook" -> {
                home()
                cook(s, arg, who, now)
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

            "homework" -> homework(s, who, now)

            "friend" -> visitFriend(s, arg, who, now)

            "meeting" -> meeting(s, who, now)

            "sports" -> sports(s, who, now)

            "acknotes" -> {
                s.remove("examNote")
                s.remove("friendNote")
                s.remove("meetingNote")
                s.remove("sportsNote")
                ""
            }

            "ackexam" -> {
                s.remove("examNote")
                ""
            }

            "ackfriend" -> {
                s.remove("friendNote")
                ""
            }

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

    private fun tasteList(s: JSONObject, key: String): List<String> {
        val a = s.optJSONObject("taste")?.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }
    }

    /** 从冰箱拿两样（可以两样一样），按配方出菜，直接喂给崽崽 */
    private fun cook(s: JSONObject, arg: String, who: String, now: Long): String {
        val keys = arg.split(Regex("[\\s+＋、,，]+")).filter { it.isNotEmpty() }
        if (keys.size != 2) throw Nope("要放两样食材（两样一样也行），比如：做饭 番茄 鸡蛋")
        val x = findIngredient(keys[0]) ?: throw Nope("没有「${keys[0]}」这种食材")
        val y = findIngredient(keys[1]) ?: throw Nope("没有「${keys[1]}」这种食材")
        val fridge = s.optJSONObject("fridge") ?: JSONObject()
        val need = if (x.id == y.id) 2 else 1
        if (fridge.optInt(x.id) < need) throw Nope("冰箱里${x.name}不够，先去菜市场买")
        if (fridge.optInt(y.id) < need) throw Nope("冰箱里${y.name}不够，先去菜市场买")
        if (d(s, "hunger") >= 95) throw Nope("崽崽吃不下啦，肚子圆滚滚的")

        val dish = dishes.firstOrNull { (it.a == x.id && it.b == y.id) || (it.a == y.id && it.b == x.id) }
        val dinner = isDinnerTime(now)
        val whoName = if (who == CLAUDE) "爸爸" else "妈妈"

        var hunger = DARK_HUNGER
        var mood = DARK_MOOD
        var energy = 0
        var health = 0
        var int = 0
        var str = 0
        var react = "dark"
        if (dish != null) {
            hunger = dish.hunger
            mood = dish.mood
            energy = dish.energy
            health = dish.health
            int = dish.int
            str = dish.str
            // 口味：两样都有按不爱吃算；不爱吃只是让这道菜的心情加成作废，不扣
            val hated = x.id in tasteList(s, "hate") || y.id in tasteList(s, "hate")
            val loved = !hated && (x.id in tasteList(s, "love") || y.id in tasteList(s, "love"))
            react = when {
                hated -> "unwilling"
                loved -> "love"
                else -> "normal"
            }
            if (hated) mood = 0
            if (loved) mood += LOVE_MOOD
        }
        if (dinner) mood += DINNER_MOOD

        fridgeAdd(s, x.id, -1)
        fridgeAdd(s, y.id, -1)
        add(s, "hunger", hunger.toDouble())
        add(s, "mood", mood.toDouble())
        add(s, "energy", energy.toDouble())
        s.put("health", (d(s, "health") + health).coerceIn(10.0, 100.0))
        s.put("int", d(s, "int") + int)
        s.put("str", d(s, "str") + str)
        checkCure(s, now)
        addExp(s, COOK_EXP)
        care(s, who)

        val name = dish?.name ?: "黑暗料理"
        val icon = dish?.icon ?: "🫕"
        val reactText = when (react) {
            "love" -> "吃得很香"
            "unwilling" -> "吃得不太情愿"
            "dark" -> "皱着脸吃完了"
            else -> "吃完了"
        }
        val effects = listOf(
            "饱食" to hunger, "心情" to mood, "体力" to energy, "健康" to health, "智力" to int, "体能" to str,
        ).filter { it.second != 0 }.map { "${it.first}${if (it.second > 0) "+" else ""}${it.second}" }

        // 日记：谁做的、做了什么、崽崽的反应；晚饭时间再记一起吃饭
        val sb = StringBuilder("做了")
        sb.append(if (dish != null) name else "一份黑暗料理（${x.name}+${y.name}）").append("喂崽崽")
        if (react != "normal") sb.append("，").append(reactText)
        if (dinner) sb.append("，和${whoName}一起吃了晚饭")
        addLog(s, who, sb.toString(), now)

        if (dish != null) {
            val rec = s.optJSONObject("recipes") ?: JSONObject().also { s.put("recipes", it) }
            val old = rec.optJSONObject(dish.id)
            rec.put(dish.id, JSONObject().put("n", (old?.optInt("n") ?: 0) + 1).put("react", react))
        } else {
            s.put("darkCount", s.optInt("darkCount") + 1)
        }
        levelUp(s, now)

        lastCook = JSONObject().put("name", name).put("icon", icon).put("dark", dish == null)
            .put("effects", JSONArray(effects)).put("react", react).put("reactText", reactText)
            .put("dinner", dinner)
        return "做了$name，崽崽$reactText：${effects.joinToString(" ")}" +
            if (dinner) "（晚饭时间，和${whoName}一起吃了晚饭）" else ""
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

    /** 陪写作业：智力+，心情-，体力-，算一次照顾；日记的署名就是陪的人（妈妈/爸爸） */
    private fun homework(s: JSONObject, who: String, now: Long): String {
        val day = dayKey(now)
        val min = minuteOfDay(now)
        val o = s.optJSONObject("out")
        if (isSchoolOut(o)) throw Nope("崽崽还在上学，放学回来才能写作业")
        if (o != null) throw Nope("崽崽出门了，还没回来")
        if (s.optString("schoolHome") != day) throw Nope("今天没有放学回来，没有作业")
        if (min < HOMEWORK_FROM_HOUR * 60 || min >= HOMEWORK_TO_HOUR * 60) {
            throw Nope("写作业的时间是 ${HOMEWORK_FROM_HOUR}:00 到 ${HOMEWORK_TO_HOUR}:00")
        }
        if (s.optString("homeworkDay") == day) throw Nope("今天的作业已经写完了")
        if (s.optBoolean("sleeping")) throw Nope("崽崽在睡觉，不能写作业")
        if (s.optBoolean("sick")) throw Nope("崽崽生病了，先好好休息，不写作业")
        if (d(s, "energy") < HOMEWORK_MIN_ENERGY) throw Nope("崽崽太累了，体力不够，写不动作业")
        s.put("homeworkDay", day)
        s.put("homeworkSince", s.optInt("homeworkSince") + 1)
        s.put("int", d(s, "int") + HOMEWORK_INT)
        add(s, "mood", HOMEWORK_MOOD.toDouble())
        add(s, "energy", HOMEWORK_ENERGY.toDouble())
        addExp(s, HOMEWORK_EXP)
        care(s, who)
        val whoName = if (who == CLAUDE) "爸爸" else "妈妈"
        addLog(s, who, "陪崽崽写作业，智力+$HOMEWORK_INT", now)
        levelUp(s, now)
        return "${whoName}陪崽崽写完了作业，智力+$HOMEWORK_INT，心情${HOMEWORK_MOOD}，体力${HOMEWORK_ENERGY}"
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
        val en = actEnergy(s, a)
        if (d(s, "energy") < en) throw Nope("体力不够，要 $en")
        if (d(s, "hunger") < 15) throw Nope("崽崽太饿了，先喂点东西")
        if (s.optInt("shells") < a.cost) throw Nope("贝壳不够，要 ${a.cost}")

        s.put("shells", s.optInt("shells") - a.cost)
        add(s, "energy", -en.toDouble())
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
