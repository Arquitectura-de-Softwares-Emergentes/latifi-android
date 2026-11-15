package com.latifi.wallet.data.chain

import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.chain.Receipt
import com.latifi.wallet.domain.chain.RpcClient
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A small JSON-RPC 2.0 client for the three reads the app needs: `eth_call`,
 * `eth_getTransactionReceipt` and `eth_getBalance`. It is hand written on OkHttp and
 * kotlinx.serialization, so the app does not need the web3j core artifact.
 *
 * The [url] may carry a provider key in its path. It is never logged, never put in an exception
 * message and never attached as a cause, so a failure report cannot leak it. [client] must be a
 * plain client without the API interceptors: the API token never travels to the node.
 */
class OkHttpRpcClient(
    private val url: String,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : RpcClient {
    private val nextId = AtomicLong(1)

    override suspend fun call(to: String, data: String, from: String): Result<CallOutcome> {
        val transaction = buildJsonObject {
            if (from.isNotBlank()) put("from", JsonPrimitive(from))
            put("to", JsonPrimitive(to))
            put("data", JsonPrimitive(data))
        }
        return rpc("eth_call", JsonArray(listOf(transaction, JsonPrimitive(LATEST)))).mapReply { reply ->
            when (reply) {
                is Reply.Value -> {
                    val hex = reply.result.stringOrNull() ?: return@mapReply fail(AppError.Server)
                    Result.success(CallOutcome.Success(hex))
                }
                is Reply.Error ->
                    if (reply.isRevert) Result.success(CallOutcome.Reverted(reply.data)) else fail(reply.failure())
            }
        }
    }

    override suspend fun getReceipt(hash: String): Result<Receipt?> =
        rpc("eth_getTransactionReceipt", JsonArray(listOf(JsonPrimitive(hash)))).mapReply { reply ->
            when (reply) {
                is Reply.Error -> fail(reply.failure())
                is Reply.Value -> {
                    // A null result is the normal answer while the transaction is pending or unknown.
                    val element = reply.result
                    if (element == null || element is JsonNull) {
                        Result.success(null)
                    } else {
                        parseReceipt(element)
                    }
                }
            }
        }

    override suspend fun getBalance(address: String): Result<BigInteger> =
        rpc("eth_getBalance", JsonArray(listOf(JsonPrimitive(address), JsonPrimitive(LATEST)))).mapReply { reply ->
            when (reply) {
                is Reply.Error -> fail(reply.failure())
                is Reply.Value -> {
                    val hex = reply.result.stringOrNull()
                    val value = hex?.let { quantity(it) }
                    if (value == null) fail(AppError.Server) else Result.success(value)
                }
            }
        }

    private fun parseReceipt(element: JsonElement): Result<Receipt?> =
        try {
            val receipt = element.jsonObject
            val status = receipt["status"].stringOrNull() ?: return fail(AppError.Server)
            val logs = receipt["logs"]?.jsonArray.orEmpty().map { log ->
                val entry = log.jsonObject
                LogEntry(
                    address = entry.getValue("address").jsonPrimitive.content,
                    topics = entry["topics"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content },
                    data = entry["data"].stringOrNull().orEmpty(),
                )
            }
            Result.success(Receipt(status = status, logs = logs))
        } catch (e: IllegalArgumentException) {
            fail(AppError.Server)
        } catch (e: NoSuchElementException) {
            fail(AppError.Server)
        }

    /** One JSON-RPC exchange. The answer is a [Reply], and every transport problem is an [AppError]. */
    private suspend fun rpc(method: String, params: JsonArray): Result<Reply> {
        val body = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(nextId.getAndIncrement()))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return try {
            val answer = client.newCall(request).await().use {
                Answer(it.code, it.header("Retry-After"), it.body.string())
            }
            when {
                answer.status == HTTP_TOO_MANY_REQUESTS -> fail(AppError.RateLimited(retryAfterSeconds(answer.retryAfter)))
                answer.status !in HTTP_OK -> fail(AppError.Server)
                else -> Result.success(parseReply(answer.body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // No cause is attached: OkHttp messages can repeat the host and the path of the URL.
            fail(AppError.Offline)
        } catch (e: SerializationException) {
            fail(AppError.Server)
        } catch (e: IllegalArgumentException) {
            fail(AppError.Server)
        }
    }

    private fun parseReply(text: String): Reply {
        val root: JsonObject = json.parseToJsonElement(text).jsonObject
        val error = root["error"]
        if (error != null && error !is JsonNull) {
            val fields = error.jsonObject
            return Reply.Error(
                code = fields["code"]?.jsonPrimitive?.intOrNull,
                message = fields["message"].stringOrNull(),
                data = fields["data"].stringOrNull(),
            )
        }
        return Reply.Value(root["result"])
    }

    /** Status, the raw `Retry-After` header and the body of one answer. The header text is never kept past parsing. */
    private class Answer(val status: Int, val retryAfter: String?, val body: String)

    /** Whole seconds from a numeric `Retry-After`. An HTTP date, a negative or an absurd value gives null. */
    private fun retryAfterSeconds(header: String?): Int? =
        header?.trim()?.toIntOrNull()?.takeIf { it in 0..MAX_RETRY_AFTER_SECONDS }

    private sealed interface Reply {
        data class Value(val result: JsonElement?) : Reply

        data class Error(val code: Int?, val message: String?, val data: String?) : Reply {
            /** Code 3 is the standard answer of a reverted `eth_call`. Some nodes use `-32000` with the same words. */
            val isRevert: Boolean
                get() = code == REVERT_CODE || message?.startsWith("execution reverted", ignoreCase = true) == true

            /**
             * A rate limit answer (code -32005, or wording such as "rate limit", "too many requests" or
             * "limit exceeded") becomes the app's own message. The node text is never copied.
             */
            fun failure(): AppError {
                val text = message.orEmpty()
                val limited = code == RATE_LIMIT_CODE || RATE_LIMIT_WORDS.any { text.contains(it, ignoreCase = true) }
                return if (limited) AppError.RateLimited(null) else AppError.Server
            }
        }
    }

    private inline fun <T> Result<Reply>.mapReply(transform: (Reply) -> Result<T>): Result<T> =
        fold(onSuccess = transform, onFailure = { Result.failure(it) })

    private fun <T> fail(error: AppError): Result<T> = Result.failure(AppException(error))

    private fun JsonElement?.stringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    /** A hex quantity such as `0x470de4df820000`. `0x` and `0x0` are zero. */
    private fun quantity(hex: String): BigInteger? {
        val body = hex.removePrefix("0x").removePrefix("0X")
        if (body.isEmpty()) return BigInteger.ZERO
        return if (body.all { Character.digit(it, HEX_RADIX) >= 0 }) BigInteger(body, HEX_RADIX) else null
    }

    private suspend fun Call.await(): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancel() }
            enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                },
            )
        }

    private companion object {
        const val LATEST = "latest"
        const val REVERT_CODE = 3
        const val HEX_RADIX = 16
        const val RATE_LIMIT_CODE = -32005
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_RETRY_AFTER_SECONDS = 3600
        val RATE_LIMIT_WORDS = listOf("rate limit", "too many requests", "limit exceeded")
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val HTTP_OK = 200..299
    }
}
