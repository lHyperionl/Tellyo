package com.tellyo.app.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
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

@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val category: String?,
    val artworkUrl: String?,
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
            "WHERE channelId = :channelId AND endEpochMillis >= :from AND startEpochMillis <= :to " +
            "ORDER BY startEpochMillis",
    )
    fun observeWindow(channelId: String, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programs: List<ProgramEntity>)

    @Query("DELETE FROM programs")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(programs: List<ProgramEntity>) {
        deleteAll()
        insertAll(programs)
    }
}

@Database(
    entities = [ChannelEntity::class, ProgramEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class TellyoDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    abstract fun programDao(): ProgramDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE programs ADD COLUMN artworkUrl TEXT")
            }
        }
    }
}
