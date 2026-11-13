package com.latifi.wallet.data.chain

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.api.ApiClientFactory
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Instant
import java.util.Locale

class RpcChainReadRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val repository = RpcChainReadRepository(OkHttpRpcClient(server.url("/").toString(), ApiClientFactory.rpcHttpClient()))
    private val owner = "0xAbC1111111111111111111111111111111111111"
    private val spender = Deployment.LOANBOOK_ADDRESS

    @After
    fun tearDown() = server.shutdown()

    private fun word(value: BigInteger) = value.toString(16).padStart(64, '0')

    private fun word(value: Long) = word(BigInteger.valueOf(value))

    private fun result(hex: String) {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody("""{"jsonrpc":"2.0","id":1,"result":"$hex"}"""),
        )
    }

    private fun revert(data: String) {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":1,"error":{"code":3,"message":"execution reverted","data":"$data"}}"""),
        )
    }

    private fun sentCall() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        .getValue("params").jsonArray[0].jsonObject

    @Test
    fun balanceOfDecodesToOneThousandTestTokens() = runBlocking {
        result("0x" + word(1_000_000_000L))

        val balance = repository.tokenBalance(owner).getOrThrow()

        assertEquals(TokenAmount.ofTokens(1_000), balance)
        assertEquals("1,000.00 tUSD", balance.format(Locale.US))
        val call = sentCall()
        assertEquals(Deployment.STABLECOIN_ADDRESS, call.getValue("to").jsonPrimitive.content)
        assertEquals(Erc20Abi.balanceOf(owner), call.getValue("data").jsonPrimitive.content)
        assertTrue(call.getValue("data").jsonPrimitive.content.startsWith("0x70a08231"))
    }

    @Test
    fun allowanceDecodesTheExactAmount() = runBlocking {
        result("0x" + word(250_000_000L))

        val allowance = repository.allowance(owner, spender).getOrThrow()

        assertEquals(BigInteger.valueOf(250_000_000L), allowance)
        val call = sentCall()
        assertEquals(Erc20Abi.allowance(owner, spender), call.getValue("data").jsonPrimitive.content)
        assertTrue(call.getValue("data").jsonPrimitive.content.startsWith("0xdd62ed3e"))
    }

    @Test
    fun aSeventyEightDigitAllowanceSurvives() = runBlocking {
        val huge = BigInteger.TWO.pow(256).subtract(BigInteger.ONE)
        result("0x" + word(huge))

        assertEquals(huge, repository.allowance(owner, spender).getOrThrow())
    }

    @Test
    fun nextInstallmentDecodesTheIndexTheDueTimeAndTheAmount() = runBlocking {
        result("0x" + word(2) + word(1_767_268_800L) + word(50_000_000L))

        val next = repository.nextInstallment(BigInteger.valueOf(5)).getOrThrow()

        assertEquals(2, next.index)
        assertEquals(Instant.ofEpochSecond(1_767_268_800L), next.dueAt)
        assertEquals(TokenAmount(BigInteger.valueOf(50_000_000L)), next.unpaid)
        val call = sentCall()
        assertEquals(Deployment.LOANBOOK_ADDRESS, call.getValue("to").jsonPrimitive.content)
        assertEquals(LoanBookAbi.nextInstallment(BigInteger.valueOf(5)), call.getValue("data").jsonPrimitive.content)
    }

    @Test
    fun aLoanThatIsNotActiveIsNotFound() = runBlocking {
        revert(RevertDecoder.LOAN_NOT_ACTIVE + word(5))

        val failure = repository.nextInstallment(BigInteger.valueOf(5)).exceptionOrNull() as AppException

        assertEquals(AppError.NotFound, failure.error)
    }

    @Test
    fun aShortAnswerIsAServerProblem() = runBlocking {
        result("0x" + word(2))

        val failure = repository.nextInstallment(BigInteger.valueOf(5)).exceptionOrNull() as AppException

        assertEquals(AppError.Server, failure.error)
    }

    @Test
    fun theNativeBalanceIsReadInWei() = runBlocking {
        result("0x470de4df820000")

        val wei = repository.polBalance(owner).getOrThrow()

        assertEquals(BigInteger("20000000000000000"), wei)
        // 0.02 POL: 2 * 10^16 wei with 18 decimals.
        assertEquals("0.02", java.math.BigDecimal(wei, 18).stripTrailingZeros().toPlainString())
    }

    @Test
    fun anInvalidAddressSendsNothingToTheNode() = runBlocking {
        val failure = repository.tokenBalance("not an address").exceptionOrNull() as AppException

        assertEquals(AppError.Validation(), failure.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aRateLimitedNodeReachesTheCallersUnchanged() = runBlocking {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "5").setBody("{}"))
        }

        val failures = listOf(
            repository.tokenBalance(owner),
            repository.allowance(owner, spender),
            repository.nextInstallment(BigInteger.valueOf(5)),
            repository.polBalance(owner),
        ).map { (it.exceptionOrNull() as AppException).error }

        assertEquals(List(4) { AppError.RateLimited(5) }, failures)
    }

    @Test
    fun aNodeThatIsDownIsOffline() = runBlocking {
        server.shutdown()

        val failure = repository.tokenBalance(owner).exceptionOrNull() as AppException

        assertEquals(AppError.Offline, failure.error)
    }
}
