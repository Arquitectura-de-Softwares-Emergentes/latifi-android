package com.latifi.wallet.data.chain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class ReceiptLogParserTest {
    private val borrowerTopic = "0x" + "1111111111111111111111111111111111111111".padStart(64, '0')

    private fun topicFor(loanId: Long): String = "0x" + BigInteger.valueOf(loanId).toString(16).padStart(64, '0')

    private fun requestLog(loanId: Long, address: String = Deployment.LOANBOOK_ADDRESS) = LogEntry(
        address = address,
        topics = listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC, topicFor(loanId), borrowerTopic),
        data = "0x",
    )

    @Test
    fun theTopicStartsWithTheResearchPrefix() {
        assertTrue(ReceiptLogParser.LOAN_REQUESTED_TOPIC, ReceiptLogParser.LOAN_REQUESTED_TOPIC.startsWith("0x1f627236"))
        assertEquals(66, ReceiptLogParser.LOAN_REQUESTED_TOPIC.length)
    }

    @Test
    fun theSignatureInCodeEqualsTheOneRebuiltFromTheFrozenEventAbi() {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("loan-events.v0.json")) {
            "loan-events.v0.json not found"
        }
        val events: JsonArray = Json.parseToJsonElement(stream.bufferedReader().use { it.readText() }).jsonArray
        val event = events.map { it.jsonObject }.single { it.getValue("name").jsonPrimitive.content == "LoanRequested" }
        val types = event.getValue("inputs").jsonArray.joinToString(",") {
            it.jsonObject.getValue("type").jsonPrimitive.content
        }
        assertEquals("LoanRequested($types)", ReceiptLogParser.LOAN_REQUESTED_SIGNATURE)
    }

    @Test
    fun loanIdComesFromTheSecondTopic() {
        assertEquals(BigInteger.valueOf(42), ReceiptLogParser.loanIdFromRequestReceipt(listOf(requestLog(42))))
    }

    @Test
    fun aForeignLogIsIgnored() {
        val foreign = LogEntry(
            address = Deployment.STABLECOIN_ADDRESS,
            topics = listOf("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", topicFor(9)),
            data = "0x",
        )
        assertEquals(BigInteger.valueOf(42), ReceiptLogParser.loanIdFromRequestReceipt(listOf(foreign, requestLog(42))))
    }

    @Test
    fun theRequestEventFromAnotherContractIsIgnored() {
        val spoof = requestLog(99, address = "0x2222222222222222222222222222222222222222")
        assertNull(ReceiptLogParser.loanIdFromRequestReceipt(listOf(spoof)))
        assertEquals(BigInteger.valueOf(5), ReceiptLogParser.loanIdFromRequestReceipt(listOf(spoof, requestLog(5))))
    }

    @Test
    fun noMatchingLogReturnsNull() {
        assertNull(ReceiptLogParser.loanIdFromRequestReceipt(emptyList()))
        val other = LogEntry(Deployment.LOANBOOK_ADDRESS, listOf("0x" + "ab".repeat(32), topicFor(1)), "0x")
        assertNull(ReceiptLogParser.loanIdFromRequestReceipt(listOf(other)))
    }

    @Test
    fun aRequestLogWithoutALoanIdTopicIsSkippedWithoutThrowing() {
        val short = LogEntry(Deployment.LOANBOOK_ADDRESS, listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC), "0x")
        val bad = LogEntry(Deployment.LOANBOOK_ADDRESS, listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC, "0xzz"), "0x")
        assertNull(ReceiptLogParser.loanIdFromRequestReceipt(listOf(short, bad)))
    }

    @Test
    fun addressAndTopicComparisonIgnoreLetterCase() {
        val upper = LogEntry(
            address = Deployment.LOANBOOK_ADDRESS.uppercase().replace("0X", "0x"),
            topics = listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC.uppercase().replace("0X", "0x"), topicFor(12)),
            data = "0x",
        )
        assertEquals(BigInteger.valueOf(12), ReceiptLogParser.loanIdFromRequestReceipt(listOf(upper)))
    }
}
