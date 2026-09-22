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

    @Provides
    @Singleton
    fun provideCategoryDao(database: AppDatabase): com.tkrz.qrtix.data.CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideCategoryRepository(
        categoryDao: com.tkrz.qrtix.data.CategoryDao,
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences
    ): com.tkrz.qrtix.data.repository.CategoryRepository {
        return com.tkrz.qrtix.data.repository.CategoryRepository(categoryDao, sheetsService, cloudPreferences)
    }

    @Provides
    @Singleton
    fun provideTicketRepository(
        ticketDao: TicketDao,
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences
    ): com.tkrz.qrtix.data.repository.TicketRepository {
        return com.tkrz.qrtix.data.repository.TicketRepository(ticketDao, sheetsService, cloudPreferences)
    }

    @Provides
    @Singleton
    fun provideEventBackupManager(
        driveService: com.tkrz.qrtix.data.cloud.GoogleDriveService,
        driveFolderManager: com.tkrz.qrtix.data.cloud.DriveFolderManager,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
        ticketDao: TicketDao,
        @ApplicationContext context: Context
    ): com.tkrz.qrtix.data.cloud.EventBackupManager {
        return com.tkrz.qrtix.data.cloud.EventBackupManager(driveService, driveFolderManager, cloudPreferences, ticketDao, context)
    }

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: EventDao,
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
        eventPreferences: com.tkrz.qrtix.data.EventPreferences,
        eventBackupManager: com.tkrz.qrtix.data.cloud.EventBackupManager,
        categoryDao: com.tkrz.qrtix.data.CategoryDao
    ): com.tkrz.qrtix.data.repository.EventRepository {
        return com.tkrz.qrtix.data.repository.EventRepository(
            eventDao,
            sheetsService,
            cloudPreferences,
            eventPreferences,
            eventBackupManager,
            categoryDao
        )
    }

    @Provides
    @Singleton
    fun provideHistoryLogRepository(
        historyLogDao: com.tkrz.qrtix.data.HistoryLogDao,
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences
    ): com.tkrz.qrtix.data.repository.HistoryLogRepository {
        return com.tkrz.qrtix.data.repository.HistoryLogRepository(historyLogDao, sheetsService, cloudPreferences)
    }

    @Provides
    @Singleton
    fun provideAuthPreferences(@ApplicationContext context: Context): com.tkrz.qrtix.data.AuthPreferences {
        return com.tkrz.qrtix.data.AuthPreferences(context)
    }

    @Provides
    @Singleton
    fun provideGoogleCredentialManager(
        @ApplicationContext context: Context,
        authPreferences: com.tkrz.qrtix.data.AuthPreferences
    ): com.tkrz.qrtix.data.cloud.GoogleCredentialManager {
        return com.tkrz.qrtix.data.cloud.GoogleCredentialManager(context, authPreferences)
    }

    @Provides
    @Singleton
    fun provideGoogleSheetsService(credentialManager: com.tkrz.qrtix.data.cloud.GoogleCredentialManager): com.tkrz.qrtix.data.cloud.GoogleSheetsService {
        return com.tkrz.qrtix.data.cloud.GoogleSheetsService(credentialManager)
    }

    @Provides
    @Singleton
    fun provideGoogleDriveService(credentialManager: com.tkrz.qrtix.data.cloud.GoogleCredentialManager): com.tkrz.qrtix.data.cloud.GoogleDriveService {
        return com.tkrz.qrtix.data.cloud.GoogleDriveService(credentialManager)
    }

    @Provides
    @Singleton
    fun provideCloudPreferences(@ApplicationContext context: Context): com.tkrz.qrtix.data.cloud.CloudPreferences {
        return com.tkrz.qrtix.data.cloud.CloudPreferences(context)
    }

    @Provides
    @Singleton
    fun provideSpreadsheetManager(
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
        driveService: com.tkrz.qrtix.data.cloud.GoogleDriveService,
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService
    ): com.tkrz.qrtix.data.cloud.SpreadsheetManager {
        return com.tkrz.qrtix.data.cloud.SpreadsheetManager(cloudPreferences, driveService, sheetsService)
    }

    @Provides
    @Singleton
    fun provideDistributionRepository(
        sheetsService: com.tkrz.qrtix.data.cloud.GoogleSheetsService,
        ticketRepository: com.tkrz.qrtix.data.repository.TicketRepository
    ): com.tkrz.qrtix.data.repository.DistributionRepository {
        return com.tkrz.qrtix.data.repository.DistributionRepository(sheetsService, ticketRepository)
    }

    @Provides
    @Singleton
    fun provideGmailService(
        credentialManager: com.tkrz.qrtix.data.cloud.GoogleCredentialManager
    ): com.tkrz.qrtix.data.cloud.GmailService {
        return com.tkrz.qrtix.data.cloud.GmailService(credentialManager)
    }

    @Provides
    @Singleton
    fun provideDriveFolderManager(
        driveService: com.tkrz.qrtix.data.cloud.GoogleDriveService
    ): com.tkrz.qrtix.data.cloud.DriveFolderManager {
        return com.tkrz.qrtix.data.cloud.DriveFolderManager(driveService)
    }

    @Provides
    @Singleton
    fun provideBackgroundUploadManager(
        driveService: com.tkrz.qrtix.data.cloud.GoogleDriveService,
        driveFolderManager: com.tkrz.qrtix.data.cloud.DriveFolderManager,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
        historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
        eventPreferences: com.tkrz.qrtix.data.EventPreferences
    ): com.tkrz.qrtix.data.cloud.BackgroundUploadManager {
        return com.tkrz.qrtix.data.cloud.BackgroundUploadManager(
            driveService,
            driveFolderManager,
            cloudPreferences,
            historyLogRepository,
            eventPreferences
        )
    }
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): com.tkrz.qrtix.utils.NetworkMonitor {
        return com.tkrz.qrtix.utils.NetworkMonitor(context)
    }

    @Provides
    @Singleton
    fun provideMediaManager(
        driveService: com.tkrz.qrtix.data.cloud.GoogleDriveService,
        cloudPreferences: com.tkrz.qrtix.data.cloud.CloudPreferences,
        driveFolderManager: com.tkrz.qrtix.data.cloud.DriveFolderManager,
        @ApplicationContext context: Context
    ): com.tkrz.qrtix.data.cloud.MediaManager {
        return com.tkrz.qrtix.data.cloud.MediaManager(driveService, cloudPreferences, driveFolderManager, context)
    }

    @Provides
    @Singleton
    fun provideDatabaseTransferManager(
        eventRepository: com.tkrz.qrtix.data.repository.EventRepository,
        ticketRepository: com.tkrz.qrtix.data.repository.TicketRepository,
        categoryRepository: com.tkrz.qrtix.data.repository.CategoryRepository,
        historyLogRepository: com.tkrz.qrtix.data.repository.HistoryLogRepository,
        @ApplicationContext context: Context
    ): com.tkrz.qrtix.data.transfer.DatabaseTransferManager {
        return com.tkrz.qrtix.data.transfer.DatabaseTransferManager(
            eventRepository,
            ticketRepository,
            categoryRepository,
            historyLogRepository,
            context
        )
    }
}
