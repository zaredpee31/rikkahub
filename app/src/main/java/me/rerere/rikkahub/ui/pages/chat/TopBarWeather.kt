package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

// 顶栏天气：服务器每天早安脚本顺手写的 weather.json，只有数字和中文天气，没有地点
@Serializable
internal data class TopBarWeather(
    val date: String = "",
    val updated: String = "",
    val code: Int = 0,
    val text: String = "",
    val min: Int = 0,
    val max: Int = 0,
    val feels: Int = 0,
    val wind: Int = 0,
)

private const val WEATHER_URL = "https://43-166-23-196.sslip.io/weather.json"

private val weatherJson = Json { ignoreUnknownKeys = true }

// 进程内缓存：date 等于今天就不再请求。不轮询、不定时刷新
private object TopBarWeatherCache {
    @Volatile
    var value: TopBarWeather? = null
}

private fun fetchTopBarWeather(client: OkHttpClient): TopBarWeather? {
    val request = Request.Builder().url(WEATHER_URL).build()
    val shortClient = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    shortClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        return weatherJson.decodeFromString<TopBarWeather>(body)
    }
}

/** 顶栏显示时读一次天气。失败或没数据返回 null，调用处显示 --，不报错不弹提示 */
@Composable
internal fun rememberTopBarWeather(): TopBarWeather? {
    val client = koinInject<OkHttpClient>()
    var weather by remember { mutableStateOf(TopBarWeatherCache.value) }
    LaunchedEffect(Unit) {
        val today = LocalDate.now().toString()
        val cached = TopBarWeatherCache.value
        if (cached != null && cached.date == today) {
            weather = cached
            return@LaunchedEffect
        }
        val fetched = try {
            withContext(Dispatchers.IO) { fetchTopBarWeather(client) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fetched != null) {
            TopBarWeatherCache.value = fetched
            weather = fetched
        }
    }
    return weather
}
