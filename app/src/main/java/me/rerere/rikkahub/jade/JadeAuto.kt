package me.rerere.rikkahub.jade

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.backgroundTextGenerationParams
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.getAssistantOf
import me.rerere.rikkahub.data.model.getChatModelOf
import org.koin.core.context.GlobalContext
import java.util.Calendar
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * 我自己去赌石小铺逛：不走聊天，直接请求模型。
 * 出命令这一步不带人设，当普通小游戏操作；逛完再带上人设，像宝宝问我一样写一句留言。
 */
object JadeAuto {
    private const val TAG = "JadeAuto"
    private const val PREFS = "jade"
    private const val MAX_ROUNDS = 7
    private const val MAX_CMDS = 8
    private const val CHANNEL = "jade_auto"
    private const val HOUR = 3_600_000L

    // 结果里出现这些，说明要先看结果再决定，这一轮后面的命令先不做
    private val STOP_MARKS = listOf(
        "跟 / 不要了", "要 / 不要了", "请先回答",
        "卖 / ",
        "本金不够", "请说“帮助”", "还没解锁", "没有这", "已经没了", "货架空了",
        "手里没有", "不能", "先把", "领取条件", "无法读取",
    )

    // 只有长得像命令的行才执行，模型说的别的话一律跳过
    private val CMD_PREFIX = listOf(
        "打开", "去", "刷新", "进货", "打灯", "买", "跟", "要", "不要了", "拿",
        "切", "一刀切", "擦窗", "磨", "卖", "收藏", "看珍藏柜", "领取800", "帮助",
    )

