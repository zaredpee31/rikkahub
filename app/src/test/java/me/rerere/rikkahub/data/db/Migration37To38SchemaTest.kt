package me.rerere.rikkahub.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.migrations.Migration_33_38
import me.rerere.rikkahub.data.db.migrations.Migration_34_38
import me.rerere.rikkahub.data.db.migrations.Migration_35_38
import me.rerere.rikkahub.data.db.migrations.Migration_36_38
import me.rerere.rikkahub.data.db.migrations.Migration_37_38
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * 37 -> 38 只删宫殿自己的 5 张表。普通对话的表、memory_plate（「关于小葵」「关于你」）一个字都不能碰。
 * 迁移执行后剩下的表必须和 Room 导出的 38.json 一致，否则升级时 Room 校验失败、应用打不开。
 */
class Migration37To38SchemaTest {
    private val palaceTables = setOf("palace_node", "palace_link", "palace_plate_entry", "palace_meta", "palace_box")

    private fun entities(version: Int) =
        File("schemas/me.rerere.rikkahub.data.db.AppDatabase/$version.json").also {
            assertTrue("$version.json 不存在：${it.absolutePath}", it.isFile)
        }.let { file ->
            Json.parseToJsonElement(file.readText()).jsonObject["database"]!!
                .jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        }

    private fun name(e: JsonObject) = e["tableName"]!!.jsonPrimitive.content

    private fun run(migration: Migration): List<String> {
        val executed = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> { executed += args!![0] as String; null }
                "toString" -> "FakeDb"
                "hashCode" -> 0
                "equals" -> false
                else -> error("迁移不该调用 ${method.name}")
            }
        } as SupportSQLiteDatabase
        migration.migrate(db)
        return executed
    }

    @Test
    fun `drops exactly the five palace tables and nothing else`() {
        val sql = run(Migration_37_38)
        val re = Regex("""DROP TABLE IF EXISTS `(\w+)`""")
        assertEquals("每条 SQL 都必须是 DROP TABLE IF EXISTS", sql.size, sql.count { re.matchEntire(it) != null })
        assertEquals(palaceTables, sql.map { re.matchEntire(it)!!.groupValues[1] }.toSet())
        assertEquals(5, sql.size)
        // palace_node 最后删
        assertEquals("DROP TABLE IF EXISTS `palace_node`", sql.last())
    }

    @Test
    fun `never touches memory_plate or conversation tables`() {
        val protectedTables = listOf(
            "memory_plate", "memory_file", "memory_node", "memory_import", "memory_consolidation",
            "ConversationEntity", "message_node", "GenMediaEntity", "favorites", "workspaces",
        )
        listOf(Migration_33_38, Migration_34_38, Migration_35_38, Migration_36_38, Migration_37_38).forEach { m ->
            val joined = run(m).joinToString("\n")
            protectedTables.forEach { assertFalse("${m.startVersion}->38 碰了 $it", joined.contains(it)) }
            assertFalse(joined.contains("DELETE", ignoreCase = true))
            assertFalse(joined.contains("UPDATE", ignoreCase = true))
        }
    }

    @Test
    fun `migrations cover every version from 33 to 37 and end at 38`() {
        val all = listOf(Migration_33_38, Migration_34_38, Migration_35_38, Migration_36_38, Migration_37_38)
        assertEquals(listOf(33, 34, 35, 36, 37), all.map { it.startVersion })
        assertTrue(all.all { it.endVersion == 38 })
        // 每条都是同样的 5 句
        val expected = run(Migration_37_38)
        all.forEach { assertEquals(expected, run(it)) }
    }

    @Test
    fun `schema 38 is schema 37 without the palace tables`() {
        val before = entities(37)
        val after = entities(38)
        assertEquals(palaceTables, before.map(::name).toSet() - after.map(::name).toSet())
        assertTrue(after.map(::name).none { it in palaceTables })
        // 其余的表建表语句一个字没变
        val keep = before.filter { name(it) !in palaceTables }.associate { name(it) to it["createSql"] }
        assertEquals(keep, after.associate { name(it) to it["createSql"] })
    }

    @Test
    fun `memory_plate is still in schema 38 with the same columns`() {
        val plate37 = entities(37).first { name(it) == "memory_plate" }
        val plate38 = entities(38).first { name(it) == "memory_plate" }
        assertEquals(plate37, plate38)
        val columns = plate38["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertTrue(columns.containsAll(listOf("assistant_id", "kind", "content")))
    }
}
