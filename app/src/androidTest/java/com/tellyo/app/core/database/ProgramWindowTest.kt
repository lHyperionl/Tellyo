package com.tellyo.app.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramWindowTest {
    @Test
    fun sourcePrioritySurvivesDatabaseReopenAndProtectsAgainstLowerPriorityUpdate() = runBlocking {
        val name = "priority-${System.nanoTime()}.db"
        var database = Room.databaseBuilder(context, TellyoDatabase::class.java, name).build()
        try {
            database.programDao().applyUpdate(listOf(program("authorized", "a", 100, 200)), null, -1, sourcePriority = 100)
            database.close()
            database = Room.databaseBuilder(context, TellyoDatabase::class.java, name).build()
            database.programDao().applyUpdate(listOf(program("built-in", "a", 110, 210),
                program("future", "a", 300, 400)), null, -1, sourcePriority = 10)
            val records = database.programDao().observeWindow("a", 0, 500).first()
            assertEquals(listOf("authorized", "future"), records.map { it.id })
            assertEquals(100, records.first().sourcePriority)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun halfOpenWindowExcludesBoundaryOnlyProgrammesAndPartialUpdatesPreserveFuture() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, TellyoDatabase::class.java).build()
        try {
            val dao = database.programDao()
            dao.insertAll(listOf(program("before", "a", 0, 100), program("current", "a", 100, 200),
                program("future", "a", 300, 400)))
            assertEquals(listOf("current"), dao.observeWindow("a", 100, 300).first().map { it.id })
            dao.applyUpdate(listOf(program("corrected", "a", 110, 200)), emptyList(), expiredBefore = -1)
            assertEquals(listOf("before", "corrected", "future"), dao.observeWindow("a", 0, 500).first().map { it.id })
        } finally { database.close() }
    }

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun batchWindowMatchesIndividualWindowsAndUsesChannelIndex() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, TellyoDatabase::class.java).build()
        try {
            val dao = database.programDao()
            dao.insertAll(
                listOf(
                    program("late", "a", 200, 400),
                    program("early", "a", 100, 200),
                    program("second-channel", "b", 120, 250),
                    program("outside", "a", 500, 600),
                    program("unselected", "c", 100, 300),
                ),
            )
            val batch = dao.observeChannelsWindow(listOf("a", "b"), 150, 300).first()
            assertEquals(listOf("early", "late", "second-channel"), batch.map { it.id })
            assertEquals(
                dao.observeWindow("a", 150, 300).first() + dao.observeWindow("b", 150, 300).first(),
                batch,
            )
            database.openHelper.readableDatabase.query(
                "EXPLAIN QUERY PLAN SELECT * FROM programs " +
                    "WHERE channelId IN ('a', 'b') AND endEpochMillis >= 150 AND startEpochMillis <= 300 " +
                    "ORDER BY channelId, startEpochMillis",
            ).use { cursor ->
                val details = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("detail")))
                }
                assertTrue(details.any { "index_programs_channelId_startEpochMillis" in it })
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun versionTwoMigrationPreservesCachedProgrammes() = runBlocking {
        val name = "program-migration-${System.nanoTime()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { database ->
            database.execSQL(
                "CREATE TABLE channels (id TEXT NOT NULL PRIMARY KEY, channelNumber INTEGER NOT NULL, " +
                    "name TEXT NOT NULL, shortName TEXT NOT NULL, logoUrl TEXT, providerId TEXT NOT NULL, " +
                    "category TEXT NOT NULL, enabled INTEGER NOT NULL)",
            )
            database.execSQL(
                "CREATE TABLE programs (id TEXT NOT NULL PRIMARY KEY, channelId TEXT NOT NULL, " +
                    "title TEXT NOT NULL, description TEXT, startEpochMillis INTEGER NOT NULL, " +
                    "endEpochMillis INTEGER NOT NULL, category TEXT, artworkUrl TEXT)",
            )
            database.execSQL("INSERT INTO programs VALUES ('saved', 'a', 'Saved show', NULL, 100, 200, NULL, NULL)")
            database.version = 2
        }
        val database = Room.databaseBuilder(context, TellyoDatabase::class.java, name)
            .addMigrations(TellyoDatabase.MIGRATION_2_3, TellyoDatabase.MIGRATION_3_4)
            .build()
        try {
            assertEquals("saved", database.programDao().observeWindow("a", 100, 200).first().single().id)
            database.openHelper.readableDatabase.query("PRAGMA index_list('programs')").use { cursor ->
                val names = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("index_programs_channelId_startEpochMillis" in names)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun program(id: String, channelId: String, start: Long, end: Long) = ProgramEntity(
        id = id,
        channelId = channelId,
        title = id,
        description = null,
        startEpochMillis = start,
        endEpochMillis = end,
        category = null,
        artworkUrl = null,
    )
}
