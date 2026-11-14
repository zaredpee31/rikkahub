package me.rerere.rikkahub.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.db.migrations.Migration_34_35
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * 34 -> 35 建出来的事件盒表，必须跟 Room 编译时导出的 35.json 一致，否则升级时 Room 校验失败、应用打不开。
 * 另外确认别的表在 34 和 35 之间一点没变。
 */
class Migration34To35SchemaTest {
    private val newTable = "palace_box"

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

    private fun name(entity: JsonObject) = entity["tableName"]!!.jsonPrimitive.content

    private fun sqlOf(entity: JsonObject): Set<String> =
        (listOf(entity["createSql"]!!.jsonPrimitive.content) +
            entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject["createSql"]!!.jsonPrimitive.content })
            .map { normalize(it.replace("\${TABLE_NAME}", name(entity))) }
            .toSet()

    @Test
    fun `migration matches exported schema 35`() {
        val target = entities(35).single { name(it) == newTable }

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
        Migration_34_35.migrate(db)

        val expected = sqlOf(target)
        assertEquals(expected, executed.map(::normalize).toSet())
        assertEquals(expected.size, executed.size)
    }

    @Test
    fun `other tables are unchanged`() {
        val before = entities(34).associate { name(it) to sqlOf(it) }
        val after = entities(35).filter { name(it) != newTable }.associate { name(it) to sqlOf(it) }
        assertEquals(before, after)
    }
}
