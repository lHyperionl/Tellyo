package com.tellyo.app.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideTellyoDatabase(@ApplicationContext context: Context): TellyoDatabase =
        Room.databaseBuilder(context, TellyoDatabase::class.java, "tellyo.db")
            .addMigrations(TellyoDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideProgramDao(database: TellyoDatabase): ProgramDao = database.programDao()
}
