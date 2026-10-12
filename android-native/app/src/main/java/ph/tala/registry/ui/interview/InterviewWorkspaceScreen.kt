package ph.tala.registry.ui.interview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ph.tala.registry.domain.form.QuestionnaireSections
import ph.tala.registry.domain.model.FieldType
import ph.tala.registry.ui.form.SectionFormState
import ph.tala.registry.ui.form.SharedField
import ph.tala.registry.ui.households.SavePhase
import ph.tala.registry.ui.households.SaveStatusLine

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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuestionnaireSections.all.filter { it.route != "members" }.forEach { section ->
                FilterChip(selected = !state.showingReview && state.route == section.route, onClick = { model.select(section.route) }, label = { Text(section.title) })
            }
        }
        if (!state.showingReview && state.section.scopedToMember) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.record!!.members.forEach { member ->
                    FilterChip(selected = state.memberId == member.id, onClick = { model.select(state.route, member.id) }, label = { Text(member.displayName) })
                }
            }
        }
        key(state.route, state.memberId, state.showingReview) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.showingReview) {
                    Text("Review interview", style = MaterialTheme.typography.headlineSmall)
                    Text("${state.issues.size} sections need attention. This records local completion only.")
                    state.issues.forEach { issue ->
                        OutlinedButton(onClick = { if (issue.route == "members") model.leave(onExit) else model.select(issue.route, issue.memberId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${issue.label}: ${issue.errors.size} missing or invalid answers")
                        }
                    }
                    Button(onClick = model::complete, enabled = state.issues.isEmpty() && state.phase != SavePhase.Saving) { Text("Mark interview reviewed") }
                } else if (state.section.scopedToMember && state.memberId == null) {
                    Text("Add a household member before answering these questions.")
                } else {
                    Text(state.section.title, style = MaterialTheme.typography.headlineSmall)
                    if (state.route == "consent") Text("Fictional-data demonstration only. Explain the purpose of the demo and ask whether the respondent agrees. Refusal is saved and never marked reviewed. A typed fictional name is a signature for this demo.")
                    val form = SectionFormState(state.answers, state.errors, state.attempt)
                    val visible = state.section.visibleFields(state.answers)
                    val firstError = visible.firstOrNull { it.key in state.errors }?.key
                    visible.forEach { field ->
                        key(field.key) { SharedField(field, form, field.key == firstError) { key, value -> model.answer(key, value) } }
                    }
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}
