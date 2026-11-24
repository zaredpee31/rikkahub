package me.rerere.rikkahub.data.ai.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.ui.activity.LocationPermissionActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// 找附近的店（照 Oliscat《让 AI 找店不瞎编》）：调用这一下才读一次定位，坐标交给家里服务器，
// 服务器转 GCJ-02 调高德周边搜索（~/ac_proactive/places.py），回来的结果不含坐标
private const val MAX_PLACE_CALLS = 2

private fun placeErr(msg: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(JSONObject().put("error", msg).toString()))

private val placeClient by lazy { OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build() }

/** 每次回复新建一份，次数按一次回复算 */
fun createPlaceTools(context: Context): List<Tool> {
    val app = context.applicationContext
    val used = AtomicInteger(0)
    val tool = Tool(
        name = "nearby_places",
        description = """
            按她手机现在的定位找附近的店（高德周边搜索），按距离从近到远，带高德评分 rating、人均 avg_cost_yuan、今天营业时间 open_today、电话。每次回复最多用 $MAX_PLACE_CALLS 次。
            只有调用这一下才读一次定位，坐标不会给你。
            字段是 null 就是高德没数据：营业时间 null 要说不确定几点关门，别猜；评分是高德的，不是大众点评，别混称。
            没拿到定位或她不在国内会报错，这时问她在哪，不要拿以前的位置猜。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("keywords", buildJsonObject {
                        put("type", "string")
                        put("description", "找什么，比如 甜品、咖啡、火锅、奶茶")
                    })
                    put("radius_m", buildJsonObject {
                        put("type", "integer")
                        put("description", "搜多远，单位米，默认 3000，最大 50000")
                    })
                },
                required = listOf("keywords"),
            )
        },
        execute = { args -> findNearby(app, args, used) },
    )
    return listOf(tool)
}

private suspend fun findNearby(app: Context, args: JsonElement, used: AtomicInteger): List<UIMessagePart> {
    val obj = runCatching { args.jsonObject }.getOrNull()
    val words = obj?.get("keywords")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val radius = obj?.get("radius_m")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
    if (words.isEmpty()) return placeErr("没有关键词")
    if (used.incrementAndGet() > MAX_PLACE_CALLS) return placeErr("这次回复已经找了 $MAX_PLACE_CALLS 次，不能再找")
    if (!LocationGate.ensure(app)) {
        return placeErr("她没给 AC 定位权限，先问她现在在哪；想用的话让她在系统设置里给 AC 定位")
    }
    val loc = CurrentLocation.get(app) ?: return placeErr(
        if (CurrentLocation.enabled(app)) "这次没拿到定位（可能在室内），问她现在在哪，别拿以前的位置猜"
        else "她手机的定位开关关着，让她打开，或者问她现在在哪"
    )
    return withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = me.rerere.rikkahub.companion.HomeLink.connection()
            val body = JSONObject()
                .put("lat", loc.latitude)
                .put("lng", loc.longitude)
                .put("accuracy_m", if (loc.hasAccuracy()) loc.accuracy.toDouble() else JSONObject.NULL)
                .put("keywords", words)
            if (radius != null) body.put("radius_m", radius)
            val req = Request.Builder().url("$base/companion/places/around")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            placeClient.newCall(req).execute().use { r ->
                require(r.isSuccessful) { "服务器返回 ${r.code}" }
                listOf<UIMessagePart>(UIMessagePart.Text(r.body.string()))
            }
        }.getOrElse { placeErr("家里服务器没连上（${it.message ?: it.javaClass.simpleName}），这次别猜") }
    }
}

/** 没有定位权限时弹一个透明页去要，等她点完（最多 90 秒） */
internal object LocationGate {
    @Volatile
    var pending: CompletableDeferred<Boolean>? = null

    fun granted(c: Context) = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(c, it) == PackageManager.PERMISSION_GRANTED }

    suspend fun ensure(c: Context): Boolean {
        if (granted(c)) return true
        val d = CompletableDeferred<Boolean>()
        pending = d
        runCatching {
            withContext(Dispatchers.Main) {
                c.startActivity(Intent(c, LocationPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.onFailure { d.complete(false) }
        val ok = withTimeoutOrNull(90_000) { d.await() } ?: false
        pending = null
        return ok || granted(c)
    }
}

/** 两分钟内的旧定位直接用，没有就现要一次（fused / network / gps 谁先回用谁，最多等 20 秒） */
internal object CurrentLocation {
    fun enabled(c: Context): Boolean {
        val lm = c.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.getProviders(true).isNotEmpty()
    }

    @SuppressLint("MissingPermission")
    suspend fun get(c: Context): Location? = withContext(Dispatchers.Main) {
        val lm = c.getSystemService(LocationManager::class.java) ?: return@withContext null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time in 0..120_000 }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        if (recent != null || Build.VERSION.SDK_INT < 30) return@withContext recent
        val order = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { it in providers }
        if (order.isEmpty()) return@withContext null
        val d = CompletableDeferred<Location?>()
        var left = order.size
        val signals = order.map { p ->
            CancellationSignal().also { sig ->
                runCatching {
                    lm.getCurrentLocation(p, sig, ContextCompat.getMainExecutor(c)) { loc ->
                        if (loc != null) d.complete(loc) else if (--left == 0) d.complete(null)
                    }
                }.onFailure { if (--left == 0) d.complete(null) }
            }
        }
        try {
            withTimeoutOrNull(20_000) { d.await() }
        } finally {
            signals.forEach { it.cancel() }
        }
    }
}