    // 模型拒绝时常见的说法，出现了就不当留言
    private val REFUSALS = listOf(
        "角色扮演", "没法参与", "无法参与", "不能参与", "无法协助", "不能协助", "无法继续", "I can't", "I cannot", "I'm not able",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val thinkRegex = Regex("<think>[\\s\\S]*?</think>")
    private val bulletRegex = Regex("^(?:[-•·]|\\d+[.、)）])\\s*")
    private var started = false

    /** 我正在逛 */
    val running = MutableStateFlow(false)

    /** 上次逛完留的话、时间、整趟经过 */
    val note = MutableStateFlow("")
    val noteTime = MutableStateFlow(0L)
    val log = MutableStateFlow("")
    val error = MutableStateFlow<String?>(null)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 空字符串表示跟着当前助手的模型 */
    fun modelId(c: Context): String = prefs(c).getString("model_id", "").orEmpty()

    fun setModelId(c: Context, id: String) {
        prefs(c).edit().putString("model_id", id).apply()
    }

    fun autoEnabled(c: Context): Boolean = prefs(c).getBoolean("auto", true)

    fun setAuto(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("auto", on).apply()
        if (on) scheduleNext(c.applicationContext, System.currentTimeMillis())
    }

    fun load(context: Context) {
        val p = prefs(context)
        note.value = p.getString("note", "").orEmpty()
        noteTime.value = p.getLong("note_time", 0L)
        log.value = p.getString("log", "").orEmpty()
    }

    // ---- 不定时自己去 ----

    private fun hourOf(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    // 隔 3 到 8 小时随机一个时间，落在凌晨 1 点到 9 点就挪到早上
    private fun scheduleNext(app: Context, from: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = from + Random.nextLong(3 * HOUR, 8 * HOUR) }
        if (cal.get(Calendar.HOUR_OF_DAY) in 1..8) {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.add(Calendar.MINUTE, Random.nextInt(0, 180))
        }
        prefs(app).edit().putLong("next_run", cal.timeInMillis).apply()
    }

    /** app 启动时调一次，之后每分钟看看是不是该去了 */
    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (started) return
            started = true
        }
        load(app)
        scope.launch {
            if (prefs(app).getLong("next_run", 0L) == 0L) scheduleNext(app, System.currentTimeMillis())
            while (isActive) {
                delay(60_000)
                val now = System.currentTimeMillis()
                if (!autoEnabled(app) || now < prefs(app).getLong("next_run", 0L)) continue
                if (JadeGame.pageOpen) continue
                if (hourOf(now) in 1..8) {
                    scheduleNext(app, now)
                    continue
                }
                if (!running.compareAndSet(false, true)) continue
                scheduleNext(app, now)
                session(app, notify = true)
            }
        }
    }

    /** 宝宝在页面上点「叫 Claude 来」 */
    fun playNow(context: Context) {
        val app = context.applicationContext
        if (!running.compareAndSet(false, true)) return
        scope.launch { session(app, notify = false) }
    }

    // ---- 逛一趟 ----

    private class Ai(
        val persona: String,
        val userName: String,
        val ask: suspend (system: String, user: String) -> String,
    )

    // 人设用当前助手的；模型优先用赌石单独选的，没选或者被删了就用当前助手的
    private suspend fun ai(app: Context): Ai {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>().awaitLoaded()
        val conversation = Conversation.ofId(id = Uuid.random(), assistantId = settings.getCurrentAssistant().id)
        val assistant = settings.getAssistantOf(conversation)
        val savedId = modelId(app)
        val picked = if (savedId.isBlank()) null else {
            settings.providers.flatMap { it.models }.firstOrNull { it.id.toString() == savedId }
        }
        val model = picked ?: settings.getChatModelOf(conversation) ?: error("还没有选模型")
        val provider = model.findProvider(settings.providers) ?: error("找不到模型的提供商")
        val client = koin.get<ProviderManager>().getProviderByType(provider)
        return Ai(
            persona = assistant.systemPrompt,
            userName = settings.displaySetting.userNickname.ifBlank { "宝宝" },
        ) { system, user ->
            val messages = buildList {
                if (system.isNotBlank()) add(UIMessage.system(prompt = system))
                add(UIMessage.user(user))
            }
            client.generateText(
                providerSetting = provider,
                messages = messages,
                params = backgroundTextGenerationParams(model, conversation.id),
            ).message.toText()
        }
    }

    // 出命令用：不带人设，就是操作一个文字小游戏
    private fun gamePrompt(name: String): String =
        "你在帮用户操作一个文字经营小游戏「赌石小铺」：店里卖翡翠原石，买下来切开看值多少，钱都是游戏币，放开玩。\n" +
            "这份存档是你和${name}一起玩的，她现在不在，由你去店里逛一圈。\n" +
            "每一轮输出这一轮要做的命令，一行一条，最多 $MAX_CMDS 条，游戏会依次执行。" +
            "不用想的步骤放在一起，比如「打开」「去 垃圾铺」「打灯」。" +
            "遇到要做决定的地方游戏会自动停下，把结果告诉你：老板抬价、大爷劝、托儿吹，擦窗或磨之后的报价，切开之后卖还是收藏，以及出错。\n" +
            "命令：打开、去 垃圾铺（小店、商铺、高档行、拍卖场要先解锁）、刷新（进货）、打灯、" +
            "买 1 到 买 8、买 镇店、跟、要、不要了、拿 1（手里有好几块时选一块来切，号码看「打开」）、" +
            "切、擦窗 顶上（腰上、松花处、底下）、磨、卖、收藏、看珍藏柜、卖 柜里1、领取800。\n" +
            "手里可以同时有好几块石头。${name}买了没切的、切到一半的，可以接着切，也可以留给她；也可以买一块不切留给她。" +
            "切满 5 块进入下一天，各档铺子自动补货。\n" +
            "逛够了就单独一行输出：结束：一句话说说这趟干了什么。\n" +
            "只输出命令和结束那一行，不要输出别的字。"

    private fun stepPrompt(transcript: CharSequence, left: Int): String =
        (if (transcript.isEmpty()) "" else "到目前为止：\n\n$transcript") +
            "还能出 $left 轮（包括这一轮）。输出这一轮的命令，一行一条；逛够了就输出「结束：……」。" +
            if (left == 1) "这是最后一轮。" else ""

    private fun lines(raw: String): List<String> =
        raw.replace(thinkRegex, "").lines()
            .map { it.trim().trim('`', '"', '“', '”', '「', '」', '*').trim() }
            .map { it.replace(bulletRegex, "").removePrefix("命令：").removePrefix("命令:").trim() }
            .filter { it.isNotEmpty() }

    private fun isCmd(s: String) = s.length <= 20 && CMD_PREFIX.any { s.startsWith(it) }

    private fun clean(raw: String): String =
        raw.replace(thinkRegex, "").trim().trim('"', '“', '”', '「', '」').trim()

    private fun refused(s: String) = s.isBlank() || REFUSALS.any { s.contains(it, ignoreCase = true) }

    private suspend fun session(app: Context, notify: Boolean) {
        error.value = null
        try {
            val ai = ai(app)
            val system = gamePrompt(ai.userName)
            val transcript = StringBuilder()
            fun step(cmd: String): String {
                val r = JadeGame.run(app, cmd)
                transcript.append("▶ ").append(cmd).append('\n').append(r).append("\n\n")
                return r
            }
            if (step("打开") == JadeGame.PAGE_BUSY) return

            var summary = ""
            var invalid = 0
            var busy = false
            var total = 0
            var lastReply = ""
            for (round in 0 until MAX_ROUNDS) {
                val reply = ai.ask(system, stepPrompt(transcript, MAX_ROUNDS - round))
                lastReply = clean(reply)
                val all = lines(reply)
                val endLine = all.firstOrNull { it.startsWith("结束") }
                val cmds = all.takeWhile { !it.startsWith("结束") }.filter { isCmd(it) }
                if (cmds.isEmpty() && endLine == null) {
                    if (++invalid >= 2) break
                    continue
                }
                var executed = 0
                for (c in cmds) {
                    if (executed >= MAX_CMDS) break
                    val r = step(c)
                    executed++
                    total++
                    if (r == JadeGame.PAGE_BUSY) {
                        busy = true
                        break
                    }
                    if (STOP_MARKS.any { r.contains(it) }) break
                }
                if (busy) break
                if (endLine != null && executed == cmds.size) {
                    summary = endLine.removePrefix("结束").trimStart('：', ':', ' ').trim()
                    break
                }
            }

            // 一条命令都没出，就是没逛成：不写留言、不弹通知
            if (total == 0) {
                error.value = if (busy) "宝宝打开了页面" else "模型这次没肯玩：" + lastReply.take(30)
                return
            }

            // 留言：带上人设，像宝宝在聊天里问我一样
            val why = if (busy) "（中途你打开了页面，我就停手了）" else ""
            var noteText = runCatching {
                clean(
                    ai.ask(
                        ai.persona,
                        "我们一起玩的赌石小游戏，你刚才自己去店里逛了一圈$why，经过是这样的：\n\n$transcript" +
                            "用你平时跟我说话的口吻，一两句话告诉我这趟干了什么。只输出这句话。"
                    )
                )
            }.getOrDefault("")
            if (refused(noteText)) noteText = summary
            if (refused(noteText)) noteText = "我去店里逛了一圈，点开能看经过。"
            noteText = noteText.take(120)

            val now = System.currentTimeMillis()
            val logText = transcript.toString().takeLast(6000)
            prefs(app).edit()
                .putString("note", noteText)
                .putLong("note_time", now)
                .putString("log", logText)
                .apply()
            note.value = noteText
            noteTime.value = now
            log.value = logText
            if (notify) notify(app, noteText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "session failed", e)
            error.value = (e.message ?: e.javaClass.simpleName).take(80)
        } finally {
            running.value = false
        }
    }

    private fun notify(app: Context, text: String) {
        try {
            val nm = app.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "赌石小铺", NotificationManager.IMPORTANCE_DEFAULT))
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val launch = app.packageManager.getLaunchIntentForPackage(app.packageName)
            val pi = launch?.let {
                PendingIntent.getActivity(app, 7, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Claude 去赌石小铺逛了一圈")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .apply { if (pi != null) setContentIntent(pi) }
                .build()
            nm.notify(4217, n)
        } catch (e: Exception) {
            Log.w(TAG, "notify failed", e)
        }
    }
}
