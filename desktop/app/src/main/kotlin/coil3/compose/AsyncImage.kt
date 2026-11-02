package coil3.compose

import android.content.Context
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.request.ImageRequest
import com.omniforge.apparat.engine.image.BitmapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop stand-in for Coil's AsyncImage: decodes the file (with EXIF orientation) off the UI
 * thread at preview size and shows it. [model] is an [ImageRequest], a File or a Uri.
 */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit
) {
    val request = model as? ImageRequest
    val data = request?.data ?: model
    val maxDim = request?.size?.let { maxOf(it.width, it.height) } ?: 1024
    val file: File? = when (data) {
        is File -> data
        is Uri -> data.file
        is String -> File(data)
        else -> null
    }
    val stamp = file?.let { it.lastModified() xor it.length() }
    val image by produceState<ImageBitmap?>(null, file, stamp, maxDim) {
        value = if (file == null) null else withContext(Dispatchers.IO) {
            runCatching { BitmapDecoder.decode(Context.app, Uri.fromFile(file), maxDimension = maxDim * 2).asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = alignment) {
        Crossfade(targetState = image) { bmp ->
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = contentDescription, contentScale = contentScale, alignment = alignment, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}
