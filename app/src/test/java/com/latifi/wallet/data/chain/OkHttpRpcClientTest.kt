package com.latifi.wallet.data.chain

import com.latifi.wallet.data.api.ApiClientFactory
import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class OkHttpRpcClientTest {
    private val server = MockWebServer().apply { start() }
    private val secret = "SECRETKEY123"
    private val url = server.url("/v3/$secret/").toString()
    private val client = OkHttpRpcClient(url, ApiClientFactory.rpcHttpClient())
    private val owner = "0x1111111111111111111111111111111111111111"
    private val token = "0x66757f1cc66b278bfc0eefad7e3cac1d70fd4f8b"

    @After
    fun tearDown() = server.shutdown()

    private fun reply(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun result(value: String) = reply("""{"jsonrpc":"2.0","id":1,"result":$value}""")

    private fun sentBody() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test
    fun ethCallSendsFromToAndDataThenLatest() = runBlocking {
        result("\"0x0000000000000000000000000000000000000000000000000000000000000001\"")

        val outcome = client.call(to = token, data = "0x70a082310000", from = owner).getOrThrow()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v3/$secret/", request.path)
        assertNull(request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("2.0", body.getValue("jsonrpc").jsonPrimitive.content)
        assertEquals("eth_call", body.getValue("method").jsonPrimitive.content)
        val params = body.getValue("params").jsonArray
        assertEquals(2, params.size)
        val call = params[0].jsonObject
        assertEquals(owner, call.getValue("from").jsonPrimitive.content)
        assertEquals(token, call.getValue("to").jsonPrimitive.content)
        assertEquals("0x70a082310000", call.getValue("data").jsonPrimitive.content)
        assertEquals("latest", params[1].jsonPrimitive.content)
        assertEquals(CallOutcome.Success("0x" + "0".repeat(63) + "1"), outcome)
    }

    @Test
    fun ethCallWithoutAFromOmitsTheField() = runBlocking {
        result("\"0x\"")

        client.call(to = token, data = "0x1234", from = "").getOrThrow()

        val call = sentBody().getValue("params").jsonArray[0].jsonObject
        assertFalse(call.containsKey("from"))
        assertEquals(setOf("to", "data"), call.keys)
    }

    @Test
    fun aNullReceiptIsAValueAndNotAFailure() = runBlocking {
        result("null")

        val receipt = client.getReceipt("0x" + "ab".repeat(32)).getOrThrow()

        assertNull(receipt)
        val body = sentBody()
        assertEquals("eth_getTransactionReceipt", body.getValue("method").jsonPrimitive.content)
        assertEquals("0x" + "ab".repeat(32), body.getValue("params").jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun aSuccessfulReceiptCarriesItsLogs() = runBlocking {
        result(
            """{"status":"0x1","blockNumber":"0x10","logs":[{"address":"0xCCB8ad15a4954138c987a77924ceab468a843266","topics":["${ReceiptLogParser.LOAN_REQUESTED_TOPIC}","0x0000000000000000000000000000000000000000000000000000000000000007"],"data":"0x00"}]}""",
        )

        val receipt = client.getReceipt("0x" + "cd".repeat(32)).getOrThrow()

        assertNotNull(receipt)
        assertTrue(receipt!!.succeeded)
        assertEquals(1, receipt.logs.size)
        assertEquals("0xCCB8ad15a4954138c987a77924ceab468a843266", receipt.logs[0].address)
        assertEquals(2, receipt.logs[0].topics.size)
        assertEquals("0x00", receipt.logs[0].data)
        assertEquals(BigInteger.valueOf(7), ReceiptLogParser.loanIdFromRequestReceipt(receipt.logs))
    }

    @Test
    fun aRevertedReceiptHasStatusZero() = runBlocking {
        result("""{"status":"0x0","logs":[]}""")

        val receipt = client.getReceipt("0x" + "ef".repeat(32)).getOrThrow()!!

        assertFalse(receipt.succeeded)
        assertEquals("0x0", receipt.status)
        assertTrue(receipt.logs.isEmpty())
    }

    @Test
    fun aReceiptWithoutAStatusIsAServerProblem() = runBlocking {
        result("""{"logs":[]}""")

        assertEquals(AppError.Server, (client.getReceipt("0x01").exceptionOrNull() as AppException).error)
    }

    @Test
    fun aRevertWithCodeThreeReturnsItsDataForTheRevertDecoder() = runBlocking {
        val data = RevertDecoder.LOAN_NOT_OPEN + "0".repeat(63) + "5"
        reply("""{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"execution reverted","data":"$data"}}""")

        val outcome = client.call(token, "0x1234", owner).getOrThrow()

        assertEquals(CallOutcome.Reverted(data), outcome)
        assertEquals(ChainFailure.NotOpen, RevertDecoder.decode((outcome as CallOutcome.Reverted).data))
    }

    @Test
    fun aRevertThatSomeNodesAnswerWithAnotherCodeIsStillARevert() = runBlocking {
        reply("""{"jsonrpc":"2.0","id":1,"error":{"code":-32000,"message":"execution reverted"}}""")

        assertEquals(CallOutcome.Reverted(null), client.call(token, "0x1234", owner).getOrThrow())
    }

    @Test
    fun anotherRpcErrorIsAServerProblem() = runBlocking {
        reply("""{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"invalid argument"}}""")

        assertEquals(AppError.Server, (client.call(token, "0x1234", owner).exceptionOrNull() as AppException).error)
    }

    @Test
    fun theBalanceInWeiReadsFromHex() = runBlocking {
        result("\"0x470de4df820000\"")

        val wei = client.getBalance(owner).getOrThrow()

        assertEquals(BigInteger("20000000000000000"), wei)
        val body = sentBody()
        assertEquals("eth_getBalance", body.getValue("method").jsonPrimitive.content)
        assertEquals(owner, body.getValue("params").jsonArray[0].jsonPrimitive.content)
        assertEquals("latest", body.getValue("params").jsonArray[1].jsonPrimitive.content)
    }

    @Test
    fun aZeroBalanceAndABadQuantity() = runBlocking {
        result("\"0x0\"")
        result("\"0xzz\"")

        assertEquals(BigInteger.ZERO, client.getBalance(owner).getOrThrow())
        assertEquals(AppError.Server, (client.getBalance(owner).exceptionOrNull() as AppException).error)
    }

    @Test
    fun rateLimitsAndServerErrorsMapToServer() = runBlocking {
        reply("{}", code = 429)
        reply("oops", code = 500)
        reply("{}", code = 403)

        repeat(3) {
            assertEquals(AppError.Server, (client.getBalance(owner).exceptionOrNull() as AppException).error)
        }
    }

    @Test
    fun anAnswerThatIsNotJsonIsAServerProblem() = runBlocking {
        reply("<html>gateway</html>")

        assertEquals(AppError.Server, (client.getBalance(owner).exceptionOrNull() as AppException).error)
    }

    @Test
    fun aClosedServerIsOfflineAndNeverNamesTheUrl() = runBlocking {
        server.shutdown()

        val failure = client.getBalance(owner).exceptionOrNull() as AppException

        assertEquals(AppError.Offline, failure.error)
        assertNull(failure.cause)
        assertFalse(failure.message.orEmpty().contains(secret))
        assertFalse(failure.message.orEmpty().contains(server.hostName))
    }

    @Test
    fun noFailureMessageContainsTheUrlOrItsKey() = runBlocking {
        reply("oops", code = 500)

        val failure = client.getReceipt("0x01").exceptionOrNull() as AppException

        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.stackTraceToString().contains(secret))
    }
}
