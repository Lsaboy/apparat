package com.omniforge.app.core.jobs

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.omniforge.app.core.model.ProcessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs tool jobs outside any screen, so pressing back or backgrounding the app doesn't cancel
 * them. Each tool (keyed by its nav route) can run one job at a time.
 *
 * Screen state that must outlive the screen while a job runs lives in [retained]. It is dropped
 * when the user leaves a tool that has nothing running, so tools still open fresh next time.
 * [ToolJobService] keeps the process in the foreground while anything runs.
 */
object ToolJobs {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()

    /** Running tools: route -> tool title. Observable from Compose and the service. */
    val running = mutableStateMapOf<String, String>()

    /** Routes whose screen is currently shown (no "done" notification needed for those). */
    private val visible = HashSet<String>()

    /** Routes whose screen is in the back stack's composition (shown or not). */
    private val open = HashSet<String>()

    private val store = HashMap<String, Any>()

    /** Tools that run a video encoder. Their jobs queue so only one encodes at a time. */
    private val videoEncodeKeys = setOf("video_shrink", "video_convert", "video_caption", "video_trim")
    private const val VIDEO_ENCODE_GROUP = "video-encode"
    private val exclusive = ExclusiveRunner()

    fun isRunning(key: String): Boolean = running.containsKey(key)

    /** Starts [block] for [key] unless a job is already running there. */
    fun launch(
        context: Context,
        key: String,
        title: String,
        block: suspend CoroutineScope.(appContext: Context) -> Unit
    ) {
        if (isRunning(key)) return
        val appContext = context.applicationContext
        running[key] = title
        // Best effort: if the service can't start, the job still runs while the app is open.
        // Plain startService (jobs only start from a visible screen, so it's allowed): the service
        // promotes itself to foreground, and if the system refuses, it can stop without the
        // "did not call startForeground" crash that startForegroundService would trigger.
        runCatching { appContext.startService(Intent(appContext, ToolJobService::class.java)) }
        val job = scope.launch {
            if (key in videoEncodeKeys) {
                exclusive.run(
                    group = VIDEO_ENCODE_GROUP,
                    onWait = {
                        setProcessState(key, ProcessState.Processing(0f, "Waiting for the other video job to finish..."))
                    }
                ) { block(appContext) }
            } else {
                block(appContext)
            }
        }
        jobs[key] = job
        job.invokeOnCompletion { cause ->
            scope.launch {
                jobs.remove(key)
                running.remove(key)
                when {
                    cause != null && key !in open -> clear(key) // stopped from the leave dialog
                    cause != null -> setProcessState(key, ProcessState.Idle) // cancelled while on screen
                    key !in visible -> ToolJobService.notifyFinished(appContext, key, title, processState(key))
                }
            }
        }
    }

    fun cancel(key: String) {
        jobs[key]?.cancel()
    }

    /** Current ProcessState of a tool, if its screen keeps one in [retained] as "processState". */
    fun processState(key: String): ProcessState? =
        (store["$key/processState"] as? State<*>)?.value as? ProcessState

    @Suppress("UNCHECKED_CAST")
    private fun setProcessState(key: String, state: ProcessState) {
        (store["$key/processState"] as? MutableState<ProcessState>)?.value = state
    }

    private fun clear(key: String) {
        store.keys.removeAll { it.startsWith("$key/") }
    }

    internal fun <S : Any> retain(key: String, name: String, create: () -> S): S {
        @Suppress("UNCHECKED_CAST")
        return store.getOrPut("$key/$name", create) as S
    }

    internal fun onScreenOpened(key: String) {
        open += key
    }

    /** The tool's screen is on screen (lifecycle STARTED). */
    internal fun onScreenShown(context: Context, key: String) {
        visible += key
        ToolJobService.cancelFinishedNotification(context, key)
    }

    /** The tool's screen went to the background. */
    internal fun onScreenHidden(key: String) {
        visible -= key
    }

    /** The tool's screen was closed. Its state is kept only while its job runs. */
    internal fun onScreenLeft(key: String) {
        visible -= key
        open -= key
        if (!isRunning(key)) clear(key)
    }
}

/**
 * Like `remember`, but the state survives leaving the screen while [key]'s job is running,
 * so the user sees progress and the result when they come back.
 */
@Composable
fun <S : Any> retained(key: String, name: String, create: () -> S): S =
    remember(key, name) { ToolJobs.retain(key, name, create) }
