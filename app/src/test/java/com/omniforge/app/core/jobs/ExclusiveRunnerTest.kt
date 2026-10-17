package com.omniforge.app.core.jobs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ExclusiveRunnerTest {

    @Test
    fun `blocks in one group never overlap`() = runBlocking {
        val runner = ExclusiveRunner()
        val running = AtomicInteger(0)
        val maxSeen = AtomicInteger(0)
        (1..4).map {
            async {
                runner.run("video") {
                    val now = running.incrementAndGet()
                    maxSeen.updateAndGet { m -> maxOf(m, now) }
                    delay(20)
                    running.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1, maxSeen.get())
    }

    @Test
    fun `only the job that has to wait is told to wait`() = runBlocking {
        val runner = ExclusiveRunner()
        val waited = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val first = async {
            runner.run("video", onWait = { waited += "first" }) {
                firstStarted.complete(Unit)
                release.await()
            }
        }
        firstStarted.await()
        val second = async { runner.run("video", onWait = { waited += "second" }) { } }
        delay(50)
        assertFalse(second.isCompleted)
        release.complete(Unit)
        first.await(); second.await()
        assertEquals(listOf("second"), waited)
    }

    @Test
    fun `different groups run in parallel`() = runBlocking {
        val runner = ExclusiveRunner()
        val inA = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val a = async { runner.run("a") { inA.complete(Unit); release.await() } }
        inA.await()
        var bRan = false
        runner.run("b") { bRan = true }
        assertTrue(bRan)
        release.complete(Unit)
        a.await()
    }

    @Test
    fun `cancelling a waiting job lets the next one run`() = runBlocking {
        val runner = ExclusiveRunner()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async { runner.run("video") { holding.complete(Unit); release.await() } }
        holding.await()
        val waiting = launch { runner.run("video") { } }
        delay(30)
        waiting.cancelAndJoin()
        var thirdRan = false
        val third = async { runner.run("video") { thirdRan = true } }
        release.complete(Unit)
        holder.await(); third.await()
        assertTrue(thirdRan)
    }

    @Test
    fun `a failing block releases the group`() = runBlocking {
        val runner = ExclusiveRunner()
        try {
            runner.run("video") { error("boom") }
        } catch (_: IllegalStateException) { }
        var ran = false
        runner.run("video") { ran = true }
        assertTrue(ran)
    }
}
