package me.rerere.rikkahub.service.hooks

import me.rerere.rikkahub.data.model.HookErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.uuid.Uuid

class ManageConversationTagsHookActionTest {
    private val allowed = Uuid.parse("00000000-0000-0000-0000-000000000010")
    private val other = Uuid.parse("00000000-0000-0000-0000-000000000020")
    private val missing = Uuid.parse("00000000-0000-0000-0000-000000000030")

    @Test
    fun emptyOpsAreRejectedAsSchemaMismatch() {
        val rejection = validateManageTagOperations(
            operations = emptyList(),
            allowedTagIds = setOf(allowed),
            existingTagIds = setOf(allowed),
        )

        assertEquals(HookErrorCode.SCHEMA_MISMATCH, rejection?.errorCode)
        assertNull(rejection?.tagId)
    }

    @Test
    fun outOfAllowlistOpRejectsWholeBatch() {
        val rejection = validateManageTagOperations(
            operations = listOf(
                TagManageOperation(TagManageOpKind.ADD, allowed),
                TagManageOperation(TagManageOpKind.REMOVE, other),
            ),
            allowedTagIds = setOf(allowed),
            existingTagIds = setOf(allowed, other),
        )

        assertEquals(HookErrorCode.TAG_NOT_ALLOWED, rejection?.errorCode)
        assertEquals(other, rejection?.tagId)
    }

    @Test
    fun missingTagRejectsWholeBatch() {
        val rejection = validateManageTagOperations(
            operations = listOf(TagManageOperation(TagManageOpKind.ADD, missing)),
            allowedTagIds = setOf(missing),
            existingTagIds = emptySet(),
        )

        assertEquals(HookErrorCode.TAG_NOT_FOUND, rejection?.errorCode)
        assertEquals(missing, rejection?.tagId)
    }

    @Test
    fun duplicateOpsRejectWholeBatch() {
        val rejection = validateManageTagOperations(
            operations = listOf(
                TagManageOperation(TagManageOpKind.ADD, allowed),
                TagManageOperation(TagManageOpKind.ADD, allowed),
            ),
            allowedTagIds = setOf(allowed),
            existingTagIds = setOf(allowed),
        )

        assertEquals(HookErrorCode.SCHEMA_MISMATCH, rejection?.errorCode)
        assertEquals(allowed, rejection?.tagId)
    }

    @Test
    fun validOpsPassFailClosedGate() {
        val rejection = validateManageTagOperations(
            operations = listOf(
                TagManageOperation(TagManageOpKind.REMOVE, allowed),
                TagManageOperation(TagManageOpKind.ADD, other),
            ),
            allowedTagIds = setOf(allowed, other),
            existingTagIds = setOf(allowed, other),
        )

        assertNull(rejection)
    }
}
