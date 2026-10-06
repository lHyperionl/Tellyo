package com.tellyo.app.core.database

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val channelNumber: Int,
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val providerId: String,
    val category: String,
    val enabled: Boolean,
)

@Entity(tableName = "programs", indices = [Index(value = ["channelId", "startEpochMillis"])])
data class ProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val category: String?,
    val artworkUrl: String?,
    @ColumnInfo(defaultValue = "0") val sourcePriority: Int = 0,
)

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE enabled = 1 ORDER BY channelNumber")
    fun observeEnabled(): Flow<List<ChannelEntity>>
}

@Dao
interface ProgramDao {
    @Query(
        "SELECT * FROM programs " +
            "WHERE channelId = :channelId AND endEpochMillis > :from AND startEpochMillis < :to " +
            "ORDER BY startEpochMillis",
    )
    fun observeWindow(channelId: String, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Query(
        "SELECT * FROM programs " +
            "WHERE channelId IN (:channelIds) AND endEpochMillis > :from AND startEpochMillis < :to " +
            "ORDER BY channelId, startEpochMillis",
    )
    fun observeChannelsWindow(channelIds: List<String>, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Query("DELETE FROM programs WHERE channelId IN (:channelIds) AND sourcePriority <= :maximumPriority")
    suspend fun deleteChannels(channelIds: List<String>, maximumPriority: Int = Int.MAX_VALUE)

    @Query("DELETE FROM programs WHERE channelId = :channelId AND endEpochMillis > :from AND startEpochMillis < :to AND sourcePriority <= :maximumPriority")
    suspend fun deleteOverlapping(channelId: String, from: Long, to: Long, maximumPriority: Int = Int.MAX_VALUE)

    @Query("SELECT * FROM programs WHERE channelId IN (:channelIds) AND sourcePriority > :priority")
    suspend fun higherPriorityPrograms(channelIds: List<String>, priority: Int): List<ProgramEntity>

    @Query("DELETE FROM programs WHERE endEpochMillis < :before")
    suspend fun deleteExpired(before: Long)

    @Transaction
    suspend fun applyUpdate(
        programs: List<ProgramEntity>,
        replacementWindows: List<com.tellyo.app.core.epg.EpgReplacementWindow>?,
        expiredBefore: Long,
        sourcePriority: Int = 0,
    ) {
        val protected = higherPriorityPrograms(programs.map { it.channelId }.distinct(), sourcePriority)
        val accepted = programs.filter { program -> protected.none { existing ->
            existing.channelId == program.channelId && existing.endEpochMillis > program.startEpochMillis &&
                existing.startEpochMillis < program.endEpochMillis
        } }
        if (replacementWindows == null) {
            deleteChannels(programs.map { it.channelId }.distinct(), sourcePriority)
        } else {
            replacementWindows.forEach { window ->
                deleteOverlapping(window.channelId, window.from.toEpochMilli(), window.to.toEpochMilli(), sourcePriority)
            }
            // Live-only metadata can correct a programme's start/id without dropping future shows.
            programs.forEach { program ->
                if (replacementWindows.none { window ->
                        window.channelId == program.channelId &&
                            window.from.toEpochMilli() <= program.startEpochMillis &&
                            window.to.toEpochMilli() >= program.endEpochMillis
                    }) {
                    deleteOverlapping(program.channelId, program.startEpochMillis, program.endEpochMillis, sourcePriority)
                }
            }
        }
        if (accepted.isNotEmpty()) insertAll(accepted.map { it.copy(sourcePriority = sourcePriority) })
        deleteExpired(expiredBefore)
    }

    @Transaction
    suspend fun replaceAll(programs: List<ProgramEntity>) {
        deleteAll()
        insertAll(programs)
    }

    @Transaction
    suspend fun replaceChannels(programs: List<ProgramEntity>, channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        deleteChannels(channelIds)
        insertAll(programs)
    }
}

@Database(
    entities = [ChannelEntity::class, ProgramEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class TellyoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    abstract fun programDao(): ProgramDao

    companion object {
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN sourcePriority INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_programs_channelId_startEpochMillis " +
                        "ON programs (channelId, startEpochMillis)",
                )
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN artworkUrl TEXT")
            }
        }
    }
}
