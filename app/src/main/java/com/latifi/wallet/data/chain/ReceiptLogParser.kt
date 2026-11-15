package com.latifi.wallet.data.chain

import org.web3j.crypto.Hash
import java.math.BigInteger

/** One log of a transaction receipt, with lowercase or mixed case hex as an RPC returns it. */
data class LogEntry(val address: String, val topics: List<String>, val data: String)

/** Reads the new loan id from the receipt of a `requestLoan` transaction. */
object ReceiptLogParser {
    /** Signature of the event, in the order of abi/loan-events.v0.json. */
    const val LOAN_REQUESTED_SIGNATURE = "LoanRequested(uint256,address,uint256,uint16,uint32,uint8)"

    /** Topic 0 of `LoanRequested`, computed once with the web3j hash. It starts with `0x1f627236`. */
    val LOAN_REQUESTED_TOPIC: String = Hash.sha3String(LOAN_REQUESTED_SIGNATURE).lowercase()

    /**
     * The loan id from `topics[1]` of the `LoanRequested` log emitted by the LoanBook address.
     * Logs of other contracts are ignored, and the result is null when no such log exists.
     */
    fun loanIdFromRequestReceipt(logs: List<LogEntry>): BigInteger? {
        for (log in logs) {
            if (!log.address.equals(Deployment.LOANBOOK_ADDRESS, ignoreCase = true)) continue
            if (log.topics.size < 2) continue
            if (!log.topics[0].equals(LOAN_REQUESTED_TOPIC, ignoreCase = true)) continue
            val topic = log.topics[1].lowercase().removePrefix("0x")
            if (topic.isEmpty() || topic.length > 64 || !topic.all { Character.digit(it, 16) >= 0 }) continue
            return BigInteger(topic, 16)
        }
        return null
    }
}
