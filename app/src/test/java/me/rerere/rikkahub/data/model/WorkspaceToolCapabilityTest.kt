package me.rerere.rikkahub.data.model

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.workspace.WorkspaceShellStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class WorkspaceToolCapabilityTest {
    private val workspaceId = Uuid.random()

    @Test
    fun capabilityIsTableDrivenByBindingResolutionAndNormalizedStatus() {
        data class Case(
            val name: String,
            val boundId: String?,
            val workspaces: List<WorkspaceEntity>,
            val available: Boolean,
            val reason: WorkspaceUnavailableReason?,
            val toolCount: Int,
        )
        val cases = listOf(
            Case("unconfigured", null, emptyList(), false, WorkspaceUnavailableReason.UNCONFIGURED, 0),
            Case("missing", workspaceId.toString(), emptyList(), false, WorkspaceUnavailableReason.MISSING, 0),
            Case("disabled", workspaceId.toString(), listOf(workspace("DISABLED")), false, WorkspaceUnavailableReason.DISABLED, 0),
            Case("installing", workspaceId.toString(), listOf(workspace("INSTALLING")), false, WorkspaceUnavailableReason.INSTALLING, 0),
            Case("broken", workspaceId.toString(), listOf(workspace("BROKEN")), false, WorkspaceUnavailableReason.BROKEN, 0),
            Case("unknown", workspaceId.toString(), listOf(workspace("future_status")), false, WorkspaceUnavailableReason.UNKNOWN, 0),
            Case("ready normalized", workspaceId.toString(), listOf(workspace(" ready ")), true, null, 4),
        )

        cases.forEach { case ->
            val capability = resolveWorkspaceToolCapability(case.boundId, case.workspaces)
            assertEquals(case.name, case.available, capability.available)
            assertEquals(case.name, case.reason, capability.unavailableReason)
            assertEquals(case.name, case.toolCount, capability.availableToolNames.size)
        }
    }

    @Test
    fun readOnlyReadyCapabilityExposesOnlyReadFile() {
        val capability = resolveWorkspaceToolCapability(
            workspaceId = workspaceId.toString(),
            workspaces = listOf(workspace(WorkspaceShellStatus.READY.name)),
            readOnly = true,
        )

        assertTrue(capability.configured)
        assertTrue(capability.available)
        assertEquals(listOf(WORKSPACE_READ_FILE_TOOL), capability.availableToolNames)
        assertNull(capability.unavailableReason)
    }

    @Test
    fun enableDecisionHandlesZeroOneManyAndInvalidEntities() {
        val secondId = Uuid.random()
        val validOne = workspace("READY")
        val validTwo = workspace("DISABLED", secondId.toString())
        val invalid = workspace("READY", "not-a-uuid")

        assertEquals(WorkspaceEnableDecision.CreateOrManage, decideWorkspaceEnable(emptyList()))
        assertEquals(WorkspaceEnableDecision.Invalid, decideWorkspaceEnable(listOf(invalid)))
        assertEquals(WorkspaceEnableDecision.Bind(workspaceId), decideWorkspaceEnable(listOf(validOne, invalid)))
        val many = decideWorkspaceEnable(listOf(validOne, invalid, validTwo))
        assertTrue(many is WorkspaceEnableDecision.Select)
        assertEquals(listOf(validOne, validTwo), (many as WorkspaceEnableDecision.Select).workspaces)
    }

    @Test
    fun selectionDecisionDistinguishesCancelInvalidAndValid() {
        assertEquals(WorkspaceSelectionDecision.Cancel, decideWorkspaceSelection(null))
        assertEquals(WorkspaceSelectionDecision.Invalid, decideWorkspaceSelection("bad-id"))
        assertEquals(WorkspaceSelectionDecision.Bind(workspaceId), decideWorkspaceSelection(workspaceId.toString()))
    }

    private fun workspace(status: String, id: String = workspaceId.toString()) = WorkspaceEntity(
        id = id,
        name = "Workspace",
        root = "/workspace/$id",
        shellStatus = status,
        createdAt = 1,
        updatedAt = 1,
    )
}
