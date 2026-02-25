package com.nidhi.app

import retrofit2.http.Body
import retrofit2.http.POST

// ── Request models ─────────────────────────────────────────────

data class InitiateRequest(
    val textFallback: String,
    val languageCode: String,
    val deviceId: String,
    val clientNonce: String? = null
)

data class ConfirmRequest(
    val txId: String,
    val deviceId: String,
    val clientNonce: String,
    val signature: String
)

// ── Response models ────────────────────────────────────────────

data class InitiateResponse(
    val success: Boolean,
    val txId: String?,
    val recipient: String?,
    val amountPaise: Int,
    val formattedAmount: String?,
    val confirmationText: String?,
    val signingPayload: String?,
    val privateKeyBase64: String?,
    val expiresAtMs: Long,
    val sourcesActive: List<String>,
    val errorMessage: String?
)

data class ConfirmResponse(
    val success: Boolean,
    val txId: String?,
    val referenceNumber: String?,
    val recipient: String?,
    val amountPaise: Int,
    val formattedAmount: String?,
    val seedHex: String?,
    val sourcesActive: List<String>,
    val responseTimeMs: Long,
    val errorCode: String?,
    val errorMessage: String?
)

// ── API interface ──────────────────────────────────────────────

interface NidhiApi {
    @POST("transaction/initiate")
    suspend fun initiate(@Body req: InitiateRequest): InitiateResponse

    @POST("transaction/confirm")
    suspend fun confirm(@Body req: ConfirmRequest): ConfirmResponse
}
