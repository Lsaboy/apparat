package android.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.omniforge.apparat.desktop.platform.MediaProbe
import java.io.File

/** Desktop stand-in for android.media.MediaMetadataRetriever, answering from ffprobe. */
class MediaMetadataRetriever : AutoCloseable {
    private var probe: MediaProbe? = null
    private var source: File? = null

    fun setDataSource(context: Context, uri: Uri) = setDataSource(uri.file)

    fun setDataSource(path: String) = setDataSource(File(path))

    fun setDataSource(file: File) {
        source = file
        probe = MediaProbe.of(file) ?: throw IllegalArgumentException("Could not read media: ${file.name}")
    }

    fun extractMetadata(keyCode: Int): String? {
        val p = probe ?: return null
        return when (keyCode) {
            METADATA_KEY_DURATION -> (p.durationSec * 1000).toLong().toString()
            METADATA_KEY_VIDEO_WIDTH -> p.width.takeIf { p.hasVideo }?.toString()
            METADATA_KEY_VIDEO_HEIGHT -> p.height.takeIf { p.hasVideo }?.toString()
            METADATA_KEY_VIDEO_ROTATION -> p.rotation.toString()
            METADATA_KEY_BITRATE -> p.bitrate?.toString()
            METADATA_KEY_HAS_VIDEO -> if (p.hasVideo) "yes" else null
            METADATA_KEY_HAS_AUDIO -> if (p.hasAudio) "yes" else null
            METADATA_KEY_MIMETYPE -> source?.let { com.omniforge.apparat.desktop.platform.MimeTypes.forName(it.name) }
            else -> null
        }
    }

    fun release() {
        probe = null
    }

    override fun close() = release()

    companion object {
        const val METADATA_KEY_DURATION = 9
        const val METADATA_KEY_MIMETYPE = 12
        const val METADATA_KEY_HAS_AUDIO = 16
        const val METADATA_KEY_HAS_VIDEO = 17
        const val METADATA_KEY_VIDEO_WIDTH = 18
        const val METADATA_KEY_VIDEO_HEIGHT = 19
        const val METADATA_KEY_BITRATE = 20
        const val METADATA_KEY_VIDEO_ROTATION = 24
        const val OPTION_CLOSEST = 3
    }
}
