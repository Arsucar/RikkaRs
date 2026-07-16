package me.rerere.rikkahub.service

import me.rerere.rikkahub.data.model.MemoryCapabilities

internal data class GenerationMemoryPlan(
    val ordinaryMemoryEnabled: Boolean,
    val memoryTableTransformerEnabled: Boolean,
    val memoryTableToolsEnabled: Boolean,
)

internal fun MemoryCapabilities.toGenerationMemoryPlan(): GenerationMemoryPlan = GenerationMemoryPlan(
    ordinaryMemoryEnabled = normalMemoryEnabled,
    memoryTableTransformerEnabled = memoryTableEnabled,
    memoryTableToolsEnabled = memoryTableEnabled,
)

internal suspend fun <T> GenerationMemoryPlan.readOrdinaryMemories(
    read: suspend () -> List<T>,
): List<T> = if (ordinaryMemoryEnabled) read() else emptyList()
