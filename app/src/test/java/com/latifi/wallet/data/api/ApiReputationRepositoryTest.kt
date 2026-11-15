package com.latifi.wallet.data.api

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiReputationRepositoryTest {
    private val rig = ApiTestRig()
    private val repository = ApiReputationRepository(rig.api)
    private val address = "0x1111111111111111111111111111111111111111"
    private val fixture = ApiClientFactory.json.parseToJsonElement(ApiTestRig.fixture("reputation.json")).jsonObject

    @After
    fun tearDown() = rig.close()

    @Test
    fun getReadsTheScoreTheLevelAndTheCounters() = runBlocking {
        rig.enqueueJson(fixture.getValue("summary").toString())

        val summary = repository.get(address).getOrThrow()

        assertEquals("/api/v1/reputation/$address", rig.server.takeRequest().path)
        assertEquals(620, summary.score)
        assertEquals(ReputationLevel.TRUSTED, summary.level)
        assertEquals(1, summary.lateCount)
    }

    @Test
    fun anAddressWithNoProfileFailsWithReputationNotFound() = runBlocking {
        rig.enqueueError(404, "REPUTATION_NOT_FOUND")

        val failure = repository.get(address).exceptionOrNull() as AppException

        assertEquals(AppError.ReputationNotFound, failure.error)
    }

    @Test
    fun historySendsTheTypeFilterAndThePage() = runBlocking {
        rig.enqueueJson(fixture.getValue("history").toString())

        val page = repository.history(address, ReputationEventType.REPAID_LATE, page = 1, size = 20).getOrThrow()

        val url = rig.server.takeRequest().requestUrl!!
        assertEquals("/api/v1/reputation/$address/history", url.encodedPath)
        assertEquals("REPAID_LATE", url.queryParameter("type"))
        assertEquals("1", url.queryParameter("page"))
        assertEquals("20", url.queryParameter("size"))
        assertEquals(3, page.items.size)
        assertEquals(3L, page.totalItems)
    }

    @Test
    fun historyWithoutAFilterSendsNoTypeParameter() = runBlocking {
        rig.enqueueJson(fixture.getValue("history").toString())

        repository.history(address, null, page = 0, size = 20).getOrThrow()

        assertNull(rig.server.takeRequest().requestUrl!!.queryParameter("type"))
    }

    @Test
    fun historyOfAnUnknownAddressFailsWithReputationNotFound() = runBlocking {
        rig.enqueueError(404, "REPUTATION_NOT_FOUND")

        val failure = repository.history(address, null, 0, 20).exceptionOrNull() as AppException

        assertEquals(AppError.ReputationNotFound, failure.error)
    }
}
