package io.keval.apps.numbcrunch

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.CancellationTokenSource
import io.keval.apps.numbcrunch.playgames.awaitSnapshotTask
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class SnapshotTaskCancellationTest {
    @Test fun sdkCancellationIsRetryableFailureRatherThanLifecycleCancellation() = runBlocking {
        val cancellation = CancellationTokenSource()
        val source = TaskCompletionSource<Any>(cancellation.token)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { source.task.awaitSnapshotTask() }.exceptionOrNull()
        }
        cancellation.cancel()
        assertTrue(withTimeout(5_000) { result.await() } is IOException)
    }

    @Test fun resultArrivingAfterCancellationIsReleased() = runBlocking {
        val source = TaskCompletionSource<Any>()
        val released = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            source.task.awaitSnapshotTask { released.complete(Unit) }
        }
        job.cancelAndJoin()
        source.setResult(Any())
        withTimeout(5_000) { released.await() }
    }

    @Test fun resultQueuedBeforeCancellationIsReleasedInsteadOfDelivered() = runBlocking {
        val pending = ConcurrentLinkedQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { pending.add(block) }
        }
        val source = TaskCompletionSource<Any>()
        val releaseCount = AtomicInteger()
        var delivered = false
        val job = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) {
            source.task.awaitSnapshotTask { releaseCount.incrementAndGet() }
            delivered = true
        }
        source.setResult(Any())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(pending.isNotEmpty())
        job.cancel()
        while (pending.isNotEmpty()) pending.poll()?.run()
        job.join()
        assertFalse(delivered)
        assertEquals(1, releaseCount.get())
    }

    @Test fun successfulConsumerOwnsResultWithoutPrematureRelease() = runBlocking {
        val source = TaskCompletionSource<Any>()
        val value = Any()
        val releaseCount = AtomicInteger()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            source.task.awaitSnapshotTask { releaseCount.incrementAndGet() }
        }
        source.setResult(value)
        assertSame(value, withTimeout(5_000) { result.await() })
        assertEquals(0, releaseCount.get())
    }
}
