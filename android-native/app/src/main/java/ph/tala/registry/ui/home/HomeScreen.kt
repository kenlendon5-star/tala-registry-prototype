package ph.tala.registry.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ph.tala.registry.R
import ph.tala.registry.data.InterviewResume
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.Household
import ph.tala.registry.ui.RegistryUiState
import ph.tala.registry.ui.profile.EnumeratorProfileUiState
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)
private val FemaleColor = Color(0xFF83B8D7)
private fun String.titleWords() = split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

@Composable
fun HomeScreen(
    state: RegistryUiState,
    insights: HomeInsights,
    profile: EnumeratorProfileUiState,
    onOpenProfile: () -> Unit,
    onContinue: (InterviewResume) -> Unit,
    onRegister: () -> Unit,
    onBrowse: () -> Unit,
    onCallbacks: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.5f
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 1100.dp).fillMaxSize().testTag("home-screen"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                HomeHeader(profile, onOpenProfile)
                Spacer(Modifier.height(24.dp))
                Text("Magandang Araw, ${profile.name.trim().substringBefore(' ').ifBlank { "Enumerator" }}.",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Ready For Your Next Household Visit?", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.loading || insights.loading) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("home-loading")) }
            } else {
                val reference = insights.resume
                val household = state.households.find { it.id == reference?.householdId }
                if (household != null && reference != null) {
                    item { ContinueCard(household, reference) { onContinue(reference) } }
                } else if (state.households.isNotEmpty()) {
                    item {
                        HomeCard {
                            Text("Start A Household Visit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("Choose A Household To Begin Or Continue An Interview.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("browse-households")) {
                                Text("Browse Households")
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = onRegister, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("register-household")) {
                        Text("+", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(if (state.households.isEmpty()) "Register Your First Household" else "Register Household")
                    }
                }
                if (state.callbackCount > 0) item {
                    val dark = MaterialTheme.colorScheme.background.luminance() < .2f
                    Surface(onClick = onCallbacks, shape = RoundedCornerShape(14.dp),
                        color = if (dark) Color(0xFF392C1B) else Color(0xFFFFF5E7),
                        border = BorderStroke(1.dp, if (dark) Color(0xFF8E6632) else Color(0xFFEBD0A3)),
                        modifier = Modifier.fillMaxWidth().testTag("home-callbacks")) {
                        Row(Modifier.padding(16.dp).heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeGlyph("calendar", color = if (dark) Color(0xFFFFD69A) else Color(0xFF8E4900))
                            Text("${state.callbackCount} ${if (state.callbackCount == 1) "Callback" else "Callbacks"} To Follow Up",
                                Modifier.weight(1f), color = if (dark) Color(0xFFFFD69A) else Color(0xFF8E4900),
                                fontWeight = FontWeight.SemiBold)
                            HomeGlyph("arrow")
                        }
                    }
                }
                insights.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                if (state.households.isEmpty()) item {
                    Text("No Households Yet", style = MaterialTheme.typography.titleLarge)
                    Text("Register Your First Household To Begin Fieldwork.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Registry Overview", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onBrowse, modifier = Modifier.testTag("browse-all")) { Text("Browse All"); HomeGlyph("arrow", Modifier.padding(start = 6.dp).size(18.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    BoxWithConstraints {
                        val stack = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.5f
                        if (stack) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard("Households", state.households.size, "house", onBrowse, Modifier.fillMaxWidth())
                            SummaryCard("Callbacks", state.callbackCount, "calendar", onCallbacks, Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SummaryCard("Households", state.households.size, "house", onBrowse, Modifier.weight(1f))
                            SummaryCard("Callbacks", state.callbackCount, "calendar", onCallbacks, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                        ResidentsCard(state, Modifier.weight(1f))
                        PuroksCard(insights.puroks, state.households.size, Modifier.weight(1f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ResidentsCard(state)
                        PuroksCard(insights.puroks, state.households.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(profile: EnumeratorProfileUiState, onOpenProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.tala_registry_logo), contentDescription = "Tala Registry Logo",
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f)) {
            Text("Tala Registry", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(profile.barangay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val initials = profile.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .let { if (it.size > 1) listOf(it.first(), it.last()) else it }.joinToString("") { it.take(1) }.uppercase(Locale.ROOT)
        FilledIconButton(onClick = onOpenProfile, shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.size(48.dp).testTag("enumerator-row").semantics { contentDescription = "Open Enumerator Profile" }) {
            Text(initials.ifBlank { "?" }, color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ContinueCard(household: Household, reference: InterviewResume, onContinue: () -> Unit) {
    Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("continue-row")) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Continue Interview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeGlyph("house", Modifier.size(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(household.headName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(household.displayCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(household.status.label.titleWords(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val section = QuestionnaireSections.get(reference.section).title.titleWords()
                val member = household.members.find { it.id.value == reference.memberId }?.displayName
                Text(listOfNotNull(section, member).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onContinue, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("continue-interview")) {
                    Text("Continue Interview", Modifier.weight(1f))
                    HomeGlyph("arrow", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SummaryCard(label: String, count: Int, glyph: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.testTag("summary-$label"), shape = CardShape, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(16.dp).heightIn(min = 60.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            HomeGlyph(glyph)
            Column(Modifier.weight(1f)) {
                Text(count.toString(), Modifier.testTag("metric-$label"), style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResidentsCard(state: RegistryUiState, modifier: Modifier = Modifier) {
    HomeCard(modifier.testTag("residents-dashboard")) {
        Text("Recorded Residents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Across Active Households", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(horizontalAlignment = Alignment.End) {
                Text(state.memberCount.toString(), Modifier.testTag("metric-Residents"), style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Total", style = MaterialTheme.typography.labelMedium)
            }
        }
        val slices = listOf(
            Triple("Male", state.maleCount, MaterialTheme.colorScheme.primary),
            Triple("Female", state.femaleCount, FemaleColor),
            Triple("Sex Not Recorded", state.unrecordedSexCount, MaterialTheme.colorScheme.outlineVariant),
        )
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics {
                contentDescription = "Residents: ${state.maleCount} Male, ${state.femaleCount} Female, ${state.unrecordedSexCount} Sex Not Recorded"
            }) {
            slices.filter { it.second > 0 }.forEach { (_, count, color) ->
                Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
            }
        }
        BoxWithConstraints {
            val stacked = maxWidth < 330.dp || LocalDensity.current.fontScale >= 1.5f
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                slices.forEach { (label, count, color) -> ResidentCount(label, count, color, Modifier.fillMaxWidth(), true) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                slices.forEach { (label, count, color) -> ResidentCount(label, count, color, Modifier.weight(1f), false) }
            }
        }
        if (state.memberCount == 0) Text("No Residents Recorded Yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Based On Recorded Sex", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResidentCount(label: String, count: Int, color: Color, modifier: Modifier, horizontal: Boolean) {
    if (horizontal) Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), Modifier.testTag("metric-$label"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(count.toString(), Modifier.testTag("metric-$label"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PuroksCard(puroks: List<PurokCount>, total: Int, modifier: Modifier = Modifier) {
    HomeCard(modifier.testTag("purok-dashboard")) {
        Text("Households By Purok", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$total Active Households", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val max = puroks.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        puroks.forEach { group ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(group.count.toString(), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(group.count.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                        .background(if (group.unrecorded) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        if (puroks.isEmpty()) Text("No Households Recorded Yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small consistent outline glyphs; all associated actions have visible labels. */
@Composable
private fun HomeGlyph(kind: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        when (kind) {
            "arrow" -> {
                drawLine(color, Offset(w * .15f, h * .5f), Offset(w * .85f, h * .5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .55f, h * .2f), Offset(w * .85f, h * .5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * .55f, h * .8f), Offset(w * .85f, h * .5f), stroke, StrokeCap.Round)
            }
            "calendar" -> {
                drawRoundRect(color, Offset(w * .1f, h * .2f), androidx.compose.ui.geometry.Size(w * .8f, h * .7f),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(stroke))
                drawLine(color, Offset(w * .1f, h * .4f), Offset(w * .9f, h * .4f), stroke)
                listOf(.3f, .7f).forEach { x -> drawLine(color, Offset(w * x, h * .1f), Offset(w * x, h * .3f), stroke, StrokeCap.Round) }
            }
            else -> {
                val path = Path().apply {
                    moveTo(w * .1f, h * .45f); lineTo(w * .5f, h * .1f); lineTo(w * .9f, h * .45f)
                    lineTo(w * .9f, h * .9f); lineTo(w * .6f, h * .9f); lineTo(w * .6f, h * .62f)
                    lineTo(w * .4f, h * .62f); lineTo(w * .4f, h * .9f); lineTo(w * .1f, h * .9f); close()
                }
                drawPath(path, color, style = Stroke(stroke))
            }
        }
    }
}
