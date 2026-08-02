package me.rerere.rikkahub.service

import android.app.Application
import android.os.SystemClock
import android.util.Log
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

private const val TAG = "ChatKeepAliveController"
private const val UPDATE_THROTTLE_MS = 1000L

/**
 * Reference-counted starter for [ChatGenerationService].
 * Tracks concurrent generations (including same conversation regenerate) so FGS is not stopped early.
 */
class ChatKeepAliveController(
    private val context: Application,
    private val settingsStore: SettingsStore,
) {
    private val activeGenerations = AtomicInteger(0)
    private val conversationRefCounts = ConcurrentHashMap<Uuid, AtomicInteger>()
    private val fgsRunning = AtomicBoolean(false)
    private val lastUpdateAt = ConcurrentHashMap<Uuid, Long>()
    @Volatile
    private var lastConversationId: Uuid? = null

    /** True only when the FGS actually entered foreground (OEM may refuse → false, live update can still show). */
    val isActive: Boolean
        get() = activeGenerations.get() > 0 &&
            fgsRunning.get() &&
            ChatGenerationService.isForegroundActive()

    fun onGenerationStart(conversationId: Uuid, senderName: String) {
        if (!settingsStore.settingsFlow.value.enableKeepAliveNotification) return

        conversationRefCounts
            .getOrPut(conversationId) { AtomicInteger(0) }
            .incrementAndGet()
        lastConversationId = conversationId
        val count = activeGenerations.incrementAndGet()

        val title = senderName.ifBlank {
            context.getString(R.string.notification_keepalive_title)
        }
        val content = context.getString(R.string.notification_keepalive_content)

        if (count == 1 || !ChatGenerationService.isForegroundActive()) {
            startService(conversationId, title, content)
        } else {
            updateService(conversationId, title, content)
        }
    }

    fun onGenerationEnd(conversationId: Uuid) {
        // Only end generations we tracked via onGenerationStart; ignore unpaired ends so we
        // never under-count and stop FGS while another conversation is still generating.
        val convCount = conversationRefCounts[conversationId] ?: return
        if (convCount.decrementAndGet() <= 0) {
            conversationRefCounts.remove(conversationId, convCount)
            lastUpdateAt.remove(conversationId)
        }

        val remaining = activeGenerations.decrementAndGet()
        if (remaining <= 0) {
            activeGenerations.set(0)
            conversationRefCounts.clear()
            lastConversationId = null
            stopService()
            return
        }

        val focusId = lastConversationId
            ?.takeIf { conversationRefCounts.containsKey(it) }
            ?: conversationRefCounts.keys.firstOrNull()
            ?: return
        val title = context.getString(R.string.notification_keepalive_title)
        val content = context.getString(
            R.string.notification_keepalive_active_count,
            remaining.coerceAtLeast(1),
        )
        updateService(focusId, title, content)
    }

    fun onGenerationProgress(
        conversationId: Uuid,
        senderName: String,
        statusText: String,
        contentText: String,
    ) {
        if (!isActive) return
        if (!conversationRefCounts.containsKey(conversationId)) return

        val now = SystemClock.elapsedRealtime()
        val last = lastUpdateAt[conversationId]
        if (last != null && now - last < UPDATE_THROTTLE_MS) return
        lastUpdateAt[conversationId] = now
        lastConversationId = conversationId

        val title = senderName.ifBlank {
            context.getString(R.string.notification_keepalive_title)
        }
        val content = listOf(statusText, contentText)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
            .ifBlank { context.getString(R.string.notification_keepalive_content) }
        updateService(conversationId, title, content)
    }

    private fun startService(conversationId: Uuid, title: String, content: String) {
        try {
            val intent = ChatGenerationService.startIntent(
                context = context,
                conversationId = conversationId.toString(),
                title = title,
                content = content,
            )
            context.startForegroundService(intent)
            // Optimistic; isActive also requires ChatGenerationService.isForegroundActive().
            fgsRunning.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ChatGenerationService", e)
            fgsRunning.set(false)
        }
    }

    private fun updateService(conversationId: Uuid, title: String, content: String) {
        if (!ChatGenerationService.isForegroundActive() && !fgsRunning.get()) return
        try {
            val intent = ChatGenerationService.updateIntent(
                context = context,
                conversationId = conversationId.toString(),
                title = title,
                content = content,
            )
            // UPDATE path also calls startForeground so a late start is safe.
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update ChatGenerationService", e)
        }
    }

    private fun stopService() {
        fgsRunning.set(false)
        lastUpdateAt.clear()
        val stopIntent = ChatGenerationService.stopIntent(context)
        try {
            // Prefer startService(STOP) so onStartCommand clears the FGS notification.
            // Context.stopService() does not deliver the Intent action to onStartCommand.
            context.startService(stopIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send STOP to ChatGenerationService", e)
            try {
                context.stopService(stopIntent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to stopService ChatGenerationService", e2)
            }
        }
    }
}
