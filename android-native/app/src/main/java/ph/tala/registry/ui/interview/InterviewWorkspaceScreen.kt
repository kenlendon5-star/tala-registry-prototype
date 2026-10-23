package ph.tala.registry.ui.interview

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.FieldType
import ph.tala.registry.domain.model.SectionSpec
import ph.tala.registry.domain.model.scalar
import ph.tala.registry.ui.form.SectionFormState
import ph.tala.registry.ui.form.SharedField
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.households.SaveStatusLine
import ph.tala.registry.ui.PhotoPreview
import ph.tala.registry.data.HouseholdRepository

private val WideLayoutBreakpoint = 840.dp
/** Two-pane only when the viewport is both wide AND tall enough to keep the form readable (UX-03). */
private val TwoPaneMinHeight = 560.dp

@Composable
fun InterviewWorkspaceScreen(model: InterviewWorkspaceViewModel, onExit: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler { model.leave(onExit) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("interview-workspace")) {
        val compact = maxHeight < TwoPaneMinHeight || LocalDensity.current.fontScale >= 1.5f
        val pageScroll = rememberScrollState()
        LaunchedEffect(state.route, state.memberId, state.showingReview) { pageScroll.scrollTo(0) }
        Column(if (compact) Modifier.fillMaxWidth().verticalScroll(pageScroll) else Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { model.leave(onExit) }) { Text("Save & Back") }
                TextButton(onClick = model::review) { Text("Review Interview") }
            }
            if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@BoxWithConstraints }
            if (state.record == null) { Text(state.message, Modifier.padding(20.dp)); return@BoxWithConstraints }
            val statuses = remember(state.record) { sectionStatuses(state.record) }
            val sections = remember { QuestionnaireSections.all.filter { it.route != "members" } }
            BoxWithConstraints(if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
                // Short/large-text pages share one scroll container, including navigation.
                // No fixed chrome can take the form's height away when the IME opens.
                if (!compact && maxWidth >= WideLayoutBreakpoint && maxHeight >= TwoPaneMinHeight) {
                    TwoPaneWorkspace(state, model, onExit, sections, statuses)
                } else {
                    SinglePaneWorkspace(state, model, onExit, sections, statuses, compact)
                }
            }
        }
    }
}

/** Phone layout: use a single scrolling page at short heights or large text. */
@Composable
private fun SinglePaneWorkspace(
    state: WorkspaceState,
    model: InterviewWorkspaceViewModel,
    onExit: () -> Unit,
    sections: List<SectionSpec>,
    statuses: Map<String, SectionStatus>,
    compact: Boolean,
) {
    var directoryOpen by remember { mutableStateOf(false) }
    Column(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { directoryOpen = true }, modifier = Modifier.size(40.dp).testTag("section-directory")) {
                MenuGlyph(Modifier.semantics { contentDescription = "Open section directory" })
            }
            sections.forEach { section ->
                FilterChip(
                    modifier = Modifier.testTag("section-${section.route}").sectionReadiness(statuses[section.route]),
                    selected = !state.showingReview && state.route == section.route,
                    onClick = { model.select(section.route) },
                    leadingIcon = { SectionStatusIcon(statuses[section.route]) },
                    label = { Text(section.title) },
                )
            }
        }
        if (!state.showingReview && state.section.scopedToMember) {
            MemberChipsRow(state, model)
        }
        WorkspaceBody(state, model, onExit,
            if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
            scrollBody = !compact)
        WorkspaceBottomBar(state, model, sections)
    }
    if (directoryOpen) {
        SectionDirectorySheet(
            state, sections, statuses,
            onSelect = { route -> directoryOpen = false; if (route == "members") model.leave(onExit) else model.select(route) },
            onDismiss = { directoryOpen = false },
        )
    }
}

