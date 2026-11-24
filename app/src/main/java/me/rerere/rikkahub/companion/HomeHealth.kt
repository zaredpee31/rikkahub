package me.rerere.rikkahub.companion

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import me.rerere.rikkahub.R
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * 手环数据：华为运动健康 → Health Sync → Health Connect，这里每 15 分钟读最近 36 小时的睡眠、心率、血氧、步数，
 * 由 HomeLink 传到服务器 /companion/health。只读，不写 Health Connect。没授权就一天弹一次通知，点了去 HealthActivity 授权。
 */
object HomeHealth {
    private const val EVERY_MS = 15 * 60_000L
    private const val NOTIFY_ID = 7301
    const val BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    val READS = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )
    val PERMS = READS + BACKGROUND
    const val RATIONALE = "阿辞每 15 分钟读一次你的睡眠、心率、血氧和步数，传到家里的服务器，只读不改，用来看你睡得好不好。"
    private val STAGE = mapOf(
        SleepSessionRecord.STAGE_TYPE_DEEP to "deep",
        SleepSessionRecord.STAGE_TYPE_LIGHT to "light",
        SleepSessionRecord.STAGE_TYPE_REM to "rem",
        SleepSessionRecord.STAGE_TYPE_AWAKE to "awake",
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "awake",
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "awake",
        SleepSessionRecord.STAGE_TYPE_SLEEPING to "sleeping",
    )

    @Volatile private var lastAt = 0L
    @Volatile var status = "还没读过"
        private set

    fun syncSoon() { lastAt = 0L }

    /** 到点了就读一次，返回要传的 JSON；没到点、读不了就返回 null。 */
    suspend fun collect(c: Context): JsonObject? {
        if (System.currentTimeMillis() - lastAt < EVERY_MS) return null
        lastAt = System.currentTimeMillis()
        if (HealthConnectClient.getSdkStatus(c) != HealthConnectClient.SDK_AVAILABLE) {
            status = "Health Connect 用不了"; return null
        }
        val hc = HealthConnectClient.getOrCreate(c)
        val granted = hc.permissionController.getGrantedPermissions()
        if (!granted.containsAll(PERMS)) askOnce(c)
        if (READS.none { it in granted }) { status = "等她授权"; return null }

        val now = Instant.now()
        val from = now.minus(Duration.ofHours(36))
        val errors = mutableListOf<String>()
        suspend fun <T> tryRead(name: String, block: suspend () -> List<T>): List<T> =
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) {
                errors += "$name: ${e::class.simpleName}"; emptyList()
            }
        val sleep = tryRead("sleep") { readAll(hc, SleepSessionRecord::class, from, now) }
        val hr = tryRead("hr") { readAll(hc, HeartRateRecord::class, from, now) }
            .flatMap { it.samples }.sortedBy { it.time }.takeLast(4000)
        val spo2 = tryRead("spo2") { readAll(hc, OxygenSaturationRecord::class, from, now) }.sortedBy { it.time }
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.now(zone)
        val steps = linkedMapOf<String, Long>()
        for (d in listOf(today.minusDays(1), today)) {
            try {
                val start = d.atStartOfDay(zone).toInstant()
                val end = minOf(d.plusDays(1).atStartOfDay(zone).toInstant(), now)
                val r = hc.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, end)))
                steps[d.toString()] = r[StepsRecord.COUNT_TOTAL] ?: 0L
            } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "steps: ${e::class.simpleName}" }
        }
        status = "读到 睡眠${sleep.size} 心率${hr.size} 血氧${spo2.size}" + if (errors.isEmpty()) "" else " 出错 $errors"
        return buildJsonObject {
            put("read_at", now.epochSecond)
            put("background", BACKGROUND in granted)
            putJsonArray("errors") { errors.forEach { add(it) } }
            putJsonArray("sleep") {
                sleep.forEach { s ->
                    add(buildJsonObject {
                        put("start", s.startTime.epochSecond)
                        put("end", s.endTime.epochSecond)
                        putJsonObject("stages") {
                            s.stages.groupBy { STAGE[it.stage] ?: "unknown" }.forEach { (k, v) ->
                                put(k, v.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() })
                            }
                        }
                    })
                }
            }
            putJsonArray("hr") { hr.forEach { addJsonArray { add(it.time.epochSecond); add(it.beatsPerMinute) } } }
            putJsonArray("spo2") { spo2.forEach { addJsonArray { add(it.time.epochSecond); add(it.percentage.value) } } }
            putJsonObject("steps") { steps.forEach { (k, v) -> put(k, v) } }
        }
    }

    private suspend fun <T : Record> readAll(hc: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val r = hc.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageSize = 1000, pageToken = token))
            out += r.records
            token = r.pageToken
        } while (!token.isNullOrEmpty() && out.size < 20000)
        return out
    }

    /** 缺权限时一天最多弹一次，点了去授权。 */
    @SuppressLint("MissingPermission")
    private fun askOnce(c: Context) {
        val sp = c.getSharedPreferences("home_health", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong("asked_at", 0L) < 24 * 3600_000L) return
        sp.edit().putLong("asked_at", System.currentTimeMillis()).apply()
        val pi = PendingIntent.getActivity(
            c, NOTIFY_ID, Intent(c, HealthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, HomeNotify.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_clawd)
            .setContentTitle("阿辞想看你的手环数据")
            .setContentText("点这里授权睡眠、心率、血氧、步数")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(c).notify(NOTIFY_ID, n) } catch (e: Exception) { Log.w("HomeHealth", "notify failed", e) }
    }
}
