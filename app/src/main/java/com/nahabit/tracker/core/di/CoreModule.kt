package com.nahabit.tracker.core.di

import android.content.Context
import androidx.room.Room
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.database.NahabitDatabase
import com.nahabit.tracker.core.database.NahabitMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NahabitDatabase {
        return Room.databaseBuilder(
            context,
            NahabitDatabase::class.java,
            "nahabit_local.db"
        )
            .addMigrations(
                NahabitMigrations.MIGRATION_1_2,
                NahabitMigrations.MIGRATION_2_3,
                NahabitMigrations.MIGRATION_3_4,
                NahabitMigrations.MIGRATION_4_5,
                NahabitMigrations.MIGRATION_5_6,
                NahabitMigrations.MIGRATION_6_7,
                NahabitMigrations.MIGRATION_7_8,
                NahabitMigrations.MIGRATION_8_9
            )
            .build()
    }

    @Provides
    fun provideNahabitDao(database: NahabitDatabase): NahabitDao = database.dao()
}
