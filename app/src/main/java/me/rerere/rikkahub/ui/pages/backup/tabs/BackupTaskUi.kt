package me.rerere.rikkahub.ui.pages.backup.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.sync.BackupTaskStage

@Composable
internal fun backupStageText(stage: BackupTaskStage): String = when (stage) {
    BackupTaskStage.PREPARING -> stringResource(R.string.backup_page_stage_preparing)
    BackupTaskStage.TRANSFERRING -> stringResource(R.string.backup_page_stage_transferring)
    BackupTaskStage.WRITING -> stringResource(R.string.backup_page_stage_writing)
    BackupTaskStage.RESTORING -> stringResource(R.string.backup_page_stage_restoring)
}
