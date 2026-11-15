package com.latifi.wallet.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The drift guard between the Retrofit interface and the contract. It reads the copy of
 * `latifi-api.v0.yaml` and fails when the version moves, when an operation is added, renamed or
 * removed, or when the HTTP method, the path or the query parameters of an operation change.
 */
class OperationIdsTest {
    private val expectedIds = setOf(
        "issueChallenge", "verifyChallenge", "getProfile", "upsertProfile", "getReputation",
        "getReputationHistory", "listLoans", "getLoan", "convertAmount", "listRates",
    )

    private val apiMethods: List<Method> =
        LatiFiApi::class.java.declaredMethods.filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }

    @Test
    fun theContractCopyIsVersionZeroPointTwoPointZero() {
        assertEquals("0.2.0", ContractSpec.version)
    }

    @Test
    fun theRetrofitMethodsAreExactlyTheOperationIdsOfTheContract() {
        val contract = ContractSpec.operations.map { it.id }.toSet()
        val retrofit = apiMethods.map { it.name }.toSet()

        val missing = contract - retrofit
        val extra = retrofit - contract
        assertTrue("Operations with no Retrofit method: $missing", missing.isEmpty())
        assertTrue("Retrofit methods with no operation in the contract: $extra", extra.isEmpty())
        assertEquals("The ten operations of 0.2.0", expectedIds, contract)
    }

    @Test
    fun everyOperationKeepsItsHttpMethodPathAndQueryParameters() {
        for (operation in ContractSpec.operations) {
            val method = apiMethods.single { it.name == operation.id }
            val (verb, path) = httpOf(method)
            assertEquals("HTTP method of ${operation.id}", operation.httpMethod, verb)
            assertEquals("Path of ${operation.id}", operation.path, "/$path")
            assertEquals("Query parameters of ${operation.id}", operation.queryParams, queryNamesOf(method))
        }
    }

    private fun httpOf(method: Method): Pair<String, String> =
        method.getAnnotation(GET::class.java)?.let { "GET" to it.value }
            ?: method.getAnnotation(POST::class.java)?.let { "POST" to it.value }
            ?: method.getAnnotation(PUT::class.java)?.let { "PUT" to it.value }
            ?: error("${method.name} has no HTTP annotation")

    private fun queryNamesOf(method: Method): Set<String> =
        method.parameterAnnotations.flatMap { it.toList() }.filterIsInstance<Query>().map { it.value }.toSet()
}
