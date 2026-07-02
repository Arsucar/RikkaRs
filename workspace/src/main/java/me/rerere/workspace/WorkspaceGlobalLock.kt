package me.rerere.workspace

import java.util.concurrent.atomic.AtomicBoolean

class WorkspaceGlobalLock {
    private val locked = AtomicBoolean(false)

    fun lock() {
        locked.set(true)
    }

    fun unlock() {
        locked.set(false)
    }

    fun isLocked(): Boolean = locked.get()
}