/** Tablet/landscape layout: the section index and member selector stay visible beside the form. */
@Composable
private fun TwoPaneWorkspace(
    state: WorkspaceState,
    model: InterviewWorkspaceViewModel,
    onExit: () -> Unit,
    sections: List<SectionSpec>,
    statuses: Map<String, SectionStatus>,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            // Member scope is pinned above the scrollable index so the active member and the
            // switcher stay discoverable without scrolling past the whole list (UX-10).
            if (!state.showingReview && state.section.scopedToMember) {
                MemberScopeIndex(state, model, Modifier.heightIn(max = 240.dp))
                HorizontalDivider()
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Sections", style = MaterialTheme.typography.titleMedium)
                sections.forEach { section ->
                    val selected = !state.showingReview && state.route == section.route
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("section-${section.route}")
                                .selectable(selected = selected, onClick = { model.select(section.route) })
                                .sectionReadiness(statuses[section.route])
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(canonicalSectionNumber(section.route).toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            Text(section.title, Modifier.weight(1f))
                            SectionStatusIcon(statuses[section.route])
                        }
                    }
                }
                MembersIndexRow(bordered = true, onOpen = { model.leave(onExit) })
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            WorkspaceBody(state, model, onExit, Modifier.weight(1f).fillMaxWidth())
            WorkspaceBottomBar(state, model, sections)
        }
    }
}

/** Pinned member switcher for the wide index. Selection is shown with a neutral dot — never a
 *  readiness check — so a selected member is never read as "done" (UX-02). */
@Composable
private fun MemberScopeIndex(state: WorkspaceState, model: InterviewWorkspaceViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Member Scope", style = MaterialTheme.typography.titleMedium)
        state.record!!.members.forEach { member ->
            val selected = state.memberId == member.id
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("member-${member.id}")
                        .selectable(selected = selected, onClick = { model.select(state.route, member.id) })
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(member.displayName, Modifier.weight(1f))
                    if (selected) SelectionDot()
                }
            }
        }
    }
}

/** Neutral "currently selected" cue — deliberately not a check mark, which reads as "done" (UX-02). */
@Composable
private fun SelectionDot(modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(16.dp)) { drawCircle(tint, radius = size.minDimension / 3.5f) }
}

/** The Members editor is section 04 in the canonical numbering but has no workspace form, so the
 *  numbered indexes would otherwise jump 03 → 05 with no trace of it. The row names the real
 *  destination and routes through save-before-exit; it never claims a readiness state. */
@Composable
private fun MembersIndexRow(bordered: Boolean, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("index-members")
                .selectable(selected = false, onClick = onOpen)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(canonicalSectionNumber("members").toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Household members")
                Text("Saves your draft, then opens the Members tab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MemberChipsRow(state: WorkspaceState, model: InterviewWorkspaceViewModel) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.record!!.members.forEach { member ->
            FilterChip(
                modifier = Modifier.testTag("member-${member.id}"),
                selected = state.memberId == member.id,
                onClick = { model.select(state.route, member.id) },
                label = { Text(member.displayName) },
            )
        }
    }
}

