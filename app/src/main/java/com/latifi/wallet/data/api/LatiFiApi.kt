package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.ChallengeRequestDto
import com.latifi.wallet.data.api.dto.ChallengeResponseDto
import com.latifi.wallet.data.api.dto.ConvertResponseDto
import com.latifi.wallet.data.api.dto.LoanPageDto
import com.latifi.wallet.data.api.dto.LoanSummaryDto
import com.latifi.wallet.data.api.dto.ProfileResponseDto
import com.latifi.wallet.data.api.dto.ProfileUpdateRequestDto
import com.latifi.wallet.data.api.dto.RateQuoteDto
import com.latifi.wallet.data.api.dto.ReputationHistoryPageDto
import com.latifi.wallet.data.api.dto.ReputationResponseDto
import com.latifi.wallet.data.api.dto.TokenResponseDto
import com.latifi.wallet.data.api.dto.VerifyRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The ten operations of OpenAPI 0.2.0, one method per `operationId` and named exactly like it.
 * `OperationIdsTest` compares these names with a copy of the contract, so a renamed or missing
 * operation fails the build. Paths are relative to the base path `/api/v1/`.
 *
 * Every method returns [Response] so the status and the headers stay readable (`Retry-After`),
 * and every `Uint256` travels as a digit string.
 */
interface LatiFiApi {
    @POST("auth/challenge")
    suspend fun issueChallenge(@Body body: ChallengeRequestDto): Response<ChallengeResponseDto>

    @POST("auth/verify")
    suspend fun verifyChallenge(@Body body: VerifyRequestDto): Response<TokenResponseDto>

    @GET("profiles/{address}")
    suspend fun getProfile(@Path("address") address: String): Response<ProfileResponseDto>

    /** 201 when the profile was created and 200 when it was replaced, so both count as success. */
    @PUT("profiles/{address}")
    suspend fun upsertProfile(
        @Path("address") address: String,
        @Body body: ProfileUpdateRequestDto,
    ): Response<ProfileResponseDto>

    @GET("reputation/{address}")
    suspend fun getReputation(@Path("address") address: String): Response<ReputationResponseDto>

    @GET("reputation/{address}/history")
    suspend fun getReputationHistory(
        @Path("address") address: String,
        @Query("type") type: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): Response<ReputationHistoryPageDto>

    /** A null query parameter is not sent. */
    @GET("loans")
    suspend fun listLoans(
        @Query("status") status: String? = null,
        @Query("minAmount") minAmount: String? = null,
        @Query("maxAmount") maxAmount: String? = null,
        @Query("maxTermDays") maxTermDays: Int? = null,
        @Query("minLevel") minLevel: String? = null,
        @Query("borrower") borrower: String? = null,
        @Query("lender") lender: String? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): Response<LoanPageDto>

    /** [loanId] is a decimal string of the `uint256` identifier. */
    @GET("loans/{loanId}")
    suspend fun getLoan(@Path("loanId") loanId: String): Response<LoanSummaryDto>

    @GET("exchange-rate/convert")
    suspend fun convertAmount(
        @Query("amount") amount: String,
        @Query("currency") currency: String,
    ): Response<ConvertResponseDto>

    /** The contract answers a bare JSON array. */
    @GET("exchange-rate/rates")
    suspend fun listRates(): Response<List<RateQuoteDto>>
}
