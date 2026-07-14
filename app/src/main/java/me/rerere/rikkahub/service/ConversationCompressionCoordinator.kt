package me.rerere.rikkahub.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.model.Conversation

internal sealed interface CompressionLockResult<out T> {
    data object Busy : CompressionLockResult<Nothing>
    data class Acquired<T>(val value: T) : CompressionLockResult<T>
}

internal class ConversationCompressionCoordinator {
    private val mutex = Mutex()

    val isBusy: Boolean
        get() = mutex.isLocked

    suspend fun <T> tryRun(block: suspend () -> T): CompressionLockResult<T> {
        if (!mutex.tryLock()) return CompressionLockResult.Busy
        return try {
            CompressionLockResult.Acquired(block())
        } finally {
            mutex.unlock()
        }
    }
}

internal suspend fun <Prepared> runAutoCompressionIfEligible(
    invocationKind: GenerationInvocationKind,
    providerInputAvailable: Boolean,
    preparedOriginal: Prepared,
    onEligible: suspend () -> Prepared,
): Prepared {
    if (!invocationKind.supportsAutoCompression(providerInputAvailable)) return preparedOriginal
    return onEligible()
}

internal fun CompressionLockResult<Unit>.toManualCompressionResult(): Result<Unit> = when (this) {
    CompressionLockResult.Busy -> Result.failure(CompressionBusyException())
    is CompressionLockResult.Acquired -> Result.success(value)
}

internal suspend fun <Prepared, Snapshot> resolveAfterAutoCompressionAttempt(
    preparedOriginal: Prepared,
    compress: suspend () -> Unit,
    reload: suspend () -> Snapshot,
    prepareReloaded: suspend (Snapshot) -> Prepared,
    onSuccess: (Prepared) -> Unit,
    onFailure: (Throwable) -> Unit,
): Prepared {
    return try {
        compress()
        val refreshed = reload()
        prepareReloaded(refreshed).also(onSuccess)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        onFailure(error)
        preparedOriginal
    }
}

internal fun buildAutoCompressionFingerprint(
    conversation: Conversation,
    config: AutoCompressionConfig,
): String {
    var hash = FNV_OFFSET_BASIS

    fun add(value: String) {
        value.forEach { char ->
            hash = (hash xor char.code.toLong()) * FNV_PRIME
        }
        hash = (hash xor FINGERPRINT_SEPARATOR) * FNV_PRIME
    }

    add(conversation.id.toString())
    add(conversation.assistantId.toString())
    add(config.signature)

    conversation.messageNodes.asSequence()
        .filterNot { it.hidden }
        .forEach { node ->
            val message = node.currentMessage
            add(node.id.toString())
            add(node.selectIndex.toString())
            add(message.id.toString())
            add(message.role.name)
            message.parts.forEach { part ->
                add(part.javaClass.name)
                if (part is UIMessagePart.Text) {
                    add(part.text.length.toString())
                    add(part.text.take(FINGERPRINT_TEXT_EDGE_CHARS))
                    add(part.text.takeLast(FINGERPRINT_TEXT_EDGE_CHARS))
                }
            }
        }

    return hash.toULong().toString(16)
}

private const val FINGERPRINT_TEXT_EDGE_CHARS = 64
private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
private const val FINGERPRINT_SEPARATOR = 0xffL

class CompressionBusyException : IllegalStateException("Conversation compression is already in progress")

class CompressionSourceChangedException : IllegalStateException(
    "Conversation changed while compression was running; the compressed result was not applied",
)
