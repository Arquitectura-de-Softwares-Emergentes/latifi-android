package com.latifi.wallet.domain.chain

import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeRpcClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptPollerTest {
    private val hash = "0x" + "ab".repeat(32)

    @Test
    fun theIntervalIsTwoSeconds() {
        assertEquals(2_000L, ReceiptPoller.INTERVAL_MILLIS)
    }

    @Test
    fun asksEveryTwoThousandMillisAndDoesNotEmitOnANullReceipt() = runTest {
        val rpc = FakeRpcClient().apply { script(hash, FakeReceiptScript(nullPolls = 100)) }
        val result = async { ReceiptPoller(rpc).poll(hash).first() }

        runCurrent()
        assertEquals("nothing is asked before the first interval", 0, rpc.receiptRequests.size)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(0, rpc.receiptRequests.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, rpc.receiptRequests.size)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2, rpc.receiptRequests.size)
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(5, rpc.receiptRequests.size)
        assertFalse("a null receipt never completes the flow", result.isCompleted)
        result.cancel()
    }

    @Test
    fun emitsTheFirstNonNullReceiptAndStopsAsking() = runTest {
        val rpc = FakeRpcClient().apply { script(hash, FakeReceiptScript(nullPolls = 2, status = "0x1")) }
        val result = async { ReceiptPoller(rpc).poll(hash).first() }

        advanceTimeBy(4_000)
        runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(result.isCompleted)
        assertTrue(result.await().succeeded)
        assertEquals(3, rpc.receiptRequests.size)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals("it stops after the receipt", 3, rpc.receiptRequests.size)
    }

    @Test
    fun aRevertedReceiptIsStillAReceipt() = runTest {
        val rpc = FakeRpcClient().apply { script(hash, FakeReceiptScript(status = "0x0")) }
        val result = async { ReceiptPoller(rpc).poll(hash).first() }

        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(result.await().succeeded)
    }

    @Test
    fun survivesConsecutiveFailuresAndKeepsPolling() = runTest {
        val rpc = FakeRpcClient().apply { script(hash, FakeReceiptScript(failedPolls = 2, nullPolls = 1)) }
        val result = async { ReceiptPoller(rpc).poll(hash).first() }

        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(2, rpc.receiptRequests.size)
        assertFalse(result.isCompleted)
        advanceTimeBy(4_000)
        runCurrent()

        assertTrue(result.isCompleted)
        assertTrue(result.await().succeeded)
        assertEquals(4, rpc.receiptRequests.size)
    }

    @Test
    fun anIoExceptionThrownByTheClientIsRetriedSilently() = runTest {
        var calls = 0
        val rpc = object : RpcClient {
            override suspend fun call(to: String, data: String, from: String) = Result.success<CallOutcome>(CallOutcome.Success("0x"))

            override suspend fun getReceipt(hash: String): Result<Receipt?> {
                calls += 1
                if (calls <= 2) throw IOException("connection reset")
                return Result.success(Receipt("0x1"))
            }

            override suspend fun getBalance(address: String) = Result.success(BigInteger.ONE)
        }
        val result = async { ReceiptPoller(rpc).poll(hash).first() }

        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(result.await().succeeded)
        assertEquals(3, calls)
    }
}
