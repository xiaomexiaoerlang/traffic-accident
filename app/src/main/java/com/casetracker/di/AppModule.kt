package com.casetracker.di

import android.content.Context
import androidx.room.Room
import com.casetracker.data.local.AppDatabase
import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
import com.casetracker.data.local.dao.ChecklistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "case_tracker.db")
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCaseDao(db: AppDatabase): CaseDao = db.caseDao()

    @Provides
    fun provideCaseEventDao(db: AppDatabase): CaseEventDao = db.caseEventDao()

    @Provides
    fun provideChecklistDao(db: AppDatabase): ChecklistDao = db.checklistDao()
}
