package me.rerere.rikkahub.data.datastore

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.runBlocking
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.data.datastore.migration.PreferenceStoreV6Migration
import me.rerere.rikkahub.data.datastore.migration.keepSiliconFlowAsrProviders
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreferenceStoreV6MigrationTest {
    private val old = """[
        {"type":"openai_realtime","id":"00000000-0000-0000-0000-000000000001","name":"a","apiKey":"k"},
        {"type":"mimo","id":"00000000-0000-0000-0000-000000000002","name":"b"},
        {"type":"siliconflow","id":"00000000-0000-0000-0000-000000000003","name":"c","apiKey":"sk"}
    ]"""

    @Test
    fun `only siliconflow providers survive and still decode`() {
        val kept = JsonInstant.decodeFromString<List<ASRProviderSetting>>(keepSiliconFlowAsrProviders(old))

        assertEquals(1, kept.size)
        assertEquals("sk", (kept.single() as ASRProviderSetting.SiliconFlow).apiKey)
    }

    @Test
    fun `garbage or empty input becomes an empty list`() {
        assertEquals("[]", keepSiliconFlowAsrProviders("not json"))
        assertEquals("[]", keepSiliconFlowAsrProviders("[]"))
    }

    @Test
    fun `migration clears a selection that points at a removed provider`() = runBlocking {
        val prefs = mutablePreferencesOf(
            SettingsStore.ASR_PROVIDERS to old,
            SettingsStore.SELECTED_ASR_PROVIDER to "00000000-0000-0000-0000-000000000001",
            SettingsStore.VERSION to 5,
        )

        val migrated = PreferenceStoreV6Migration().migrate(prefs)

        assertFalse(SettingsStore.SELECTED_ASR_PROVIDER in migrated)
        assertEquals(6, migrated[SettingsStore.VERSION])
    }

    @Test
    fun `migration keeps a selection that points at siliconflow`() = runBlocking {
        val id = "00000000-0000-0000-0000-000000000003"
        val prefs = mutablePreferencesOf(
            SettingsStore.ASR_PROVIDERS to old,
            SettingsStore.SELECTED_ASR_PROVIDER to id,
            SettingsStore.VERSION to 5,
        )

        assertEquals(id, PreferenceStoreV6Migration().migrate(prefs)[SettingsStore.SELECTED_ASR_PROVIDER])
    }
}
