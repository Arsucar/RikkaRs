package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import me.rerere.rikkahub.CHAT_KEEPALIVE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "ChatGenerationService"

/**
 * Experimental foreground service that raises process priority while chat generation is active.
 * Started/stopped by [ChatKeepAliveController] with reference counting across concurrent conversations.
 */
class ChatGenerationService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.CHAT_GENERATION_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.CHAT_GENERATION_STOP"
        const val ACTION_UPDATE = "me.rerere.rikkahub.action.CHAT_GENERATION_UPDATE"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CONTENT = "content"
        const val NOTIFICATION_ID = 2002

        private val foregroundActive = AtomicBoolean(false)

        fun isForegroundActive(): Boolean = foregroundActive.get()

        fun startIntent(
            context: Context,
            conversationId: String,
            title: String,
            content: String,
        ): Intent = Intent(context, ChatGenerationService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CONTENT, content)
        }

        fun updateIntent(
            context: Context,
            conversationId: String,
            title: String,
            content: String,
        ): Intent = Intent(context, ChatGenerationService::class.java).apply {
            action = ACTION_UPDATE
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_CONTENT, content)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, ChatGenerationService::class.java).apply {
                action = ACTION_STOP
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
                val title = intent.getStringExtra(EXTRA_TITLE)
                    ?: getString(R.string.notification_keepalive_title)
                val content = intent.getStringExtra(EXTRA_CONTENT)
                    ?: getString(R.string.notification_keepalive_content)
                if (!ensureForeground(conversationId, title, content)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            ACTION_STOP, null -> {
                clearForeground()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // stopService() may tear down without delivering ACTION_STOP; always drop FGS notification.
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground in onDestroy failed", e)
        }
        foregroundActive.set(false)
        super.onDestroy()
    }

    private fun ensureForeground(
        conversationId: String?,
        title: String,
        content: String,
    ): Boolean {
        return try {
            val notification = buildNotification(conversationId, title, content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundActive.set(true)
            true
        } catch (e: Exception) {
            // Some OEM ROMs reject specialUse FGS even when Manifest declares it.
            Log.e(TAG, "Failed to start foreground service", e)
            foregroundActive.set(false)
            false
        }
    }

    private fun clearForeground() {
        foregroundActive.set(false)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
    }

    private fun buildNotification(
        conversationId: String?,
        title: String,
        content: String,
    ): android.app.Notification {
        return NotificationCompat.Builder(this, CHAT_KEEPALIVE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(buildChatPendingIntent(conversationId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun buildChatPendingIntent(conversationId: String?): PendingIntent {
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!conversationId.isNullOrBlank()) {
                putExtra("conversationId", conversationId)
            }
        }
        val requestCode = conversationId?.hashCode() ?: 0
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
