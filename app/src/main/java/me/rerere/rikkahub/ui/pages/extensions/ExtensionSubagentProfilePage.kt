package me.rerere.rikkahub.ui.pages.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
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

    var currentProfileName by remember(profileName) { mutableStateOf(profileName) }
    val globalProfiles = settings.globalSubagentProfiles
    val effectiveGlobals = SubagentRegistry.effectiveGlobalProfiles(globalProfiles)
    val resolved = effectiveGlobals.firstOrNull { it.name == currentProfileName }
        ?: SubagentProfile(name = currentProfileName)

    var pathDraft by remember(currentProfileName) { mutableStateOf("") }

    fun persist(transform: (SubagentProfile) -> SubagentProfile) {
        val base = globalProfiles.firstOrNull { it.name == currentProfileName }
            ?: effectiveGlobals.firstOrNull { it.name == currentProfileName }
            ?: SubagentProfile(name = currentProfileName)
        val updated = transform(base)
        val oldName = currentProfileName
        val newProfiles = globalProfiles
            .filterNot { it.name == oldName || it.name == updated.name } + updated
        vm.updateSettings(settings.copy(globalSubagentProfiles = newProfiles))
        currentProfileName = updated.name
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(resolved.name)
                },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState { 4 }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .imePadding(),
        ) {
            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.subagent_profile_tab_basic)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.subagent_profile_tab_model)) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text(stringResource(R.string.subagent_profile_tab_tools)) },
                )
                Tab(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    text = { Text(stringResource(R.string.subagent_profile_tab_output)) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                SubagentProfileForm(
                    resolved = resolved,
                    profileName = currentProfileName,
                    createMode = createMode,
                    takenProfileNames = (effectiveGlobals.map { it.name } - currentProfileName).toSet(),
                    canEditName = currentProfileName !in SubagentRegistry.BUILTIN_PROFILES.map { it.name },
                    providers = settings.providers,
                    mcpServers = settings.mcpServers,
                    skills = skills,
                    presets = settings.presets,
                    readOnly = false,
                    pathDraft = pathDraft,
                    onPathDraftChange = { pathDraft = it },
                    onPersist = ::persist,
                    tabPage = page,
                )
            }
        }
    }
}