/** The review pane or the active section form; re-keyed whenever scope or mode changes. */
@Composable
private fun WorkspaceBody(
    state: WorkspaceState,
    model: InterviewWorkspaceViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBody: Boolean = true,
) {
    key(state.route, state.memberId, state.showingReview) {
        Column(
            modifier.then(if (scrollBody) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.showingReview) {
                ReviewContent(state, model, onExit)
            } else if (state.section.scopedToMember && state.memberId == null) {
                Text("This section is answered for each household member.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Save your draft, then go back and open the Members tab to add a household member. " +
                        "Return here and choose a member to answer these questions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { model.leave(onExit) }, modifier = Modifier.testTag("leave-to-add-member")) {
                    Text("Save and Go Back")
                }
            } else {
                Text(state.section.title, style = MaterialTheme.typography.headlineSmall)
                if (state.route == "consent") Text("Fictional-data demonstration only. Explain the purpose of the demo and ask whether the respondent agrees. Refusal is saved and never marked reviewed. A typed fictional name is a signature for this demo.")
                if (state.attempt > 0 && state.errors.isNotEmpty()) ErrorSummary(state)
                if (state.route == "consent" && state.answers.scalar("consent") == "Yes") {
                    SignatureColumn(model)
                }
                val form = SectionFormState(state.answers, state.errors, state.attempt)
                val visible = state.section.visibleFields(state.answers)
                val firstError = visible.firstOrNull { it.key in state.errors }?.key
                visible.forEach { field ->
                    key(field.key) {
                        if (field.type == FieldType.Photo) {
                            if (state.route == "household") {
                                HouseholdPhotoSlot(model)
                            } else if (state.section.scopedToMember) {
                                MemberPhotoSlot(model)
                            } else {
                                SharedField(field, form, field.key == firstError) { key, value -> model.answer(key, value) }
                            }
                        } else {
                            SharedField(field, form, field.key == firstError) { key, value -> model.answer(key, value) }
                        }
                    }
                }
                if (state.route == "visit") {
                    Button(onClick = model::recordVisit, enabled = state.phase != SavePhase.Saving) { Text("Record Visit Result") }
                    Text("Save draft keeps your edits. Record visit result adds a dated entry to the visit history.", style = MaterialTheme.typography.bodySmall)
                    Text("Visit History", style = MaterialTheme.typography.titleMedium)
                    if (state.record!!.visits.isEmpty()) Text("No recorded visits yet.")
                    state.record!!.visits.sortedByDescending { it.recordedAt }.forEach { visit ->
                        Column(Modifier.fillMaxWidth().testTag("visit-${visit.id}")) {
                            Text("${visit.visitedOn.ifBlank { "Date unrecorded" }} · ${visit.outcome}")
                            visit.callbackAt?.let { Text("Callback appointment: $it") }
                            if (visit.notes.isNotBlank()) Text(visit.notes)
                        }
                    }
                }
            }
        }
    }
}

/** One prominent count at the top so the first highlighted field is never a surprise. */
@Composable
private fun ErrorSummary(state: WorkspaceState) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().testTag("error-summary"),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionStatusIcon(SectionStatus.NeedsAttention)
            Text(
                "${state.errors.size} answers need attention. The first one is highlighted below.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Review issues grouped the way enumerators fix them: whole-household answers first, then per member. */
@Composable
private fun ReviewContent(state: WorkspaceState, model: InterviewWorkspaceViewModel, onExit: () -> Unit) {
    Text("Review Interview", style = MaterialTheme.typography.headlineSmall)
    Text("${state.progress.ready} of ${state.progress.total} sections ready. This records local completion only.")
    LinearProgressIndicator(
        progress = { if (state.progress.total == 0) 0f else state.progress.ready.toFloat() / state.progress.total },
        modifier = Modifier.fillMaxWidth().testTag("review-progress"),
    )
    val householdIssues = state.issues.filter { it.memberId == null }
    val memberIssues = state.issues.filter { it.memberId != null }
    if (householdIssues.isNotEmpty()) {
        Text("Household Scope", style = MaterialTheme.typography.titleMedium)
        householdIssues.forEach { issue -> IssueButton(issue) { if (issue.route == "members") model.leave(onExit) else model.select(issue.route, issue.memberId) } }
    }
    if (memberIssues.isNotEmpty()) {
        Text("Member Scope", style = MaterialTheme.typography.titleMedium)
        memberIssues.forEach { issue -> IssueButton(issue) { if (issue.route == "members") model.leave(onExit) else model.select(issue.route, issue.memberId) } }
    }
    if (state.issues.isEmpty()) {
        Text("Every section is ready. Marking the interview reviewed records local completion only.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = model::complete, enabled = state.issues.isEmpty() && state.phase != SavePhase.Saving) { Text("Mark Interview Reviewed") }
}

@Composable
private fun IssueButton(issue: ph.tala.registry.domain.form.SectionIssue, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("${issue.label}: ${issue.errors.size} missing or invalid answers")
    }
}

/** Sticky bottom navigation: previous/next section, position, then the save controls. */
@Composable
private fun WorkspaceBottomBar(state: WorkspaceState, model: InterviewWorkspaceViewModel, sections: List<SectionSpec>) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!state.showingReview) {
                val index = sections.indexOfFirst { it.route == state.route }.coerceAtLeast(0)
                // Reflow the footer at large font (UX-03): the counter gets its own line instead
                // of squeezing between the buttons and splitting mid-phrase.
                if (LocalDensity.current.fontScale >= 1.5f) {
                    Text(
                        "Section ${canonicalSectionNumber(state.route)} of ${QuestionnaireSections.all.size}",
                        Modifier.fillMaxWidth().testTag("section-position"),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviousSectionButton(model, sections, index, Modifier.weight(1f))
                        NextSectionButton(model, sections, index, Modifier.weight(1f))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PreviousSectionButton(model, sections, index)
                        Text(
                            "Section ${canonicalSectionNumber(state.route)} of ${QuestionnaireSections.all.size}",
                            Modifier.weight(1f).testTag("section-position"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        NextSectionButton(model, sections, index)
                    }
                }
            }
            if (state.phase == SavePhase.Idle && state.message.isNotBlank()) Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SaveStatusLine(state.phase, state.message)
            if (state.phase == SavePhase.Failed) TextButton(onClick = model::reload) { Text("Discard unsaved edits and reload saved section") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = model::check, enabled = !state.showingReview) { Text("Check Answers") }
                Button(onClick = model::save, enabled = state.phase != SavePhase.Saving) { Text("Save Draft") }
            }
        }
    }
}

