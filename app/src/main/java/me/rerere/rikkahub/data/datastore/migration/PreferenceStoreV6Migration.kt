package me.rerere.rikkahub.data.datastore.migration

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

/** 语音识别只保留硅基流动，旧的五家配置在解码前丢掉，否则整份设置读不出来。 */
class PreferenceStoreV6Migration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[SettingsStore.VERSION]
        return version == null || version < 6
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()

        prefs[SettingsStore.ASR_PROVIDERS]?.let { json ->
            val kept = keepSiliconFlowAsrProviders(json)
            prefs[SettingsStore.ASR_PROVIDERS] = kept
            // 选中的识别服务如果已被删掉，选择也清空，运行时会回退到第一个
            val remaining = (JsonInstant.parseToJsonElement(kept) as JsonArray)
                .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitiveOrNull?.contentOrNull }
            prefs[SettingsStore.SELECTED_ASR_PROVIDER]?.let { selected ->
                if (selected !in remaining) prefs.remove(SettingsStore.SELECTED_ASR_PROVIDER)
            }
        }

        prefs[SettingsStore.VERSION] = 6
        return prefs.toPreferences()
    }

    override suspend fun cleanUp() {}
}

internal fun keepSiliconFlowAsrProviders(asrProvidersJson: String): String {
    val root = runCatching { JsonInstant.parseToJsonElement(asrProvidersJson) as? JsonArray }.getOrNull()
        ?: return "[]"
    val kept = root.filter {
        (it as? JsonObject)?.get("type")?.jsonPrimitiveOrNull?.contentOrNull == "siliconflow"
    }
    return JsonInstant.encodeToString(JsonArray(kept))
}
