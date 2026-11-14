package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.migrations.Migration_32_33
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * 32 -> 33 迁移建出来的三张表，必须跟 Room 编译时导出的 33.json 完全一致，
 * 否则装到手机上升级时 Room 校验失败、应用打不开。
 * 没有真机也没有模拟器，这里截下迁移执行的 SQL，跟 schema 里的建表、建索引语句逐条比。
 */
class Migration32To33SchemaTest {
    private val tables = setOf("memory_node", "memory_plate", "memory_import")

    private fun normalize(sql: String) = sql
        .replace("IF NOT EXISTS ", "")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\( "), "(")
        .replace(Regex(" \\)"), ")")
        .trim()

    @Test
    fun `migration matches exported schema 33`() {
        val schema = File("schemas/me.rerere.rikkahub.data.db.AppDatabase/33.json")
        assertTrue("33.json 不存在：${schema.absolutePath}", schema.isFile)
        val entities = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!
            .jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
            .filter { it["tableName"]!!.jsonPrimitive.content in tables }
        assertEquals(tables, entities.map { it["tableName"]!!.jsonPrimitive.content }.toSet())
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
        Migration_32_33.migrate(db)

        // schema 里的 ${TABLE_NAME} 是占位符，换成实际表名再比
        val expectedWithNames = entities.flatMap { entity ->
            val name = entity["tableName"]!!.jsonPrimitive.content
            (listOf(entity["createSql"]!!.jsonPrimitive.content) +
                entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content })
                .map { normalize(it.replace("\${TABLE_NAME}", name)) }
        }.toSet()
        assertEquals(expectedWithNames, executed.map(::normalize).toSet())
        assertEquals(expectedWithNames.size, executed.size)
    }
}
