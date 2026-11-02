package android.content

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Desktop stand-in for android.content.ClipData (plain text only). */
class ClipData private constructor(val label: CharSequence?, private val items: List<Item>) {
    class Item(val text: CharSequence?)

    val itemCount: Int get() = items.size
    fun getItemAt(index: Int): Item = items[index]

    companion object {
        @JvmStatic
        fun newPlainText(label: CharSequence?, text: CharSequence?): ClipData = ClipData(label, listOf(Item(text)))
    }
}

/** Desktop stand-in for android.content.ClipboardManager, backed by the system clipboard. */
object ClipboardManager {
    val primaryClip: ClipData?
        get() = runCatching {
            val text = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
            text?.let { ClipData.newPlainText("text", it) }
        }.getOrNull()

    fun setPrimaryClip(clip: ClipData) {
        val text = if (clip.itemCount > 0) clip.getItemAt(0).text?.toString().orEmpty() else ""
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun hasPrimaryClip(): Boolean = primaryClip != null
}
