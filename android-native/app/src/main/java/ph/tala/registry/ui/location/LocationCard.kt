package ph.tala.registry.ui.location

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ph.tala.registry.domain.model.Coordinate
import ph.tala.registry.domain.model.CoordinateSource
import java.text.DateFormat
import java.util.Date

/**
 * Household location card for the record screen. Capture is request-on-use: the
 * permission dialog appears only when the enumerator taps "Capture GPS location".
 * Any capture failure (denied, GPS off, timeout, provider unavailable) changes only
 * this card's state — manual correction and the rest of the questionnaire stay usable.
 */
@Composable
fun HouseholdLocationCard(
    state: LocationUiState,
    onCapture: () -> Unit,
    onOpenManual: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditManualLatitude: (String) -> Unit,
    onEditManualLongitude: (String) -> Unit,
    onSaveManual: () -> Unit,
    onCancelManual: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Household location", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("location-card"))
            when (val coordinate = state.coordinate) {
                null -> Text(
                    "No coordinates recorded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("location-empty"),
                )
                else -> CoordinateDetails(coordinate)
            }
            Column {
                val message = state.message
                if (message.isNotBlank()) Text(
                    message,
                    color = if (state.capture in listOf(LocationCapture.Denied, LocationCapture.PermanentlyDenied, LocationCapture.LocationOff, LocationCapture.TimedOut, LocationCapture.Failed) ||
                        state.writePhase == LocationWritePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("location-status").semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (state.writePhase != LocationWritePhase.Idle) Text(
                    when (state.writePhase) {
                        LocationWritePhase.Saving -> "Saving…"
                        LocationWritePhase.Saved -> "Saved on this device."
                        LocationWritePhase.Failed -> "Could not save the coordinates."
                        LocationWritePhase.Idle -> ""
                    },
                    color = if (state.writePhase == LocationWritePhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("location-write-status"),
                )
            }
            if (state.manual == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCapture,
                        enabled = state.capture !in listOf(LocationCapture.Acquiring, LocationCapture.AwaitingPermission) && state.writePhase != LocationWritePhase.Saving && !state.loading,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture-location"),
                    ) { Text(if (state.capture == LocationCapture.Acquiring) "Acquiring…" else "Capture device location") }
                    OutlinedButton(
                        onClick = onOpenManual,
                        enabled = state.writePhase != LocationWritePhase.Saving,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("correct-location"),
                    ) { Text("Correct manually") }
                }
                when (state.capture) {
                    LocationCapture.Denied -> OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("retry-location"),
                    ) { Text("Retry location permission") }
                    LocationCapture.PermanentlyDenied -> OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open-location-settings"),
                    ) { Text("Open app settings") }
                    LocationCapture.LocationOff -> OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("retry-location"),
                    ) { Text("Check location again") }
                    else -> {}
                }
            } else {
                val draft = state.manual
                val latitudeFocus = remember { FocusRequester() }
                val longitudeFocus = remember { FocusRequester() }
                LaunchedEffect(draft.attempt) {
                    if (draft.attempt > 0) {
                        if (draft.latitudeError != null) latitudeFocus.requestFocus()
                        else if (draft.longitudeError != null) longitudeFocus.requestFocus()
                    }
                }
                OutlinedTextField(
                    value = draft.latitude,
                    onValueChange = onEditManualLatitude,
                    enabled = state.writePhase != LocationWritePhase.Saving,
                    label = { Text("Latitude (-90 to 90)") },
                    singleLine = true,
                    isError = draft.latitudeError != null,
                    supportingText = { draft.latitudeError?.let { Text(it, Modifier.testTag("hint-manual-lat")) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(latitudeFocus).testTag("manual-lat"),
                )
                OutlinedTextField(
                    value = draft.longitude,
                    onValueChange = onEditManualLongitude,
                    enabled = state.writePhase != LocationWritePhase.Saving,
                    label = { Text("Longitude (-180 to 180)") },
                    singleLine = true,
                    isError = draft.longitudeError != null,
                    supportingText = { draft.longitudeError?.let { Text(it, Modifier.testTag("hint-manual-lon")) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(longitudeFocus).testTag("manual-lon"),
                )
                Text(
                    "Manual coordinates replace the stored point without a measured accuracy; the entry time records when the correction was made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSaveManual,
                        enabled = state.writePhase != LocationWritePhase.Saving,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save-manual-location"),
                    ) { Text(if (state.writePhase == LocationWritePhase.Saving) "Saving…" else "Save coordinates") }
                    OutlinedButton(
                        onClick = onCancelManual,
                        enabled = state.writePhase != LocationWritePhase.Saving,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cancel-manual-location"),
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun CoordinateDetails(coordinate: Coordinate) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("location-coords")) {
        Text(
            "%.6f, %.6f".format(coordinate.latitude, coordinate.longitude),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            when (coordinate.source) {
                CoordinateSource.Gps ->
                    "Device fix · ±%s m · captured %s".format(
                        formatAccuracy(coordinate.accuracyMetres),
                        formatTime(coordinate.capturedAt),
                    )
                CoordinateSource.Manual -> "Manually corrected · entered %s".format(formatTime(coordinate.capturedAt))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("location-metadata"),
        )
    }
}

private fun formatAccuracy(accuracy: Double?): String =
    if (accuracy == null) "?" else if (accuracy >= 10) "%.0f".format(accuracy) else "%.1f".format(accuracy)

private fun formatTime(capturedAt: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(capturedAt))
