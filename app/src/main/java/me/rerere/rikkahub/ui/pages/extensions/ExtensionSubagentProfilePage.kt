package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.subagent.SubagentProfile
import me.rerere.rikkahub.data.ai.subagent.SubagentRegistry
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.pages.assistant.detail.SubagentProfileForm
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun ExtensionSubagentProfilePage(
    profileName: String,
    createMode: Boolean = false,
) {
    val vm: SettingVM = koinViewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val skillManager: SkillManager = koinInject()
    var skills by remember { mutableStateOf(emptyList<me.rerere.rikkahub.data.files.SkillMetadata>()) }
    LaunchedEffect(Unit) { skills = skillManager.listSkills() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val globalProfiles = settings.globalSubagentProfiles
    val resolved = globalProfiles.firstOrNull { it.name == profileName }
        ?: SubagentProfile(name = profileName)

    var pathDraft by remember(profileName) { mutableStateOf("") }
    var excludedDraft by remember(profileName) { mutableStateOf("") }

    fun persist(transform: (SubagentProfile) -> SubagentProfile) {
        val base = globalProfiles.firstOrNull { it.name == profileName }
            ?: SubagentProfile(name = profileName)
        val updated = transform(base)
        val newProfiles = globalProfiles
            .map { if (it.name == profileName) updated else it }
            .let { if (profileName !in it.map { p -> p.name }) it + updated else it }
        vm.updateSettings(settings.copy(globalSubagentProfiles = newProfiles))
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(resolved.displayName.ifBlank { profileName })
                },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SubagentProfileForm(
                resolved = resolved,
                profileName = profileName,
                createMode = createMode,
                providers = settings.providers,
                mcpServers = settings.mcpServers,
                skills = skills,
                readOnly = false,
                pathDraft = pathDraft,
                onPathDraftChange = { pathDraft = it },
                excludedDraft = excludedDraft,
                onExcludedDraftChange = { excludedDraft = it },
                onPersist = ::persist,
            )
        }
    }
}
