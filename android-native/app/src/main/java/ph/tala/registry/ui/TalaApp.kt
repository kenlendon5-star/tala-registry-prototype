package ph.tala.registry.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import ph.tala.registry.data.HouseholdRepository
import ph.tala.registry.domain.model.Household
import ph.tala.registry.domain.model.HouseholdId
import ph.tala.registry.ui.form.FormSessionViewModel
import ph.tala.registry.ui.form.PracticeFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalaApp(repository: HouseholdRepository) {
    val registry: RegistryViewModel = viewModel(factory = viewModelFactory {
        initializer { RegistryViewModel(repository, createSavedStateHandle()) }
    })
    val state by registry.uiState.collectAsStateWithLifecycle()
    val forms: FormSessionViewModel = viewModel()
    val formState by forms.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: Destination.Home.route
    val isInterview = route == Destination.Interview.PATTERN || route == Destination.Practice.PATTERN
    val openHousehold: (HouseholdId) -> Unit = { nav.navigate(Destination.Interview(it).route) { launchSingleTop = true } }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tala", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text("BARANGAY COGON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    if (route != Destination.Home.route) TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                },
                actions = {
                    Text("LOCAL DEMO", modifier = Modifier.padding(end = 20.dp), style = MaterialTheme.typography.labelSmall)
                },
            )
        },
        bottomBar = {
            if (!isInterview) NavigationBar {
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
                composable(Destination.Home.route) {
                    HomeScreen(state, onBrowse = { nav.navigate(Destination.Households.route) { launchSingleTop = true } }, onOpen = openHousehold)
                }
                composable(Destination.Households.route) {
                    HouseholdsScreen(state, registry::setQuery, registry::setFilter, registry::clearSearch, openHousehold)
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
                    InterviewScreen(interviewState, interview::selectTab, onBack = { nav.popBackStack() }, onPractice = { nav.navigate(Destination.Practice(id).route) { launchSingleTop = true } })
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
private fun HomeScreen(state: RegistryUiState, onBrowse: () -> Unit, onOpen: (HouseholdId) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home-screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Magandang araw, Jamie.", style = MaterialTheme.typography.titleMedium)
            Text("Your fieldwork workspace", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun InterviewScreen(state: InterviewUiState, onTab: (InterviewTab) -> Unit, onBack: () -> Unit, onPractice: () -> Unit) {
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
            Text(household.id.value, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("record-id"))
            Spacer(Modifier.height(8.dp))
            Text(household.headName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(household.address, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Enumerator · Jamie Dela Cruz")
                        }
                    }
                }
                item { Button(onClick = { onTab(InterviewTab.Members) }, modifier = Modifier.fillMaxWidth()) { Text("View members") } }
                item { OutlinedButton(onClick = { onTab(InterviewTab.Sections) }, modifier = Modifier.fillMaxWidth()) { Text("View interview sections") } }
                item { OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().testTag("open-practice")) { Text("Try a practice interview") } }
            }
            InterviewTab.Members -> {
                item { Text("Household members", style = MaterialTheme.typography.titleLarge) }
                items(household.members, key = { it.id.value }) { member ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(member.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("Sample member · Forms coming next", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (household.members.isEmpty()) item { Text("No members in this sample household.") }
            }
            InterviewTab.Sections -> {
                item {
                    Text("Interview sections", style = MaterialTheme.typography.titleLarge)
                    Text("Preview the interview structure. Try the practice interview to check the shared controls.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth().testTag("open-practice")) { Text("Try a practice interview") }
                }
                items(SectionTitles.withIndex().toList(), key = { it.index }) { (index, title) ->
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
        onClick = { onOpen(household.id) }, modifier = Modifier.fillMaxWidth().testTag("household-${household.id.value}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { RegistryIcon(true) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(household.headName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(household.id.value, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                Text(household.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(household.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoNotice() {
    Text("Fictional sample records · Read-only preview.\nHousehold editing and saved interviews arrive in a later build.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
