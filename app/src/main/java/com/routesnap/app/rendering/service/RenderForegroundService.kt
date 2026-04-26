package com.routesnap.app.rendering.service

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
import androidx.media3.common.util.UnstableApi
import com.routesnap.app.MainActivity
import com.routesnap.app.data.repository.TripRepository
import com.routesnap.app.util.StorageHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for video rendering
 * Keeps the app alive during long-running video export operations
 */
@UnstableApi
@AndroidEntryPoint
class RenderForegroundService : Service() {
    @Inject
    lateinit var renderManager: RenderManager

    @Inject
    lateinit var tripRepository: TripRepository

    @Inject
    lateinit var storageHelper: StorageHelper

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var stateObservationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                renderManager.cancelRendering()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START_RENDER -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                if (tripId != null) {
                    startRender(tripId)
                } else {
                    stopSelf()
                }
            }

            else -> {
                // Default behavior if no action specified
                if (intent?.hasExtra(EXTRA_TRIP_ID) == true) {
                    startRender(intent.getStringExtra(EXTRA_TRIP_ID)!!)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(0, "Initializing..."))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startRender(tripId: String) {
        startForeground(NOTIFICATION_ID, createNotification(0, "Preparing trip..."))

        stateObservationJob?.cancel()
        stateObservationJob = renderManager.renderState
            .onEach { state ->
                when (state) {
                    is RenderManager.RenderState.Rendering -> {
                        updateProgress(state.progress, state.status)
                    }
                    is RenderManager.RenderState.Completed -> {
                        // Notify completion if needed, then stop
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is RenderManager.RenderState.Failed -> {
                        // Notify failure if needed, then stop
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is RenderManager.RenderState.Cancelled -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> {}
                }
            }
            .launchIn(serviceScope)

        serviceScope.launch {
            val trip = tripRepository.getTripById(tripId)
            if (trip != null) {
                val outputFile = storageHelper.createOutputFile(trip.name)
                renderManager.startRendering(trip, outputFile)
            } else {
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stateObservationJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Video Rendering",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows progress of video rendering"
                    setShowBadge(false)
                }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        progress: Int,
        status: String,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, RenderForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rendering Video")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(
                100,
                progress,
                progress == 0,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent,
            )
            .build()
    }

    /**
     * Update the notification progress
     */
    @Suppress("NotificationPermission")
    fun updateProgress(progress: Int, status: String) {
        val notification = createNotification(progress, status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "routesnap_render_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "action_cancel"
        const val ACTION_START_RENDER = "action_start_render"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        /**
         * Helper to start the service
         */
        fun start(context: Context, tripId: String) {
            val intent = Intent(context, RenderForegroundService::class.java).apply {
                action = ACTION_START_RENDER
                putExtra(EXTRA_TRIP_ID, tripId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
