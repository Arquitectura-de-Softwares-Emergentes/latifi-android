package com.latifi.wallet

import com.latifi.wallet.support.FakeAppKitClient
import com.latifi.wallet.support.testConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope

/**
 * Research Pitfall 10: the order is bootstrap, then the consumers of wallet answers, then the
 * delegate, then the session extension, then the resumer. The placeholder project id never reaches the SDK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupTest {
    private val live = testConfig().copy(
        reownProjectId = "0123456789abcdef0123456789abcdef",
        apiBaseUrl = "https://api.example/api/v1/",
    )

    private class Recorder {
        val log = mutableListOf<String>()
    }

    private fun startup(scope: TestScope, config: com.latifi.wallet.core.AppConfig, recorder: Recorder): AppStartup {
        val client = object : com.latifi.wallet.data.wallet.AppKitClient by FakeAppKitClient() {
            override fun attachDelegate() {
                recorder.log += "delegate"
            }

            override suspend fun extendSession() {
                recorder.log += "extend"
            }
        }
        return AppStartup(
            config = config,
            bootstrap = WalletBootstrap { recorder.log += "bootstrap" },
            client = client,
            resumer = Provider {
                recorder.log += "consumers"
                PendingResume { recorder.log += "resume" }
            },
            scope = CoroutineScope(StandardTestDispatcher(scope.testScheduler)),
        )
    }

    @Test
    fun liveModeRunsBootstrapConsumersDelegateExtendThenResume() = runTest {
        val recorder = Recorder()

        startup(this, live, recorder).run()
        testScheduler.runCurrent()

        assertEquals(listOf("bootstrap", "consumers", "delegate", "extend", "resume"), recorder.log)
    }

    @Test
    fun thePlaceholderProjectIdNeverTouchesTheClientButTheResumerStillRuns() = runTest {
        val recorder = Recorder()

        startup(this, testConfig(), recorder).run()
        testScheduler.runCurrent()

        assertEquals(listOf("consumers", "resume"), recorder.log)
    }

    @Test
    fun aRealProjectIdWithoutAnApiHostStaysInDemoModeAndLeavesTheSdkAlone() = runTest {
        val recorder = Recorder()
        val demoWithId = testConfig().copy(reownProjectId = "0123456789abcdef0123456789abcdef")

        startup(this, demoWithId, recorder).run()
        testScheduler.runCurrent()

        assertEquals(listOf("consumers", "resume"), recorder.log)
    }

    @Test
    fun theDelegateIsSetBeforeTheResumerRuns() = runTest {
        val recorder = Recorder()

        startup(this, live, recorder).run()
        testScheduler.runCurrent()

        assertTrue(recorder.log.indexOf("delegate") < recorder.log.indexOf("resume"))
        assertTrue(recorder.log.indexOf("consumers") < recorder.log.indexOf("delegate"))
    }
}
