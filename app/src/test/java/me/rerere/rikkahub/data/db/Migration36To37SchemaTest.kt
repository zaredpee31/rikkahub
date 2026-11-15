package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.migrations.Migration_36_37
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * 36 -> 37 只给 palace_node 加一列 last_event_at，并把旧记忆的值设成 created_at（老数据原样保留）。迁移执行后的列必须和 Room 导出的 37.json 一致，
 * 否则升级时 Room 校验失败、应用打不开。
 */
class Migration36To37SchemaTest {
    private val changed = setOf("palace_node")

    private fun entities(version: Int) =
        File("schemas/me.rerere.rikkahub.data.db.AppDatabase/$version.json").also {
            assertTrue("$version.json 不存在：${it.absolutePath}", it.isFile)
        }.let { file ->
            Json.parseToJsonElement(file.readText()).jsonObject["database"]!!
                .jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        }

    private fun name(e: JsonObject) = e["tableName"]!!.jsonPrimitive.content

    private fun columns(e: JsonObject): Map<String, String> = e["fields"]!!.jsonArray.associate {
        val f = it.jsonObject
        f["columnName"]!!.jsonPrimitive.content to
            (f["affinity"]!!.jsonPrimitive.content + (if (f["notNull"]?.jsonPrimitive?.content == "true") " NOT NULL" else "") +
                (f["defaultValue"]?.jsonPrimitive?.content?.let { d -> " DEFAULT $d" } ?: ""))
    }

    private fun run(): List<String> {
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
        Migration_36_37.migrate(db)
        return executed
    }

    @Test
    fun `added columns equal the difference between schema 36 and 37`() {
        val before = entities(36).filter { name(it) in changed }.associate { name(it) to columns(it) }
        val after = entities(37).filter { name(it) in changed }.associate { name(it) to columns(it) }
        val expected = after.flatMap { (table, cols) ->
            (cols - before.getValue(table).keys).map { (col, type) -> "$table.$col $type" }
        }.toSet()
        val re = Regex("""ALTER TABLE `(\w+)` ADD COLUMN `(\w+)` (.+)""")
        val actual = run().filter { it.startsWith("ALTER") }.map { sql -> re.matchEntire(sql.trim())!!.destructured.let { (t, c, d) -> "$t.$c $d" } }
        assertEquals(expected, actual.toSet())
        assertEquals(expected.size, actual.size)
        // 已有的列一个没动
        before.forEach { (table, cols) -> assertEquals(cols, after.getValue(table).filterKeys { it in cols }) }
    }

    @Test
    fun `other tables are unchanged`() {
        val before = entities(36).filter { name(it) !in changed }.associate { name(it) to it["createSql"] }
        val after = entities(37).filter { name(it) !in changed }.associate { name(it) to it["createSql"] }
        assertEquals(before, after)
    }

    @Test
    fun `old memories start with last_event_at equal to created_at`() {
        val sql = run()
        assertEquals(
            listOf(
                "ALTER TABLE `palace_node` ADD COLUMN `last_event_at` INTEGER NOT NULL DEFAULT 0",
                "UPDATE `palace_node` SET `last_event_at` = `created_at`",
            ),
            sql,
        )
    }
}