/** Jump-to-any-section sheet: every section row carries the same status cue as its chip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionDirectorySheet(
    state: WorkspaceState,
    sections: List<SectionSpec>,
    statuses: Map<String, SectionStatus>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("All Sections", style = MaterialTheme.typography.titleLarge)
            Text("Jump straight to any part of the questionnaire.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            sections.forEach { section ->
                val selected = !state.showingReview && state.route == section.route
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .testTag("directory-${section.route}")
                            .selectable(selected = selected, onClick = { onSelect(section.route) })
                            .sectionReadiness(statuses[section.route])
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(canonicalSectionNumber(section.route).toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(section.title, Modifier.weight(1f))
                        SectionStatusIcon(statuses[section.route])
                    }
                }
            }
            MembersIndexRow(bordered = false, onOpen = { onSelect("members") })
        }
    }
}

@Composable
private fun SectionStatusIcon(status: SectionStatus?) {
    if (status == null) return
    val tint = when (status) {
        SectionStatus.Ready -> MaterialTheme.colorScheme.primary
        SectionStatus.NeedsAttention -> MaterialTheme.colorScheme.error
        SectionStatus.NotStarted -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Drawn glyphs, not icon fonts: this build ships no material-icons artifact.
    Canvas(Modifier.size(16.dp).testTag("section-status-$status")) {
        val stroke = 1.5.dp.toPx()
        fun path(build: Path.() -> Unit) = Path().apply(build)
        when (status) {
            SectionStatus.Ready -> {
                drawCircle(tint, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                drawPath(path({
                    moveTo(size.width * 0.26f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.70f)
                    lineTo(size.width * 0.76f, size.height * 0.30f)
                }), tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            SectionStatus.NeedsAttention -> {
                drawPath(path({
                    moveTo(size.width * 0.5f, size.height * 0.08f)
                    lineTo(size.width * 0.95f, size.height * 0.90f)
                    lineTo(size.width * 0.05f, size.height * 0.90f)
                    close()
                }), tint, style = Stroke(stroke, join = StrokeJoin.Round))
                drawLine(tint, Offset(size.width * 0.5f, size.height * 0.42f), Offset(size.width * 0.5f, size.height * 0.66f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawCircle(tint, radius = stroke * 0.9f, center = Offset(size.width * 0.5f, size.height * 0.82f))
            }
            SectionStatus.NotStarted -> drawCircle(tint, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        }
    }
}

/** Readiness as a spoken state, kept separate from navigation selection (UX-01). */
private val SectionStatus.announced: String
    get() = when (this) {
        SectionStatus.Ready -> "Ready"
        SectionStatus.NeedsAttention -> "Needs Attention"
        SectionStatus.NotStarted -> "Not Started"
    }

/** Exposes real section readiness to TalkBack without folding it into the selected state (UX-01). */
private fun Modifier.sectionReadiness(status: SectionStatus?): Modifier =
    semantics { status?.let { stateDescription = it.announced } }

