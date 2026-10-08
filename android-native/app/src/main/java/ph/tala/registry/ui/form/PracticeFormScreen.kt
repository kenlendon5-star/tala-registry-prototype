package ph.tala.registry.ui.form

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ph.tala.registry.domain.form.PracticeSection
import ph.tala.registry.domain.model.*

@Composable
fun PracticeFormScreen(household: Household, session: FormSessionState, onAnswer: (SectionSpec, AnswerScope, String, AnswerValue) -> Unit, onCheck: (SectionSpec, AnswerScope) -> Unit) {
    var selectedMember by rememberSaveable(household.id.value) { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val member = household.members.find { it.id.value == selectedMember }
    val scope = member?.let { AnswerScope.Member(household.id, it.id) } ?: AnswerScope.Household(household.id)
    val section = if (member == null) PracticeSection.household else PracticeSection.member
    Column(Modifier.fillMaxSize().testTag("practice-screen")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("${household.id.value} · ${household.headName}", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag("answer-scope")) {
                    Text("Answering for: ${member?.displayName ?: "Household"}", Modifier.weight(1f)); Text("▾")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Household") }, modifier = Modifier.testTag("scope-household"), onClick = { selectedMember = null; expanded = false })
                    household.members.forEach { person ->
                        DropdownMenuItem(text = { Text(person.displayName) }, modifier = Modifier.testTag("scope-${person.id.value}"), onClick = { selectedMember = person.id.value; expanded = false })
                    }
                }
            }
        }
        // Disposing the old controls cancels open pickers/focus effects on a scope switch.
        key(scope) {
            SectionForm(section, session.form(section, scope), onAnswer = { key, value -> onAnswer(section, scope, key, value) }, onCheck = { onCheck(section, scope) }, modifier = Modifier.weight(1f))
        }
    }
}
