package me.rerere.rikkahub.service.hooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.truncateHookReason
import kotlin.uuid.Uuid

sealed interface ParsedHookOutput {
    val decision: HookDecision
    val reason: String
    val reasonTruncated: Boolean
}

data class ParsedAddTagHookOutput(
    override val decision: HookDecision,
    val tagId: Uuid?,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

data class ParsedMemoryTableSyncHookOutput(
    override val decision: HookDecision,
    val baseRevision: Int,
    val operations: JsonArray,
    override val reason: String,
    override val reasonTruncated: Boolean,
) : ParsedHookOutput

class HookOutputException(val code: HookErrorCode) : IllegalArgumentException(code.name)

object HookOutputParser {
    private val exactKeys = setOf("decision", "tagId", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String): ParsedAddTagHookOutput {
        if (raw != raw.trim()) throw HookOutputException(HookErrorCode.INVALID_JSON)
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)

        val decisionValue = objectValue["decision"].stringValue()
        val reasonValue = objectValue["reason"].stringValue().trim()
        val truncatedReason = truncateHookReason(reasonValue)
        return when (decisionValue) {
            "apply" -> {
                val tagValue = objectValue["tagId"].stringValue()
                val tagId = runCatching { Uuid.parse(tagValue) }.getOrElse {
                    throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
                }
                ParsedAddTagHookOutput(
                    HookDecision.APPLY,
                    tagId,
                    truncatedReason.value,
                    truncatedReason.truncated,
                )
            }

            "skip" -> {
                if (objectValue["tagId"] !is JsonNull) {
                    throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
                }
                ParsedAddTagHookOutput(
                    HookDecision.SKIP,
                    null,
                    truncatedReason.value,
                    truncatedReason.truncated,
                )
            }

            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (primitive.isString.not()) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}

object MemoryTableSyncHookOutputParser {
    private val exactKeys = setOf("decision", "baseRevision", "operations", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String, maxOperations: Int): ParsedMemoryTableSyncHookOutput {
        if (raw.length > me.rerere.rikkahub.data.model.HookRuntimeRules.MAX_SYNC_RESPONSE_CHARS) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        if (raw != raw.trim()) throw HookOutputException(HookErrorCode.INVALID_JSON)
        val objectValue = runCatching { strictJson.parseToJsonElement(raw) }.getOrElse {
            throw HookOutputException(HookErrorCode.INVALID_JSON)
        } as? JsonObject ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (objectValue.keys != exactKeys) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val decision = when (objectValue["decision"].stringValue()) {
            "apply" -> HookDecision.APPLY
            "skip" -> HookDecision.SKIP
            else -> throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val baseRevision = (objectValue["baseRevision"] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.intOrNull
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        val operations = objectValue["operations"] as? JsonArray
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (operations.size > maxOperations || (decision == HookDecision.SKIP && operations.isNotEmpty())) {
            throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        }
        val truncatedReason = truncateHookReason(objectValue["reason"].stringValue().trim())
        return ParsedMemoryTableSyncHookOutput(
            decision = decision,
            baseRevision = baseRevision,
            operations = operations,
            reason = truncatedReason.value,
            reasonTruncated = truncatedReason.truncated,
        )
    }

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String {
        val primitive = this as? JsonPrimitive
            ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        if (!primitive.isString) throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
        return primitive.contentOrNull ?: throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
    }
}
