package me.rerere.rikkahub.data.ai.subagent

import me.rerere.rikkahub.data.model.MemoryTableDocument
import me.rerere.rikkahub.data.model.MemoryTableScopeType
import me.rerere.rikkahub.data.model.MemoryTableTemplate

/**
 * Resolve selected memory table **document instances** into templates/documents
 * safe to inject for the parent assistant's effective scope.
 */
fun resolveSubagentMemoryTableInjection(
    selectedDocumentIds: Set<String>,
    templates: List<MemoryTableTemplate>,
    documents: List<MemoryTableDocument>,
    parentMemoryTableEnabled: Boolean,
    memoryTableIsolation: Boolean = false,
): Pair<List<MemoryTableTemplate>, List<MemoryTableDocument>> {
    if (!parentMemoryTableEnabled || selectedDocumentIds.isEmpty()) {
        return emptyList<MemoryTableTemplate>() to emptyList()
    }
    val selected = LinkedHashSet(selectedDocumentIds)
    val scopedDocuments = if (memoryTableIsolation) {
        documents.filter { it.scopeType == MemoryTableScopeType.CONVERSATION }
    } else {
        documents
    }
    val resolvedDocuments = scopedDocuments
        .filter { it.id in selected }
        .sortedWith(compareBy({ it.templateId }, { it.id }))
    if (resolvedDocuments.isEmpty()) {
        return emptyList<MemoryTableTemplate>() to emptyList()
    }
    val allowedTemplateIds = resolvedDocuments.mapTo(mutableSetOf()) { it.templateId }
    val resolvedTemplates = templates
        .filter { it.id in allowedTemplateIds }
        .sortedBy { it.id }
    if (resolvedTemplates.isEmpty()) {
        return emptyList<MemoryTableTemplate>() to emptyList()
    }
    val presentTemplateIds = resolvedTemplates.mapTo(mutableSetOf()) { it.id }
    val documentsWithSchema = resolvedDocuments.filter { it.templateId in presentTemplateIds }
    if (documentsWithSchema.isEmpty()) {
        return emptyList<MemoryTableTemplate>() to emptyList()
    }
    return resolvedTemplates to documentsWithSchema
}
