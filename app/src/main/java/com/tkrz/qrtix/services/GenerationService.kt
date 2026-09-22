package com.tkrz.qrtix.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tkrz.qrtix.MainActivity
import com.tkrz.qrtix.utils.TicketExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GenerationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val CHANNEL_ID = "generation_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SERVICE") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Prepare intent to return to the app
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // We can pass an extra to tell the app to open the Generator screen or handle backup
            putExtra("FROM_GENERATION_SERVICE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action for notification
        val stopIntent = Intent(this, GenerationService::class.java).apply {
            this.action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QRTix — Generate Tiket")
            .setContentText("Memulai proses generate...")
            .setSmallIcon(com.tkrz.qrtix.R.mipmap.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())

        startGeneration(notificationBuilder, launchIntent)

        return START_NOT_STICKY
    }

    private fun startGeneration(notificationBuilder: NotificationCompat.Builder, launchIntent: Intent) {
        val tickets = GenerationTaskHolder.dummyTickets
        val event = GenerationTaskHolder.eventForExport
        val zipName = GenerationTaskHolder.zipName

        if (tickets.isEmpty()) {
            stopForeground(true)
            stopSelf()
            return
        }

        serviceScope.launch {
            val exporter = TicketExporter(applicationContext)
            var lastUpdateMs = System.currentTimeMillis()

            val (file, uploadTasks) = exporter.exportTicketsToZip(tickets, event, fileName = zipName) { current, total ->
                val now = System.currentTimeMillis()
                // Update Notification at most every 500ms to avoid throttling
                if (now - lastUpdateMs > 500 || current == total) {
                    val percent = (current.toFloat() / total * 100).toInt()
                    notificationBuilder.setContentText("Memproses $current/$total tiket ($percent%)")
                        .setProgress(total, current, false)
                    
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
                    
                    GenerationTaskHolder.updateProgress(current, total)
                    lastUpdateMs = now
                }
            }

            GenerationTaskHolder.updateProgress(tickets.size, tickets.size, true, file, uploadTasks)

            // Show completion notification
            val successPendingIntent = PendingIntent.getActivity(
                applicationContext, 2, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val successNotification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Generate Selesai")
                .setContentText("${tickets.size} tiket berhasil di-generate. Ketuk untuk opsi backup.")
                .setSmallIcon(com.tkrz.qrtix.R.mipmap.ic_launcher_foreground)
                .setContentIntent(successPendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID + 1, successNotification)

            stopForeground(true)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Proses Background QRTix",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi untuk proses generate dan upload berjalan di latar belakang"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
