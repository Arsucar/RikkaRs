package me.rerere.rikkahub.service.hooks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.rerere.rikkahub.data.model.HookDecision
import me.rerere.rikkahub.data.model.HookErrorCode
import me.rerere.rikkahub.data.model.truncateHookReason
import kotlin.uuid.Uuid

data class ParsedHookOutput(
    val decision: HookDecision,
    val tagId: Uuid?,
    val reason: String,
    val reasonTruncated: Boolean,
)

class HookOutputException(val code: HookErrorCode) : IllegalArgumentException(code.name)

object HookOutputParser {
    private val exactKeys = setOf("decision", "tagId", "reason")
    private val strictJson = Json { isLenient = false; ignoreUnknownKeys = false }

    fun parse(raw: String): ParsedHookOutput {
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
                ParsedHookOutput(HookDecision.APPLY, tagId, truncatedReason.value, truncatedReason.truncated)
            }

            "skip" -> {
                if (objectValue["tagId"] !is JsonNull) {
                    throw HookOutputException(HookErrorCode.SCHEMA_MISMATCH)
                }
                ParsedHookOutput(HookDecision.SKIP, null, truncatedReason.value, truncatedReason.truncated)
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
