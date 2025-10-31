package com.example.twinmind2.di

import android.content.Context
import androidx.room.Room
import com.example.twinmind2.data.AppDatabase
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.recording.RecordingRepository
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
        Room.databaseBuilder(context, AppDatabase::class.java, "twinmind.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecordingDao(db: AppDatabase): RecordingDao = db.recordingDao()

    @Provides
    @Singleton
    fun provideRecordingRepository(dao: RecordingDao, @ApplicationContext context: Context): RecordingRepository =
        RecordingRepository(context, dao)
}


