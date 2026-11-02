package androidx.activity.result.contract

import android.net.Uri
import com.omniforge.apparat.desktop.DesktopDialogs

/** Desktop stand-in: a contract opens a native dialog and returns its result directly. */
abstract class ActivityResultContract<I, O> {
    abstract fun launchDesktop(input: I): O
}

object ActivityResultContracts {
    /** Input: MIME filters, e.g. arrayOf("application/pdf"). */
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun launchDesktop(input: Array<String>): Uri? = DesktopDialogs.openFile(input.toList())?.let(Uri::fromFile)
    }

    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun launchDesktop(input: Array<String>): List<Uri> = DesktopDialogs.openFiles(input.toList()).map(Uri::fromFile)
    }

    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun launchDesktop(input: String): Uri? = DesktopDialogs.openFile(listOf(input))?.let(Uri::fromFile)
    }

    /** Input: the suggested file name. */
    class CreateDocument(private val mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun launchDesktop(input: String): Uri? = DesktopDialogs.saveFile(input, mimeType)?.let(Uri::fromFile)
    }

    /** Input: a starting folder (may be null). Output: the chosen folder. */
    class OpenDocumentTree : ActivityResultContract<Uri?, Uri?>() {
        override fun launchDesktop(input: Uri?): Uri? = DesktopDialogs.chooseFolder(input?.file)?.let(Uri::fromFile)
    }

    /** Desktop apps need no runtime permissions. */
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun launchDesktop(input: String): Boolean = true
    }
}
