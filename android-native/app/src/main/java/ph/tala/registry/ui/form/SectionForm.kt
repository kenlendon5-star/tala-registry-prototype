package ph.tala.registry.ui.form

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ph.tala.registry.domain.form.FormRules
import ph.tala.registry.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Stateless shared renderer; all mutations carry the original scope in the owner's callback. */
@Composable
fun SectionForm(
    section: SectionSpec,
    state: SectionFormState,
    onAnswer: (String, AnswerValue) -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = section.visibleFields(state.answers)
    val firstInvalid = visible.firstOrNull { it.key in state.errors }?.key
    Column(modifier.fillMaxSize().testTag("section-form")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleLarge)
            Text("Practice with fictional answers. Entries last while the app is running.", style = MaterialTheme.typography.bodySmall)
            visible.forEach { field ->
                key(field.key) {
                    SharedField(field, state, field.key == firstInvalid, onAnswer)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.validationAttempt > 0) {
                    Text(
                        if (state.checkedAndValid) "Answers are valid. This is a practice entry, not a saved interview."
                        else "${state.errors.size} answers need attention.",
                        modifier = Modifier.testTag("form-result").semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (state.checkedAndValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("check-answers")) { Text("Check answers") }
            }
        }
    }
}

@Composable
private fun SharedField(field: FieldSpec, state: SectionFormState, firstInvalid: Boolean, onAnswer: (String, AnswerValue) -> Unit) {
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val message = state.errors[field.key]
    var handledAttempt by remember { mutableIntStateOf(state.validationAttempt) }
    // Keep this keyed only to a deliberate validation request; ordinary edits must not steal focus.
    // A newly revealed field starts at the current attempt and waits for the next Check.
    LaunchedEffect(state.validationAttempt) {
        val isNewCheck = state.validationAttempt > handledAttempt
        handledAttempt = state.validationAttempt
        if (isNewCheck && firstInvalid) {
            focus.requestFocus()
            bringIntoView.bringIntoView()
        }
    }
    val scalar = state.answers.scalar(field.key)
    val target = Modifier.fillMaxWidth().testTag("field-${field.key}").focusRequester(focus)
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        when (field.type) {
            FieldType.Text, FieldType.TextArea, FieldType.Phone, FieldType.Number, FieldType.Date, FieldType.DateTime -> {
                var wasFocused by remember { mutableStateOf(false) }
                var showCalendar by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = scalar,
                    onValueChange = { onAnswer(field.key, AnswerValue.Scalar(it)) },
                    label = { Text(field.label) },
                    isError = message != null,
                    supportingText = {
                        val hint = message ?: field.hint ?: when (field.type) {
                            FieldType.Date -> "YYYY-MM-DD"
                            FieldType.DateTime -> "YYYY-MM-DDTHH:MM · Local time"
                            FieldType.Number -> listOfNotNull(field.min?.let { "Minimum $it" }, field.max?.let { "maximum $it" }).joinToString(" · ")
                            else -> ""
                        }
                        if (hint.isNotEmpty()) Text(hint, Modifier.testTag("hint-${field.key}"))
                    },
                    singleLine = field.type != FieldType.TextArea,
                    minLines = if (field.type == FieldType.TextArea) 3 else 1,
                    keyboardOptions = KeyboardOptions(keyboardType = when (field.type) {
                        FieldType.Phone -> KeyboardType.Phone
                        FieldType.Number -> KeyboardType.Number
                        else -> KeyboardType.Text
                    }),
                    trailingIcon = if (field.type == FieldType.Date || field.type == FieldType.DateTime) {
                        { TextButton(onClick = { showCalendar = true }, modifier = Modifier.testTag("pick-${field.key}")) { Text("Pick") } }
                    } else null,
                    modifier = target.onFocusChanged { focusState ->
                        if (wasFocused && !focusState.isFocused && field.type == FieldType.Phone) {
                            val formatted = FormRules.formatPhone(scalar)
                            if (formatted != scalar) onAnswer(field.key, AnswerValue.Scalar(formatted))
                        }
                        wasFocused = focusState.isFocused
                    },
                )
                if (showCalendar) CalendarInput(field.type, scalar, onDismiss = { showCalendar = false }) {
                    onAnswer(field.key, AnswerValue.Scalar(it))
                    showCalendar = false
                }
            }
            FieldType.Choice -> {
                FieldLabel(field)
                Column(target.focusable().selectableGroup().semantics { if (message != null) error(message) }) {
                    field.options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("option-${field.key}-$option")
                                .selectable(selected = scalar == option, role = Role.RadioButton, onClick = { onAnswer(field.key, AnswerValue.Scalar(option)) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = scalar == option, onClick = null)
                            Text(option, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                FieldMessage(field, message)
            }
            FieldType.Select -> {
                var expanded by remember { mutableStateOf(false) }
                FieldLabel(field)
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = target.semantics { if (message != null) error(message) }) {
                        Text(scalar.ifEmpty { "Choose an answer" }, Modifier.weight(1f))
                        Text("▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        if (field.optional) DropdownMenuItem(text = { Text("Clear selection") }, onClick = { onAnswer(field.key, AnswerValue.Blank); expanded = false })
                        field.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, modifier = Modifier.testTag("option-${field.key}-$option"), onClick = {
                                onAnswer(field.key, AnswerValue.Scalar(option)); expanded = false
                            })
                        }
                    }
                }
                FieldMessage(field, message)
            }
            FieldType.MultiSelect -> {
                FieldLabel(field)
                val selected = state.answers.multi(field.key)
                Column(target.focusable().semantics { if (message != null) error(message) }) {
                    field.options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("option-${field.key}-$option")
                                .toggleable(value = option in selected, role = Role.Checkbox, onValueChange = { onAnswer(field.key, FormRules.toggle(field, selected, option)) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = option in selected, onCheckedChange = null)
                            Text(option, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                FieldMessage(field, message)
            }
            FieldType.Photo -> {
                Column(target.focusable()) {
                    FieldLabel(field)
                    Text("Photo collection will be available in a later build.")
                    FieldMessage(field, message)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(field: FieldSpec) {
    Text(field.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun FieldMessage(field: FieldSpec, message: String?) {
    (message ?: field.hint)?.let {
        Text(it, modifier = Modifier.testTag("hint-${field.key}"), style = MaterialTheme.typography.bodySmall,
            color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Calendar milliseconds are UTC dates; appointments are local date/times without timezone conversion. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarInput(type: FieldType, value: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val existingTime = FormRules.parseDateTime(value)
    val initialDate = (FormRules.parseDate(value) ?: existingTime?.toLocalDate())
        ?.takeIf { it.year in 1900..2100 } ?: LocalDate.now()
    val calendar = rememberDatePickerState(initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    if (selectedDate == null) DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = calendar.selectedDateMillis != null, onClick = {
                val date = Instant.ofEpochMilli(requireNotNull(calendar.selectedDateMillis)).atZone(ZoneOffset.UTC).toLocalDate()
                if (type == FieldType.Date) onSelect(date.toString()) else selectedDate = date
            }) { Text(if (type == FieldType.Date) "Use date" else "Choose time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = calendar) }
    else {
        val time = rememberTimePickerState(initialHour = existingTime?.hour ?: 9, initialMinute = existingTime?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Appointment time") },
            text = { TimeInput(state = time) },
            confirmButton = { TextButton(onClick = {
                onSelect(requireNotNull(selectedDate).atTime(time.hour, time.minute).format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")))
            }) { Text("Use appointment") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
