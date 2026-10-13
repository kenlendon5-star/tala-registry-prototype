package ph.tala.registry.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PhotoPreview(path: String?, description: String) {
    if (path == null) return
    val root = LocalContext.current.filesDir
    val preview by produceState<Pair<Boolean, ImageBitmap?>>(false to null, path) {
        value = withContext(Dispatchers.IO) {
            true to runCatching {
                val file = File(root, path).canonicalFile
                if (file.toPath().startsWith(root.canonicalFile.toPath())) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
            }.getOrNull()
        }
    }
    preview.second?.let { Image(it, description, Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("photo-preview"), contentScale = ContentScale.Fit) }
        ?: Text(if (preview.first) "Saved picture unavailable. Choose a replacement." else "Loading saved picture…")
}
