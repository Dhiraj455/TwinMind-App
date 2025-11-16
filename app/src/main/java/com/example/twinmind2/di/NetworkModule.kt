package com.example.twinmind2.di

import com.example.twinmind2.transcription.TranscriptionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: android.content.Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTranscriptionApi(retrofit: Retrofit): TranscriptionApi {
        return retrofit.create(TranscriptionApi::class.java)
    }
    
    fun getGeminiApiKey(context: android.content.Context): String {
        // Hardcoded Google Gemini API key
        // Get a new key from: https://aistudio.google.com/app/apikey
        val hardcodedApiKey = "YOUR_GOOGLE_GEMINI_API_KEY_HERE"
        
        // Try to get from SharedPreferences first, fallback to hardcoded
        val prefs = context.getSharedPreferences("twinmind_prefs", android.content.Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "")?.takeIf { it.isNotEmpty() }
        
        return savedKey ?: hardcodedApiKey
    }
}

