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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// 叫滴滴：查价时读一次定位当起点，交给家里服务器（~/ac_proactive/didi.py）用高德搜起终点、调滴滴 MCP。
// 下单的关卡在服务器：她最新一句必须是「可以叫车」，查价之后、5 分钟内，一句只叫一次
private const val MAX_TAXI_CALLS = 4

private fun taxiErr(msg: String): List<UIMessagePart> =
    listOf(UIMessagePart.Text(JSONObject().put("error", msg).toString()))

private val taxiClient by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }

private fun strProp(desc: String) = buildJsonObject {
    put("type", "string")
    put("description", desc)
}

/** 每次回复新建一份，次数按一次回复算 */
fun createDidiTools(context: Context): List<Tool> {
    val app = context.applicationContext
    val used = AtomicInteger(0)
    val tool = Tool(
        name = "taxi",
        description = """
            帮她叫滴滴。action：estimate 查价；order 下单；status 查订单、司机离上车点多远；cancel 取消。每次回复最多用 $MAX_TAXI_CALLS 次。
            下单规矩：先 estimate，把起点、终点、各车型价格发给她，让她挑车型；她回「可以叫车」之后才 order，product_category 填她挑的那个。
            服务器会核对她最新一句：不是「可以叫车」、在查价之前说的、超过 5 分钟、这句已经叫过车、她说的车型和你传的不一样，都会拒绝。被拒就照报错跟她说，别绕开。
            终点搜错了换 pick 或换关键词重新 estimate。下单报断线时先 status 查，别再下单。cancel 只在她说要取消时用，司机接单后取消可能要付费。
            返回 mode 是「调试」时是假数据，不会真叫车。
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", strProp("estimate / order / status / cancel"))
                    put("to", strProp("estimate 用：目的地关键词，比如 公司名、商场、地铁站"))
                    put("from", strProp("estimate 用：起点关键词，不填就用她手机现在的定位"))
                    put("pick", buildJsonObject {
                        put("type", "integer")
                        put("description", "estimate 用：终点用第几个搜到的结果，默认 0；上次返回的 other_destinations 里有编号")
                    })
                    put("product_category", strProp("order 用：她挑的车型代码，从 estimate 的 options 里拿"))
                    put("order_id", strProp("status / cancel 用：不填就是刚叫的那单"))
                    put("reason", strProp("cancel 用：取消原因，可不填"))
                },
                required = listOf("action"),
            )
        },
        execute = { args -> callTaxi(app, args, used) },
    )
    return listOf(tool)
}

private suspend fun callTaxi(app: Context, args: JsonElement, used: AtomicInteger): List<UIMessagePart> {
    val obj = runCatching { args.jsonObject }.getOrNull() ?: return taxiErr("参数不对")
    fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    val action = s("action")
    if (action !in setOf("estimate", "order", "status", "cancel")) return taxiErr("action 只能是 estimate / order / status / cancel")
    if (used.incrementAndGet() > MAX_TAXI_CALLS) return taxiErr("这次回复已经用了 $MAX_TAXI_CALLS 次，停下来跟她说")
    val body = JSONObject().put("action", action)
    for (k in listOf("to", "from", "product_category", "order_id", "reason")) {
        val v = s(k)
        if (v.isNotEmpty()) body.put(k, v)
    }
    s("pick").toDoubleOrNull()?.toInt()?.let { body.put("pick", it) }
    if (action == "estimate") {
        if (s("to").isEmpty()) return taxiErr("没有目的地，问她去哪")
        val hasFrom = s("from").isNotEmpty()
        val ok = if (hasFrom) LocationGate.granted(app) else LocationGate.ensure(app)
        if (!ok && !hasFrom) return taxiErr("她没给 AC 定位权限，问她从哪出发，用 from 传进来")
        val loc = if (ok) CurrentLocation.get(app) else null
        if (loc == null && !hasFrom) return taxiErr("这次没拿到定位，问她从哪出发，用 from 传进来，别拿以前的位置猜")
        if (loc != null) {
            body.put("lat", loc.latitude).put("lng", loc.longitude)
            if (loc.hasAccuracy()) body.put("accuracy_m", loc.accuracy.toDouble())
        }
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val (base, token) = me.rerere.rikkahub.companion.HomeLink.connection()
            val req = Request.Builder().url("$base/companion/didi")
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            taxiClient.newCall(req).execute().use { r ->
                require(r.isSuccessful) { "服务器返回 ${r.code}" }
                listOf<UIMessagePart>(UIMessagePart.Text(r.body.string()))
            }
        }.getOrElse {
            taxiErr(
                if (action == "order") "家里服务器没回话（${it.message ?: it.javaClass.simpleName}），不知道叫没叫上，先 status 查，别再下单"
                else "家里服务器没连上（${it.message ?: it.javaClass.simpleName}），这次别猜"
            )
        }
    }
}
