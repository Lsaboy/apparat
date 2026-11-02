package androidx.activity.compose

import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** Launches a desktop dialog (file picker, save dialog...) and reports its result. */
class ManagedActivityResultLauncher<I, O> internal constructor(
    private val contract: ActivityResultContract<I, O>,
    private val onResult: () -> (O) -> Unit
) {
    fun launch(input: I) {
        val result = contract.launchDesktop(input)
        onResult()(result)
    }
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit
): ManagedActivityResultLauncher<I, O> {
    val current by rememberUpdatedState(onResult)
    return remember(contract::class) { ManagedActivityResultLauncher(contract) { current } }
}

/** Back navigation on desktop: Esc, Alt+Left or the mouse back button (see the desktop window). */
object BackDispatcher {
    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    private val entries = mutableStateListOf<Entry>()

    internal fun add(entry: Entry) = entries.add(entry)
    internal fun remove(entry: Entry) = entries.remove(entry)

    /** Runs the innermost enabled handler; false if none handled it. */
    fun dispatch(): Boolean {
        val handler = entries.lastOrNull { it.enabled } ?: return false
        handler.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry(enabled) { current() } }
    entry.enabled = enabled
    DisposableEffect(entry) {
        BackDispatcher.add(entry)
        onDispose { BackDispatcher.remove(entry) }
    }
}
