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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
fun InterviewWorkspaceScreen(model: InterviewWorkspaceViewModel, onExit: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler { model.leave(onExit) }
    Column(Modifier.fillMaxSize().testTag("interview-workspace")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { model.leave(onExit) }) { Text("Save & back") }
            TextButton(onClick = model::review) { Text("Review interview") }
        }
        if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
        if (state.record == null) { Text(state.message, Modifier.padding(20.dp)); return }
        val statuses = remember(state.record) { sectionStatuses(state.record) }
        val sections = remember { QuestionnaireSections.all.filter { it.route != "members" } }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth >= WideLayoutBreakpoint) {
                TwoPaneWorkspace(state, model, onExit, sections, statuses)
            } else {
                SinglePaneWorkspace(state, model, onExit, sections, statuses)
            }
        }
    }
}

/** Phone layout: horizontal section chips, then the form, then the sticky navigation bar. */
@Composable
private fun SinglePaneWorkspace(
    state: WorkspaceState,
    model: InterviewWorkspaceViewModel,
    onExit: () -> Unit,
    sections: List<SectionSpec>,
    statuses: Map<String, SectionStatus>,
) {
    var directoryOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
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
                    modifier = Modifier.testTag("section-${section.route}"),
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
        WorkspaceBody(state, model, onExit, Modifier.weight(1f).fillMaxWidth())
        WorkspaceBottomBar(state, model, sections)
    }
    if (directoryOpen) {
        SectionDirectorySheet(
            state, sections, statuses,
            onSelect = { route -> directoryOpen = false; model.select(route) },
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
        Column(
            Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Sections", style = MaterialTheme.typography.titleMedium)
            sections.forEachIndexed { index, section ->
                val selected = !state.showingReview && state.route == section.route
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("section-${section.route}")
                            .selectable(selected = selected, onClick = { model.select(section.route) })
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text((index + 1).toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(section.title, Modifier.weight(1f))
                        SectionStatusIcon(statuses[section.route])
                    }
                }
            }
            if (!state.showingReview && state.section.scopedToMember) {
                HorizontalDivider()
                Text("Member scope", style = MaterialTheme.typography.titleMedium)
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
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(member.displayName, Modifier.weight(1f))
                            SectionStatusIcon(if (selected) SectionStatus.Ready else null)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            WorkspaceBody(state, model, onExit, Modifier.weight(1f).fillMaxWidth())
            WorkspaceBottomBar(state, model, sections)
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
) {
    key(state.route, state.memberId, state.showingReview) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (state.showingReview) {
                ReviewContent(state, model, onExit)
            } else if (state.section.scopedToMember && state.memberId == null) {
                Text("Add a household member before answering these questions.")
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
                    Button(onClick = model::recordVisit, enabled = state.phase != SavePhase.Saving) { Text("Record visit result") }
                    Text("Save draft keeps your edits. Record visit result adds a dated entry to the visit history.", style = MaterialTheme.typography.bodySmall)
                    Text("Visit history", style = MaterialTheme.typography.titleMedium)
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
    Text("Review interview", style = MaterialTheme.typography.headlineSmall)
    Text("${state.progress.ready} of ${state.progress.total} sections ready. This records local completion only.")
    LinearProgressIndicator(
        progress = { if (state.progress.total == 0) 0f else state.progress.ready.toFloat() / state.progress.total },
        modifier = Modifier.fillMaxWidth().testTag("review-progress"),
    )
    val householdIssues = state.issues.filter { it.memberId == null }
    val memberIssues = state.issues.filter { it.memberId != null }
    if (householdIssues.isNotEmpty()) {
        Text("Household scope", style = MaterialTheme.typography.titleMedium)
        householdIssues.forEach { issue -> IssueButton(issue) { if (issue.route == "members") model.leave(onExit) else model.select(issue.route, issue.memberId) } }
    }
    if (memberIssues.isNotEmpty()) {
        Text("Member scope", style = MaterialTheme.typography.titleMedium)
        memberIssues.forEach { issue -> IssueButton(issue) { if (issue.route == "members") model.leave(onExit) else model.select(issue.route, issue.memberId) } }
    }
    if (state.issues.isEmpty()) {
        Text("Every section is ready. Marking the interview reviewed records local completion only.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = model::complete, enabled = state.issues.isEmpty() && state.phase != SavePhase.Saving) { Text("Mark interview reviewed") }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { model.select(sections[index - 1].route) }, enabled = index > 0, modifier = Modifier.testTag("section-prev")) {
                        Text("‹ Previous")
                    }
                    Text(
                        "Section ${index + 1} of ${sections.size}",
                        Modifier.weight(1f).testTag("section-position"),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Button(onClick = { model.select(sections[index + 1].route) }, enabled = index < sections.lastIndex, modifier = Modifier.testTag("section-next")) {
                        Text("Next ›")
                    }
                }
            }
            if (state.phase == SavePhase.Idle && state.message.isNotBlank()) Text(state.message)
            SaveStatusLine(state.phase, state.message)
            if (state.phase == SavePhase.Failed) TextButton(onClick = model::reload) { Text("Discard unsaved edits and reload saved section") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = model::check, enabled = !state.showingReview) { Text("Check answers") }
                Button(onClick = model::save, enabled = state.phase != SavePhase.Saving) { Text("Save draft") }
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
            Text("All sections", style = MaterialTheme.typography.titleLarge)
            Text("Jump straight to any part of the questionnaire.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            sections.forEachIndexed { index, section ->
                val selected = !state.showingReview && state.route == section.route
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected = selected, onClick = { onSelect(section.route) })
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text((index + 1).toString().padStart(2, '0'), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                        Text(section.title, Modifier.weight(1f))
                        SectionStatusIcon(statuses[section.route])
                    }
                }
            }
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
        Text("Respondent signature (drawn)", style = MaterialTheme.typography.titleSmall)
        PhotoPreview(saved, "Saved drawn signature")
        SignaturePad(
            hasSavedSignature = model.hasSignature,
            onSave = model::saveSignature,
            onClear = model::clearSignature,
        )
        Text("Or type a fictional name as the signature below.", style = MaterialTheme.typography.bodySmall)
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
            ) { Text(if (model.hasHouseholdPhoto) "Replace picture" else "Choose picture") }
            OutlinedButton(
                onClick = model::removePhoto,
                enabled = model.hasHouseholdPhoto && state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("photo-remove"),
            ) { Text("Remove picture") }
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
            ) { Text(if (model.hasMemberPhoto) "Replace picture" else "Choose picture") }
            OutlinedButton(
                onClick = model::removeMemberPhoto,
                enabled = model.hasMemberPhoto && state.phase != SavePhase.Saving,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("member-photo-remove"),
            ) { Text("Remove picture") }
        }
    }
}