/** Canonical 1-based index in the full questionnaire so every list agrees on numbering (UX-04). */
internal fun canonicalSectionNumber(route: String): Int =
    QuestionnaireSections.all.indexOfFirst { it.route == route } + 1

/** Previous/next are plain words; the chevron glyphs are hidden from assistive tech (UX-08). */
@Composable
private fun PreviousSectionButton(model: InterviewWorkspaceViewModel, sections: List<SectionSpec>, index: Int, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = { model.select(sections[index - 1].route) }, enabled = index > 0, modifier = modifier.testTag("section-prev")) {
        Text("‹", Modifier.clearAndSetSemantics { })
        Text(" Previous")
    }
}

@Composable
private fun NextSectionButton(model: InterviewWorkspaceViewModel, sections: List<SectionSpec>, index: Int, modifier: Modifier = Modifier) {
    Button(onClick = { model.select(sections[index + 1].route) }, enabled = index < sections.lastIndex, modifier = modifier.testTag("section-next")) {
        Text("Next ")
        Text("›", Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun MenuGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        for (y in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(color, Offset(0.15f * size.width, y * size.height), Offset(0.85f * size.width, y * size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** The consent signature slot (plan card D13): drawn pad with undo/clear plus the typed name field. */
@Composable
private fun SignatureColumn(model: InterviewWorkspaceViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val saved = state.record?.attachments?.find { it.kind == HouseholdRepository.SIGNATURE }?.relativePath
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Respondent Signature (Drawn)", style = MaterialTheme.typography.titleSmall)
        PhotoPreview(saved, "Saved drawn signature")
        SignaturePad(
            hasSavedSignature = model.hasSignature,
            onSave = model::saveSignature,
            onClear = model::clearSignature,
        )
        Text(
            "Accessible alternative: type a fictional name in the signature field instead of drawing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The household picture slot (plan card D08): pick, replace and remove through app-private storage. */
@Composable
private fun HouseholdPhotoSlot(model: InterviewWorkspaceViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(model::savePhoto) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Household Profile Picture (Optional)", style = MaterialTheme.typography.titleSmall)
        PhotoPreview(state.record?.attachments?.find { it.kind == HouseholdRepository.HOUSEHOLD_PHOTO }?.relativePath, "Saved household picture")
        Text("JPEG, PNG or WebP · up to 5 MiB", style = MaterialTheme.typography.bodySmall)
        Text(
            if (model.hasHouseholdPhoto) "A picture is saved on this device." else "No picture saved yet.",
            modifier = Modifier.testTag("photo-status"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("photo-choose"),
            ) { Text(if (model.hasHouseholdPhoto) "Replace Picture" else "Choose Picture") }
            OutlinedButton(
                onClick = model::removePhoto,
                enabled = model.hasHouseholdPhoto && state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("photo-remove"),
            ) { Text("Remove Picture") }
        }
    }
}

/** The member picture slot (plan card D08): pick, replace and remove through app-private storage. */
@Composable
private fun MemberPhotoSlot(model: InterviewWorkspaceViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var pickerMember by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val member = pickerMember
        pickerMember = null
        if (uri != null && member != null) model.saveMemberPhoto(uri, ph.tala.registry.domain.model.MemberId(member))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Member Profile Picture (Optional)", style = MaterialTheme.typography.titleSmall)
        PhotoPreview(state.record?.attachments?.find { it.kind == HouseholdRepository.MEMBER_PHOTO && it.memberId == state.memberId }?.relativePath, "Saved member picture")
        Text("JPEG, PNG or WebP · up to 5 MiB", style = MaterialTheme.typography.bodySmall)
        Text(
            if (model.hasMemberPhoto) "A picture is saved on this device." else "No picture saved yet.",
            modifier = Modifier.testTag("member-photo-status"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { pickerMember = state.memberId; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("member-photo-choose"),
            ) { Text(if (model.hasMemberPhoto) "Replace Picture" else "Choose Picture") }
            OutlinedButton(
                onClick = model::removeMemberPhoto,
                enabled = model.hasMemberPhoto && state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("member-photo-remove"),
            ) { Text("Remove Picture") }
        }
    }
}
