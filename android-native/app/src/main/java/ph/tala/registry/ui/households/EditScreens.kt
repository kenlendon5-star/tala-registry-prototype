package ph.tala.registry.ui.households

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ph.tala.registry.domain.model.HouseholdStatus

/** One shared status line so every edit screen reports saving, saved and failed the same way. */
@Composable
fun SaveStatusLine(phase: SavePhase, message: String?, modifier: Modifier = Modifier) {
    if (phase == SavePhase.Idle) return
    val text = when (phase) {
        SavePhase.Saving -> "Saving…"
        SavePhase.Saved -> message ?: "Saved on this device."
        SavePhase.Failed -> message ?: "Could not save on this device."
        SavePhase.Idle -> return
    }
    Text(
        text,
        modifier = modifier.testTag("save-status").semantics { liveRegion = LiveRegionMode.Polite },
        color = when (phase) {
            SavePhase.Saved -> MaterialTheme.colorScheme.primary
            SavePhase.Failed -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
fun HouseholdEditScreen(
    state: HouseholdEditUiState,
    onHeadName: (String) -> Unit,
    onAddress: (String) -> Unit,
    onStatus: (HouseholdStatus) -> Unit,
    onSave: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onNameDetails: () -> Unit = {},
) {
    var confirmArchive by remember { mutableStateOf(false) }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (state.missing) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Household unavailable", style = MaterialTheme.typography.headlineSmall)
            Text("This record is no longer stored on this device. Go back to the household list.")
        }
        return
    }
    Column(Modifier.fillMaxSize().testTag("household-edit-screen")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                if (state.creating) "Register a Household" else "Edit Household",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                state.displayCode ?: "A display code is assigned when this household is first saved.",
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("edit-record-id"),
            )
            OutlinedTextField(
                value = state.headName, onValueChange = onHeadName, enabled = !state.saving, readOnly = state.structuredName,
                label = { Text("Household head") }, singleLine = true,
                isError = state.headNameError != null,
                supportingText = { state.headNameError?.let { Text(it, Modifier.testTag("hint-headName")) } },
                modifier = Modifier.fillMaxWidth().testTag("field-headName"),
            )
            if (!state.creating) {
                if (state.structuredName) Text("The full name is built from the saved name details.")
                OutlinedButton(onClick = onNameDetails, enabled = !state.saving, modifier = Modifier.testTag("edit-name-details")) { Text("Name Details") }
            }
            OutlinedTextField(
                value = state.address, onValueChange = onAddress, enabled = !state.saving,
                label = { Text("Address / purok") }, minLines = 2,
                isError = state.addressError != null,
                supportingText = { state.addressError?.let { Text(it, Modifier.testTag("hint-address")) } },
                modifier = Modifier.fillMaxWidth().testTag("field-address"),
            )
            Text("Visit status", style = MaterialTheme.typography.titleSmall)
            if (state.status == HouseholdStatus.Reviewed) Text("Reviewed on this device. Saving changed details reopens the interview for review.")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HouseholdStatus.entries.filterNot { it == HouseholdStatus.Reviewed }.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("status-${option.name}")
                            .selectable(selected = state.status == option, enabled = !state.saving, onClick = { onStatus(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.status == option, onClick = null)
                        Text(option.label, Modifier.padding(start = 12.dp))
                    }
                }
            }
            if (!state.creating) {
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("archive-household"),
                ) { Text(if (state.archived) "Restore Household" else "Archive Household") }
                Text(
                    "Archiving keeps members, answers and pictures; it only removes the record from active counts and stays reversible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SaveStatusLine(state.phase, state.message)
                Button(
                    onClick = onSave, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save-household"),
                ) { Text(if (state.saving) "Saving…" else "Save Household") }
            }
        }
    }
    if (confirmArchive) AlertDialog(
        onDismissRequest = { confirmArchive = false },
        title = { Text(if (state.archived) "Restore This Household?" else "Archive This Household?") },
        text = {
            Text(
                if (state.archived) "The record returns to the active household list with its members and answers."
                else "Members, answers and pictures stay available. The record leaves the active household and member counts.",
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmArchive = false; onArchive(!state.archived) }, modifier = Modifier.testTag("confirm-archive")) {
                Text(if (state.archived) "Confirm Restore" else "Confirm Archive")
            }
        },
        dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
    )
}

@Composable
fun MemberEditScreen(
    state: MemberEditUiState,
    onDisplayName: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onNameDetails: () -> Unit = {},
) {
    var confirmRemove by remember { mutableStateOf(false) }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (state.missing) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Member unavailable", style = MaterialTheme.typography.headlineSmall)
            Text("This household is no longer stored on this device. Go back to the household list.")
        }
        return
    }
    Column(Modifier.fillMaxSize().testTag("member-edit-screen")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                if (state.creating) "Add Household Member" else "Edit Household Member",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(state.householdLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = state.displayName, onValueChange = onDisplayName, enabled = !state.saving, readOnly = state.structuredName,
                label = { Text("Full name") }, singleLine = true,
                isError = state.displayNameError != null,
                supportingText = { state.displayNameError?.let { Text(it, Modifier.testTag("hint-displayName")) } },
                modifier = Modifier.fillMaxWidth().testTag("field-displayName"),
            )
            if (!state.creating) {
                if (state.structuredName) Text("The full name is built from the saved name details.")
                OutlinedButton(onClick = onNameDetails, enabled = !state.saving, modifier = Modifier.testTag("edit-name-details")) { Text("Name Details") }
            }
            if (!state.creating) {
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("remove-member"),
                ) { Text("Remove Member") }
                Text(
                    "Removing deletes this member's saved answers with the member. Other members are unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SaveStatusLine(state.phase, state.message)
                Button(
                    onClick = onSave, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save-member"),
                ) { Text(if (state.saving) "Saving…" else "Save Member") }
            }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove This Member?") },
        text = { Text("Saved answers for ${state.displayName.ifBlank { "this member" }} are deleted with the member. This cannot be undone from this screen.") },
        confirmButton = {
            TextButton(onClick = { confirmRemove = false; onRemove() }, modifier = Modifier.testTag("confirm-remove")) { Text("Confirm Remove") }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}
