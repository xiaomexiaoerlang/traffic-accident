package com.casetracker.di

import android.content.Context
import androidx.room.Room
import com.casetracker.data.local.AppDatabase
import com.casetracker.data.local.dao.CaseDao
import com.casetracker.data.local.dao.CaseEventDao
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
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCaseDao(db: AppDatabase): CaseDao = db.caseDao()

    @Provides
    fun provideCaseEventDao(db: AppDatabase): CaseEventDao = db.caseEventDao()
}
