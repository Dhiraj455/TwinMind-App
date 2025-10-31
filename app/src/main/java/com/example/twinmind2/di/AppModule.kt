package com.example.twinmind2.di

import android.content.Context
import androidx.room.Room
import com.example.twinmind2.data.AppDatabase
import com.example.twinmind2.data.dao.RecordingDao
import com.example.twinmind2.data.dao.SummaryDao
import com.example.twinmind2.data.dao.TranscriptDao
import com.example.twinmind2.recording.RecordingRepository
import com.example.twinmind2.transcription.TranscriptionApi
import com.example.twinmind2.transcription.TranscriptionRepository
import com.example.twinmind2.summary.SummaryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
    fun provideTranscriptDao(db: AppDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    fun provideSummaryDao(db: AppDatabase): SummaryDao = db.summaryDao()

    @Provides
    @Singleton
    fun provideRecordingRepository(dao: RecordingDao, @ApplicationContext context: Context): RecordingRepository =
        RecordingRepository(context, dao)

    @Provides
    @Singleton
    fun provideTranscriptionRepository(
        transcriptDao: TranscriptDao,
        transcriptionApi: TranscriptionApi,
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): TranscriptionRepository = TranscriptionRepository(transcriptDao, transcriptionApi, context, okHttpClient)

    @Provides
    @Singleton
    fun provideSummaryRepository(
        summaryDao: SummaryDao,
        transcriptDao: TranscriptDao,
        transcriptionApi: TranscriptionApi,
        @ApplicationContext context: Context
    ): SummaryRepository = SummaryRepository(context, summaryDao, transcriptDao, transcriptionApi)
}


