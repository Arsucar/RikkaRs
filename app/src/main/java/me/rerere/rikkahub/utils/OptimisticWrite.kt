package me.rerere.rikkahub.utils

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

/**
 * Result of an optimistic write (#295).
 *
 * [value] is the optimistic snapshot produced by [runOptimisticWrite]'s apply step
 * (or the latest generation token for [OptimisticWriteCoordinator]).
 */
data class OptimisticWriteResult<T>(
    val value: T,
    val success: Boolean,
    val error: Throwable? = null,
)

/**
 * Pure optimistic-write primitive (#295).
 *
 * 1. [applyOptimistic] runs **synchronously** so UI can flip in the same frame.
 * 2. [persist] runs asynchronously (caller decides the dispatcher).
 * 3. On failure: [rollback] restores prior UI state, then [onError] surfaces the failure.
 * 4. [CancellationException] is rethrown without rollback (structured cancel is not a write failure).
 *
 * Returns [OptimisticWriteResult] for tests and callers that need the outcome.
 */
suspend fun <T> runOptimisticWrite(
    applyOptimistic: () -> T,
    persist: suspend (T) -> Unit,
    rollback: (T) -> Unit,
    onError: (Throwable) -> Unit = {},
): OptimisticWriteResult<T> {
    val snapshot = applyOptimistic()
    return try {
        persist(snapshot)
        OptimisticWriteResult(value = snapshot, success = true)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        rollback(snapshot)
        onError(error)
        OptimisticWriteResult(value = snapshot, success = false, error = error)
    }
}

/**
 * Coordinates rapid successive optimistic writes on the same logical key (#295 AC: 连续快速操作不丢更新).
 *
 * Each [run] bumps a generation counter. Only the latest generation is allowed to
 * commit success or perform rollback, so a slow failed write cannot clobber a newer
 * successful optimistic state.
 *
 * Thread-safe for concurrent [run] calls via atomic generation; callers still serialize
 * their own state mutations if needed.
 */
class OptimisticWriteCoordinator {
    private val generation = AtomicLong(0L)

    fun currentGeneration(): Long = generation.get()

    /**
     * @param applyOptimistic synchronous UI mutation; return a token describing the write
     * @param persist suspend persistence for that token
     * @param rollback only invoked when this generation is still current and [persist] failed
     * @param onError only invoked on failure for the current generation (same gate as rollback)
     */
    suspend fun <T> run(
        applyOptimistic: () -> T,
        persist: suspend (T) -> Unit,
        rollback: (T) -> Unit,
        onError: (Throwable) -> Unit = {},
    ): OptimisticWriteResult<T> {
        val gen = generation.incrementAndGet()
        val snapshot = applyOptimistic()
        return try {
            persist(snapshot)
            // Stale success: a newer write already owns UI; do not report failure.
            OptimisticWriteResult(value = snapshot, success = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (generation.get() == gen) {
                rollback(snapshot)
                onError(error)
            }
            OptimisticWriteResult(value = snapshot, success = false, error = error)
        }
    }

    /** Test helper: force the next [run] to treat prior generations as stale. */
    fun bumpGeneration(): Long = generation.incrementAndGet()
}

/**
 * Applies a list of pure overlays onto a base value left-to-right.
 * Useful for `combine(dbFlow, overlayFlow) { base, overlays -> foldOverlays(base, overlays) }`.
 */
fun <T> foldOverlays(base: T, overlays: List<(T) -> T>): T =
    overlays.fold(base) { acc, overlay -> overlay(acc) }
