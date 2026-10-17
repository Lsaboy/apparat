package com.omniforge.apparat.core.jobs

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs blocks of the same group one at a time, in the order they ask. Phones expose only one or
 * two hardware video encoders, so two simultaneous encodes make the second one fail.
 */
class ExclusiveRunner {
    private val locks = HashMap<String, Mutex>()

    /** Calls [onWait] first if another block of [group] is running, then runs [block] when it's free. */
    suspend fun <T> run(group: String, onWait: () -> Unit = {}, block: suspend () -> T): T {
        val mutex = synchronized(locks) { locks.getOrPut(group) { Mutex() } }
        if (mutex.isLocked) onWait()
        return mutex.withLock { block() }
    }
}
