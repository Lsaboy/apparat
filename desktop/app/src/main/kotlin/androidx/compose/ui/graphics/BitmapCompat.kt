package androidx.compose.ui.graphics

import com.omniforge.apparat.desktop.platform.SkiaImages

/** Desktop counterpart of Android's Bitmap.asImageBitmap() for the desktop [android.graphics.Bitmap]. */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = SkiaImages.toImage(this).toComposeImageBitmap()
