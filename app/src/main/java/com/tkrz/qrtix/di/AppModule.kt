package com.tkrz.qrtix.di

import android.content.Context
import com.tkrz.qrtix.data.AppDatabase
import com.tkrz.qrtix.data.EventDao
import com.tkrz.qrtix.data.EventPreferences
import com.tkrz.qrtix.data.TicketDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTicketDao(database: AppDatabase): TicketDao {
        return database.ticketDao()
    }

    @Provides
    @Singleton
    fun provideEventDao(database: AppDatabase): EventDao {
        return database.eventDao()
    }

    @Provides
    @Singleton
    fun provideEventPreferences(@ApplicationContext context: Context): EventPreferences {
        return EventPreferences(context)
    }

    @Provides
    @Singleton
    fun provideHistoryLogDao(database: AppDatabase): com.tkrz.qrtix.data.HistoryLogDao {
        return database.historyLogDao()
    }
}
