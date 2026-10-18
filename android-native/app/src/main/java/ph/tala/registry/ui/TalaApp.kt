package ph.tala.registry.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import ph.tala.registry.ui.interview.InterviewWorkspaceScreen
import ph.tala.registry.ui.interview.InterviewWorkspaceViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ph.tala.registry.data.EnumeratorProfileStore
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.data.PhotoStore
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.domain.model.MemberId
import ph.tala.registry.ui.form.FormSessionViewModel
import ph.tala.registry.ui.form.PracticeFormScreen
import ph.tala.registry.ui.households.HouseholdEditScreen
import ph.tala.registry.ui.households.HouseholdEditViewModel
import ph.tala.registry.ui.households.MemberEditScreen
import ph.tala.registry.ui.households.MemberEditViewModel
import ph.tala.registry.ui.households.SaveStatusLine
import ph.tala.registry.ui.profile.EnumeratorProfileUiState
import ph.tala.registry.ui.profile.EnumeratorProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalaApp(repository: HouseholdRepository, photos: PhotoStore, profiles: EnumeratorProfileStore) {
    val registry: RegistryViewModel = viewModel(factory = viewModelFactory {
        initializer { RegistryViewModel(repository, createSavedStateHandle()) }
    })
    val state by registry.uiState.collectAsStateWithLifecycle()
    val profile: EnumeratorProfileViewModel = viewModel(factory = viewModelFactory {
        initializer { EnumeratorProfileViewModel(profiles, photos) }
    })
    val profileState by profile.uiState.collectAsStateWithLifecycle()
    val savedProfile by profiles.profile.collectAsStateWithLifecycle(initialValue = ph.tala.registry.domain.model.EnumeratorProfile("", ""))
    val forms: FormSessionViewModel = viewModel()
    val formState by forms.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Destination.Home.route
    // Task screens own the full window: forms and interviews hide the bottom bar so a
    // mis-tap cannot abandon an unfinished edit.
    val isFullScreen = route == Destination.Questionnaire.PATTERN || route == Destination.Interview.PATTERN || route == Destination.Practice.PATTERN ||
        route == Destination.HouseholdAdd.route || route == Destination.HouseholdEdit.PATTERN ||
        route == Destination.MemberAdd.PATTERN || route == Destination.MemberEdit.PATTERN || route == Destination.Profile.route
    val openHousehold: (HouseholdId) -> Unit = { nav.navigate(Destination.Interview(it).route) { launchSingleTop = true } }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tala", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(savedProfile.barangay, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    if (route != Destination.Home.route && route != Destination.Questionnaire.PATTERN) TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                },
                actions = {
                    Text("LOCAL DEMO", modifier = Modifier.padding(end = 20.dp), style = MaterialTheme.typography.labelSmall)
                },
            )
        },
        bottomBar = {
            if (!isFullScreen) NavigationBar {
                listOf(Destination.Home to "Home", Destination.Households to "Households").forEach { (destination, label) ->
                    NavigationBarItem(
                        selected = route == destination.route,
                        onClick = {
                            if (destination == Destination.Home) nav.popBackStack(Destination.Home.route, inclusive = false)
                            else nav.navigate(destination.route) { launchSingleTop = true }
                        },
                        icon = { RegistryIcon(house = destination == Destination.Home) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("nav-$label"),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            NavHost(navController = nav, startDestination = Destination.Home.route) {
                composable(Destination.Questionnaire.PATTERN, arguments = listOf(
                    navArgument("householdId") { type = HouseholdIdNavType },
                    navArgument("section") { defaultValue = "setup" },
                    navArgument("member") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                )) { backStack ->
                    val id = requireNotNull(HouseholdIdNavType.get(requireNotNull(backStack.arguments), "householdId"))
                    val workspace: InterviewWorkspaceViewModel = viewModel(factory = viewModelFactory {
                        initializer { InterviewWorkspaceViewModel(id, repository, photos, profiles,
                            backStack.arguments?.getString("section") ?: "setup", backStack.arguments?.getString("member"), createSavedStateHandle()) }
                    })
                    InterviewWorkspaceScreen(workspace) { nav.popBackStack() }
                }
                composable(Destination.Home.route) {
                    HomeScreen(
                        state, profileState.copy(name = savedProfile.name, barangay = savedProfile.barangay),
                        onBrowse = { nav.navigate(Destination.Households.route) { launchSingleTop = true } },
                        onOpen = openHousehold,
                        onProfile = { nav.navigate(Destination.Profile.route) { launchSingleTop = true } },
                    )
                }
                composable(Destination.Profile.route) {
                    ProfileScreen(
                        profileState,
                        onName = profile::setName,
                        onBarangay = profile::setBarangay,
                        onSave = profile::save,
                        onPickPhoto = profile::savePhoto,
                        onRemovePhoto = profile::removePhoto,
                    )
                }
                composable(Destination.Households.route) {
                    HouseholdsScreen(state, registry::setQuery, registry::setFilter, registry::clearSearch, openHousehold) {
                        nav.navigate(Destination.HouseholdAdd.route) { launchSingleTop = true }
                    }
                }
                composable(Destination.HouseholdAdd.route) {
                    val edit: HouseholdEditViewModel = viewModel(factory = viewModelFactory {
                        initializer { HouseholdEditViewModel(null, repository, createSavedStateHandle()) }
                    })
                    val editState by edit.uiState.collectAsStateWithLifecycle()
                    HouseholdEditScreen(editState, edit::setHeadName, edit::setAddress, edit::setStatus, edit::save, edit::setArchived) {
                        edit.openNameDetails { nav.navigate(Destination.Questionnaire(edit.id, "household").route) }
                    }
                }
                composable(
                    Destination.HouseholdEdit.PATTERN,
                    arguments = listOf(navArgument(Destination.HouseholdEdit.ARGUMENT) { type = HouseholdIdNavType }),
                ) { backStack ->
                    val id = requireNotNull(HouseholdIdNavType.get(requireNotNull(backStack.arguments), Destination.HouseholdEdit.ARGUMENT))
                    val edit: HouseholdEditViewModel = viewModel(factory = viewModelFactory {
                        initializer { HouseholdEditViewModel(id, repository, createSavedStateHandle()) }
                    })
                    val editState by edit.uiState.collectAsStateWithLifecycle()
                    HouseholdEditScreen(editState, edit::setHeadName, edit::setAddress, edit::setStatus, edit::save, edit::setArchived) {
                        edit.openNameDetails { nav.navigate(Destination.Questionnaire(edit.id, "household").route) }
                    }
                }
                composable(
                    Destination.MemberAdd.PATTERN,
                    arguments = listOf(navArgument(Destination.MemberAdd.ARGUMENT) { type = HouseholdIdNavType }),
                ) { backStack ->
                    val householdId = requireNotNull(HouseholdIdNavType.get(requireNotNull(backStack.arguments), Destination.MemberAdd.ARGUMENT))
                    val edit: MemberEditViewModel = viewModel(factory = viewModelFactory {
                        initializer { MemberEditViewModel(householdId, null, repository, createSavedStateHandle()) }
                    })
                    val editState by edit.uiState.collectAsStateWithLifecycle()
                    if (editState.removed) LaunchedEffect(editState.removed) { nav.popBackStack() }
                    MemberEditScreen(editState, edit::setDisplayName, edit::save, edit::remove) {
                        edit.openNameDetails { nav.navigate(Destination.Questionnaire(householdId, "demographics", edit.id).route) }
                    }
                }
                composable(
                    Destination.MemberEdit.PATTERN,
                    arguments = listOf(
                        navArgument(Destination.MemberEdit.HOUSEHOLD_ARGUMENT) { type = HouseholdIdNavType },
                        navArgument(Destination.MemberEdit.ARGUMENT) { type = HouseholdIdNavType },
                    ),
                ) { backStack ->
                    val arguments = requireNotNull(backStack.arguments)
                    val householdId = requireNotNull(HouseholdIdNavType.get(arguments, Destination.MemberEdit.HOUSEHOLD_ARGUMENT))
                    val memberId = MemberId(requireNotNull(HouseholdIdNavType.get(arguments, Destination.MemberEdit.ARGUMENT)).value)
                    val edit: MemberEditViewModel = viewModel(factory = viewModelFactory {
                        initializer { MemberEditViewModel(householdId, memberId, repository, createSavedStateHandle()) }
                    })
                    val editState by edit.uiState.collectAsStateWithLifecycle()
                    if (editState.removed) LaunchedEffect(editState.removed) { nav.popBackStack() }
                    MemberEditScreen(editState, edit::setDisplayName, edit::save, edit::remove) {
                        edit.openNameDetails { nav.navigate(Destination.Questionnaire(householdId, "demographics", edit.id).route) }
                    }
                }
                composable(
                    Destination.Interview.PATTERN,
                    arguments = listOf(navArgument(Destination.Interview.ARGUMENT) { type = HouseholdIdNavType }),
                ) { backStack ->
                    val id = requireNotNull(HouseholdIdNavType.get(requireNotNull(backStack.arguments), Destination.Interview.ARGUMENT))
                    val interview: InterviewViewModel = viewModel(factory = viewModelFactory {
                        initializer { InterviewViewModel(id, repository, createSavedStateHandle()) }
                    })
                    val interviewState by interview.uiState.collectAsStateWithLifecycle()
                    InterviewScreen(
                        interviewState,
                        interview::selectTab,
                        onBack = { nav.popBackStack() },
                        onPractice = { nav.navigate(Destination.Practice(id).route) { launchSingleTop = true } },
                        onEditHousehold = { nav.navigate(Destination.HouseholdEdit(id).route) { launchSingleTop = true } },
                        onAddMember = { nav.navigate(Destination.MemberAdd(id).route) { launchSingleTop = true } },
                        onEditMember = { memberId -> nav.navigate(Destination.MemberEdit(id, memberId).route) { launchSingleTop = true } },
                        onQuestionnaire = { nav.navigate(Destination.Questionnaire(id).route) },
                    )
                }
                composable(Destination.Practice.PATTERN, arguments = listOf(navArgument(Destination.Interview.ARGUMENT) { type = HouseholdIdNavType })) { backStack ->
                    val id = requireNotNull(HouseholdIdNavType.get(requireNotNull(backStack.arguments), Destination.Interview.ARGUMENT))
                    val household = state.households.find { it.id == id }
                    if (household != null) PracticeFormScreen(household, formState, forms::answer, forms::check)
                    else Text(if (state.loading) "Loading household…" else "Household unavailable", Modifier.padding(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(state: RegistryUiState, profile: EnumeratorProfileUiState, onBrowse: () -> Unit, onOpen: (HouseholdId) -> Unit, onProfile: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home-screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Magandang araw, ${profile.name}.", style = MaterialTheme.typography.titleMedium)
            Text("Your fieldwork workspace · ${profile.barangay}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onProfile, modifier = Modifier.heightIn(min = 48.dp).testTag("open-profile")) { Text("Edit enumerator profile") }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    RegistryIcon(house = true, modifier = Modifier.size(34.dp))
                    Text("Small visits.\nA fuller picture.", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.headlineLarge)
                    Text("Find a household and pick up where you left off.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Households", state.households.size, Modifier.weight(1f))
                    Metric("Members", state.memberCount, Modifier.weight(1f))
                    Metric("Callbacks", state.callbackCount, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Male", state.maleCount, Modifier.weight(1f))
                    Metric("Female", state.femaleCount, Modifier.weight(1f))
                    Metric("Sex not recorded", state.unrecordedSexCount, Modifier.weight(1f))
                }
            }
            item { Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Browse households") } }
            item { Text("Continue an interview", style = MaterialTheme.typography.titleLarge) }
            items(state.households.take(2), key = { it.id.value }) { HouseholdCard(it, onOpen) }
            if (state.households.isEmpty()) item { Text("No households available.") }
        }
        item { DemoNotice() }
    }
}

@Composable
private fun HouseholdsScreen(
    state: RegistryUiState,
    onQuery: (String) -> Unit,
    onFilter: (HouseholdFilter) -> Unit,
    onClear: () -> Unit,
    onOpen: (HouseholdId) -> Unit,
    onAdd: () -> Unit,
) {
    // Search and results share a scroll container so a keyboard or large type cannot trap content.
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("households-screen"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Your households", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Find the right record before you begin.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add-household")) {
                Text("Register a household")
            }
        }
        item {
            OutlinedTextField(
                value = state.query, onValueChange = onQuery,
                label = { Text("Search households") }, placeholder = { Text("Name, ID or address") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("household-search"),
                trailingIcon = { if (state.query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") } },
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HouseholdFilter.entries.forEach { filter ->
                    FilterChip(selected = filter == state.filter, onClick = { onFilter(filter) }, label = { Text(filter.label) })
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else {
            item { Text("${state.visibleHouseholds.size} of ${state.households.size} households", style = MaterialTheme.typography.labelLarge) }
            items(state.visibleHouseholds, key = { it.id.value }) { HouseholdCard(it, onOpen) }
            if (state.visibleHouseholds.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No matching households", style = MaterialTheme.typography.titleMedium)
                    Text("Try another name, household ID or status.")
                    OutlinedButton(onClick = onClear) { Text("Clear search and filters") }
                }
            }
        }
        item { DemoNotice() }
    }
}

@Composable
private fun InterviewScreen(
    state: InterviewUiState,
    onTab: (InterviewTab) -> Unit,
    onBack: () -> Unit,
    onPractice: () -> Unit,
    onEditHousehold: () -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (MemberId) -> Unit,
    onQuestionnaire: () -> Unit,
) {
    val household = state.household
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (household == null) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Household unavailable", style = MaterialTheme.typography.headlineSmall)
            Text("This record is no longer in the local household list.")
            Button(onClick = onBack) { Text("Back to households") }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("interview-screen"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(household.displayCode, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("record-id"))
            Spacer(Modifier.height(8.dp))
            Text(household.headName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(household.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (household.archived) {
                Text("Archived · still recoverable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InterviewTab.entries.forEach { tab ->
                    FilterChip(selected = state.tab == tab, onClick = { onTab(tab) }, label = { Text(tab.label) }, modifier = Modifier.testTag("tab-${tab.name}"))
                }
            }
        }
        when (state.tab) {
            InterviewTab.Overview -> {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Household overview", style = MaterialTheme.typography.titleLarge)
                            Text("Status · ${household.status.label}")
                            Text("${household.members.size} household members")
                            Text("Enumerator · ${state.enumeratorName ?: "Not recorded yet"}")
                        }
                    }
                }
                item { Button(onClick = onEditHousehold, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("edit-household")) { Text("Edit household profile") } }
                item { Button(onClick = onQuestionnaire, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open-questionnaire")) { Text("Continue questionnaire") } }
                item { Button(onClick = { onTab(InterviewTab.Members) }, modifier = Modifier.fillMaxWidth()) { Text("View members") } }
                item { OutlinedButton(onClick = { onTab(InterviewTab.Sections) }, modifier = Modifier.fillMaxWidth()) { Text("View interview sections") } }
                item { OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().testTag("open-practice")) { Text("Try a practice interview") } }
            }
            InterviewTab.Members -> {
                item { Text("Household members", style = MaterialTheme.typography.titleLarge) }
                items(household.members, key = { it.id.value }) { member ->
                    Surface(
                        onClick = { onEditMember(member.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("member-${member.id.value}"),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { RegistryIcon(house = false) }
                            Column(Modifier.weight(1f)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("Tap to edit this member", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (household.members.isEmpty()) item { Text("No members in this sample household.") }
                item {
                    Button(onClick = onAddMember, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add-member")) {
                        Text("Add household member")
                    }
                }
            }
            InterviewTab.Sections -> {
                item {
                    Text("Interview sections", style = MaterialTheme.typography.titleLarge)
                    Text("Answer every section in the questionnaire workspace. Try the practice interview to check the shared controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-practice")) { Text("Try a practice interview") }
                    Button(onClick = onQuestionnaire, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open-questionnaire-sections")) { Text("Continue questionnaire") }
                }
                item {
                    Text("The questionnaire covers all 18 sections:", style = MaterialTheme.typography.titleMedium)
                }
                items(SectionTitles.withIndex().toList(), key = { "title-" + it.index }) { (index, title) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text((index + 1).toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item { DemoNotice() }
    }
}

@Composable
private fun HouseholdCard(household: Household, onOpen: (HouseholdId) -> Unit) {
    Card(
        onClick = { onOpen(household.id) }, modifier = Modifier.fillMaxWidth().testTag("household-${household.displayCode}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { RegistryIcon(true) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(household.headName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(household.displayCode, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                Text(household.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (household.archived) "Archived" else household.status.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (household.archived) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), modifier = Modifier.testTag("metric-$label"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoNotice() {
    Text("Fictional sample records · Saves stay on this device.\nQuestionnaire answers, pictures, signatures and the enumerator profile save here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Editable enumerator profile (plan card D08): name, barangay and an app-private profile picture. */
@Composable
private fun ProfileScreen(
    state: EnumeratorProfileUiState,
    onName: (String) -> Unit,
    onBarangay: (String) -> Unit,
    onSave: () -> Unit,
    onPickPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPickPhoto) }
    Column(
        Modifier.fillMaxSize().testTag("profile-screen").verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Enumerator profile", style = MaterialTheme.typography.titleLarge)
        Text("Changes stamp new interviews; saved records keep their own attribution.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.name, onValueChange = onName, enabled = !state.saving,
            label = { Text("Enumerator name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-profileName"),
        )
        OutlinedTextField(
            value = state.barangay, onValueChange = onBarangay, enabled = !state.saving,
            label = { Text("Barangay") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("field-profileBarangay"),
        )
        PhotoPreview(state.photoPath, "Saved enumerator picture")
        Text("JPEG, PNG or WebP · up to 5 MiB", style = MaterialTheme.typography.bodySmall)
        Text(
            if (state.photoSaved) "A profile picture is saved on this device." else "No profile picture yet.",
            modifier = Modifier.testTag("profile-photo-status"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !state.saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("profile-photo-choose"),
            ) { Text(if (state.photoSaved) "Replace picture" else "Choose picture") }
            OutlinedButton(
                onClick = onRemovePhoto, enabled = state.photoSaved && !state.saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("profile-photo-remove"),
            ) { Text("Remove picture") }
        }
        if (state.phase == ph.tala.registry.ui.households.SavePhase.Idle && state.message.isNotBlank()) Text(state.message)
        SaveStatusLine(state.phase, state.message)
        Button(
            onClick = onSave, enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save-profile"),
        ) { Text(if (state.saving) "Saving…" else "Save profile") }
    }
}

@Composable
private fun RegistryIcon(house: Boolean, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        if (house) {
            line(.1f, .45f, .5f, .12f); line(.5f, .12f, .9f, .45f)
            line(.22f, .42f, .22f, .88f); line(.78f, .42f, .78f, .88f); line(.22f, .88f, .78f, .88f)
            line(.44f, .88f, .44f, .62f); line(.44f, .62f, .59f, .62f); line(.59f, .62f, .59f, .88f)
        } else for (y in listOf(.25f, .5f, .75f)) { line(.15f, y, .23f, y); line(.4f, y, .85f, y) }
    }
}

private val SectionTitles = listOf(
    "Survey setup", "Visit information", "Household profile", "Household members", "Demographics",
    "Other demographics", "Migration", "Education", "Employment", "Health", "Food security",
    "Transport & finance", "Disaster preparedness", "Internet & public safety", "Social protection",
    "Water & sanitation", "Housing characteristics", "Consent & review",
)
