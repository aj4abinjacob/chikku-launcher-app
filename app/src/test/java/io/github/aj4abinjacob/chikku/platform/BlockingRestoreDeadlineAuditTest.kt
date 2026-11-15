package io.github.aj4abinjacob.chikku.platform

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BlockingRestoreDeadlineAuditTest {
    @Test fun uncooperativeWorkerCannotHoldCallerOrGrowWithoutBound() = runBlocking {
        BoundedRestoreChecks().use { checks ->
            val release = CountDownLatch(1)
            val entered = AtomicInteger()
            try {
                repeat(2) {
                    val result = withTimeout(2_000) {
                        checks.await(100) {
                            entered.incrementAndGet()
                            while (release.count > 0) {
                                try {
                                    release.await()
                                } catch (_: InterruptedException) { /* Hostile provider. */ }
                            }
                            "late private value"
                        }
                    }
                    assertThat(result).isNull()
                }
                assertThat(entered.get()).isEqualTo(2)
                repeat(20) {
                    assertThat(withTimeout(1_000) { checks.await(5_000) { entered.incrementAndGet() } }).isNull()
                }
                assertThat(entered.get()).isEqualTo(2)
            } finally {
                release.countDown()
            }
        }
    }

    @Test fun cooperativeCancellationAndNormalResultRemainSupported() = runBlocking {
        BoundedRestoreChecks().use { checks ->
            assertThat(checks.await(2_000) { 7 }).isEqualTo(7)
            val cancelled = CountDownLatch(1)
            assertThat(
                checks.await<Unit>(100, cancelled::countDown) {
                    kotlinx.coroutines.awaitCancellation()
                },
            ).isNull()
            assertThat(cancelled.await(1, TimeUnit.SECONDS)).isTrue()
        }
    }
}
