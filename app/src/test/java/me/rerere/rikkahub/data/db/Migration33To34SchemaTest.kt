package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.migrations.Migration_33_34
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * 33 -> 34 建出来的宫殿四张表，必须跟 Room 编译时导出的 34.json 一致，
 * 否则升级时 Room 校验失败、应用打不开。另外确认 34.json 里旧的三张记忆表跟 33.json 没变，
 * 也就是这次升级没有偷偷改旧表。
 */
class Migration33To34SchemaTest {
    private val newTables = setOf("palace_node", "palace_link", "palace_plate_entry", "palace_meta")
    private val oldTables = setOf("memory_file", "memory_node", "memory_plate", "memory_import", "memory_consolidation")

    private fun normalize(sql: String) = sql
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\( "), "(")
        .replace(Regex(" \\)"), ")")
        .trim()

    private fun entities(version: Int) =
        File("schemas/me.rerere.rikkahub.data.db.AppDatabase/$version.json").also {
            assertTrue("$version.json 不存在：${it.absolutePath}", it.isFile)
        }.let { file ->
            Json.parseToJsonElement(file.readText()).jsonObject["database"]!!
                .jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        }

    private fun sqlOf(entity: kotlinx.serialization.json.JsonObject): Set<String> {
        val name = entity["tableName"]!!.jsonPrimitive.content
        return (listOf(entity["createSql"]!!.jsonPrimitive.content) +
            entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content })
            .map { normalize(it.replace("\${TABLE_NAME}", name)) }
            .toSet()
    }

    @Test
    fun `migration matches exported schema 34`() {
        val target = entities(34).filter { it["tableName"]!!.jsonPrimitive.content in newTables }
        assertEquals(newTables, target.map { it["tableName"]!!.jsonPrimitive.content }.toSet())

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
        Migration_33_34.migrate(db)

        val expected = target.flatMap { sqlOf(it) }.toSet()
        assertEquals(expected, executed.map(::normalize).toSet())
        assertEquals(expected.size, executed.size)
    }

    @Test
    fun `old memory tables are unchanged`() {
        val before = entities(33).filter { it["tableName"]!!.jsonPrimitive.content in oldTables }
            .associate { it["tableName"]!!.jsonPrimitive.content to sqlOf(it) }
        val after = entities(34).filter { it["tableName"]!!.jsonPrimitive.content in oldTables }
            .associate { it["tableName"]!!.jsonPrimitive.content to sqlOf(it) }
        assertEquals(before, after)
    }
}
