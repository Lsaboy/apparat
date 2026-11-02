package coil3.request

import coil3.size.Size

/**
 * Desktop stand-in for Coil's ImageRequest: just what to load ([data]: File or Uri) and the
 * preview bounds. The shared screens build requests exactly as on Android.
 */
class ImageRequest private constructor(val data: Any?, val size: Size?, val crossfade: Boolean) {
    class Builder(@Suppress("UNUSED_PARAMETER") context: Any?) {
        private var data: Any? = null
        private var size: Size? = null
        internal var crossfade: Boolean = false

        fun data(data: Any?) = apply { this.data = data }
        fun size(size: Size) = apply { this.size = size }
        fun size(width: Int, height: Int) = apply { this.size = Size(width, height) }
        fun build(): ImageRequest = ImageRequest(data, size, crossfade)
    }
}

fun ImageRequest.Builder.crossfade(enable: Boolean): ImageRequest.Builder = apply { crossfade = enable }
