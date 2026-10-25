package ph.tala.registry.ui.interview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * Drawn consent signature (plan card D13). Strokes save on lift: only a visible mark is
 * recorded, so tapping a blank pad never signs the form. Undo drops the last stroke and
 * Clear removes the saved mark; the typed-name field is the other accepted mode.
 *
 * Gesture isolation: the pad consumes every change from the initial down event, so the
 * surrounding scroll container can never turn a stroke into a page scroll (fieldwork
 * finding: strokes starting near the pad edge dragged the form instead of drawing).
 */
@Composable
fun SignaturePad(
    hasSavedSignature: Boolean,
    onSave: (ByteArray) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Strokes are stored in a fixed 600x240 pad space so the rendered PNG matches what was drawn.
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val current = remember { mutableStateListOf<Offset>() }
    val undone = remember { mutableStateListOf<List<Offset>>() }
    val save by rememberUpdatedState(onSave)
    val clear by rememberUpdatedState(onClear)
    val saved by rememberUpdatedState(hasSavedSignature)
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = 48.dp).testTag("signature-expand"),
            ) { Text(if (expanded) "Shrink canvas" else "Expand canvas") }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (expanded) 320.dp else 180.dp)
                .testTag("signature-pad")
                // Assistive tech gets an explanation of the canvas and its saved/empty state
                // instead of an unexplained blank surface (UX-07).
                .semantics {
                    contentDescription =
                        "Signature drawing area. Draw with your finger, or use the typed name field as an accessible alternative."
                    stateDescription = if (saved) "Signature saved" else "No signature saved"
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // requireUnconsumed = false: take the pointer even if a parent
                        // scroll already claimed the stream, then own it from here on.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        current.clear()
                        current += normalize(down.position, size.width, size.height)
                        val completed = drag(down.id) { change ->
                            change.consume()
                            current += normalize(change.position, size.width, size.height)
                        }
                        if (completed && current.size > 1) {
                            strokes += current.toList()
                            undone.clear()
                            save(renderStrokes(strokes.toList()))
                        }
                        current.clear()
                    }
                },
        ) {
            drawRect(Color.White)
            drawLine(Color(0xFFCCCCCC), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), strokeWidth = 2f)
            for (stroke in strokes) drawStroke(stroke, size.width, size.height)
            if (current.size > 1) drawStroke(current, size.width, size.height)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (strokes.isNotEmpty()) {
                        undone += strokes.removeAt(strokes.lastIndex)
                        if (strokes.isEmpty()) clear() else save(renderStrokes(strokes.toList()))
                    }
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signature-undo"),
            ) { Text("Undo stroke") }
            OutlinedButton(
                onClick = {
                    if (undone.isNotEmpty()) {
                        strokes += undone.removeAt(undone.lastIndex)
                        save(renderStrokes(strokes.toList()))
                    }
                },
                enabled = undone.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signature-redo"),
            ) { Text("Redo stroke") }
            OutlinedButton(
                onClick = { strokes.clear(); current.clear(); undone.clear(); clear() },
                enabled = strokes.isNotEmpty() || saved,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("signature-clear"),
            ) { Text(if (saved) "Clear signature" else "Clear strokes") }
        }
    }

}

private fun normalize(point: Offset, widthPx: Int, heightPx: Int) = Offset(
    (point.x / widthPx.toFloat() * 600f).coerceIn(0f, 600f),
    (point.y / heightPx.toFloat() * 240f).coerceIn(0f, 240f),
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStroke(stroke: List<Offset>, widthPx: Float, heightPx: Float) {
    val path = Path()
    path.moveTo(stroke.first().x * widthPx / 600f, stroke.first().y * heightPx / 240f)
    for (point in stroke.drop(1)) path.lineTo(point.x * widthPx / 600f, point.y * heightPx / 240f)
    drawPath(path, Color(0xFF172944), style = Stroke(width = 4f))
}

/** Encodes the strokes to a bounded PNG on a white background; empty strokes never reach here. */
private fun renderStrokes(strokes: List<List<Offset>>): ByteArray {
    val bitmap = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply {
        color = android.graphics.Color.rgb(0x17, 0x29, 0x44)
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in strokes) {
        if (stroke.size < 2) continue
        val path = android.graphics.Path()
        path.moveTo(stroke.first().x, stroke.first().y)
        for (point in stroke.drop(1)) path.lineTo(point.x, point.y)
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return out.toByteArray()
}
