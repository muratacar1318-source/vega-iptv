package com.tivimax.player.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * G-060: Room 2 → 3 geçişinin SQL'i, Room'un ürettiği `schemas/.../3.json` ile birebir aynı olmalı.
 * (MigrationTestHelper cihaz gerektirir; bu test JVM'de dizgi eşitliğini doğrular.)
 */
class AppDatabaseMigrationSqlTest {
    private val schemaFile: File = listOf(
        File("schemas/com.tivimax.player.data.local.AppDatabase/3.json"),
        File("app/schemas/com.tivimax.player.data.local.AppDatabase/3.json")
    ).first { it.exists() }

    private val database: JsonObject =
        JsonParser.parseString(schemaFile.readText(Charsets.UTF_8)).asJsonObject.getAsJsonObject("database")

    private fun entity(table: String): JsonObject =
        database.getAsJsonArray("entities").map { it.asJsonObject }.first { it.get("tableName").asString == table }

    private fun String.forTable(table: String) = replace("\${TABLE_NAME}", table)

    @Test
    fun `sema surumu 3`() {
        assertEquals(3, database.get("version").asInt)
    }

    @Test
    fun `epg_programs tablo SQL'i 3 json ile ayni`() {
        val expected = entity("epg_programs").get("createSql").asString.forTable("epg_programs")
        assertEquals(expected, AppDatabaseMigrations.MIGRATION_2_3_SQL[0])
        assertEquals(expected, AppDatabaseMigrations.CREATE_EPG_PROGRAMS)
    }

    @Test
    fun `epg_programs indeks SQL'i 3 json ile ayni`() {
        val index = entity("epg_programs").getAsJsonArray("indices")[0].asJsonObject
        assertEquals("index_epg_programs_channelEpgId_startMs", index.get("name").asString)
        assertEquals(index.get("createSql").asString.forTable("epg_programs"), AppDatabaseMigrations.MIGRATION_2_3_SQL[1])
    }

    @Test
    fun `live_streams yeni sutun tanimi 3 json ile ayni ve sutun sonda`() {
        val liveSql = entity("live_streams").get("createSql").asString.forTable("live_streams")
        val alter = AppDatabaseMigrations.MIGRATION_2_3_SQL[2]
        assertTrue(alter.startsWith("ALTER TABLE `live_streams` ADD COLUMN "))
        val columnDef = alter.removePrefix("ALTER TABLE `live_streams` ADD COLUMN ")
        assertEquals("`tvArchiveDuration` INTEGER NOT NULL DEFAULT 0", columnDef)
        // Yeni sütun tablonun son sütunudur (ALTER TABLE ADD COLUMN sona ekler) ve tanımı birebir aynıdır
        assertTrue(liveSql.contains("`directSource` TEXT, $columnDef, PRIMARY KEY(`streamId`))"))
    }

    @Test
    fun `gecis 2 surumunde olmayan tek sey bu uc ifadedir`() {
        // v2 live_streams: yeni sütun çıkarılınca eski tanım kalır (veri silen DROP/CREATE yok)
        assertEquals(3, AppDatabaseMigrations.MIGRATION_2_3_SQL.size)
        assertTrue(AppDatabaseMigrations.MIGRATION_2_3_SQL.none { it.contains("DROP", ignoreCase = true) })
        assertTrue(AppDatabaseMigrations.MIGRATION_2_3_SQL.none { it.contains("DELETE", ignoreCase = true) })
        val v2LiveSql = entity("live_streams").get("createSql").asString.forTable("live_streams")
            .replace(", `tvArchiveDuration` INTEGER NOT NULL DEFAULT 0", "")
        assertTrue(v2LiveSql.endsWith("`directSource` TEXT, PRIMARY KEY(`streamId`))"))
    }
}
