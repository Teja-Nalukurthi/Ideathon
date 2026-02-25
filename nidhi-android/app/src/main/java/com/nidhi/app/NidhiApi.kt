package com.nidhi.app

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── Request models ─────────────────────────────────────────────

data class LoginRequest(
    val phone: String,
    val pin:   String
)

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

// ── Response models ────────────────────────────────────────────────────

data class LoginResponse(
    val success:       Boolean,
    val phone:         String?,
    val fullName:      String?,
    val accountNumber: String?,
    val languageCode:  String?,
    val error:         String?
)

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

data class AccountInfoResponse(
    val accountNumber: String?,
    val fullName:      String?,
    val phone:         String?,
    val balancePaise:  Long,
    val languageCode:  String?,
    val active:        Boolean,
    val deviceId:      String?      // TEE-linked device identifier, null while not registered
)

data class TransactionItem(
    val referenceId:   String?,
    val txType:        String?,
    val fromAccount:   String?,
    val fromName:      String?,
    val toAccount:     String?,
    val toName:        String?,
    val amountPaise:   Long,
    val status:        String?,
    val failureReason: String?,
    val adminNote:     String?,
    val createdAt:     String?
)

// ── API interface ──────────────────────────────────────────────

interface NidhiApi {
    @POST("bank/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @GET("bank/account/info")
    suspend fun getAccountInfo(@Query("account") account: String): AccountInfoResponse

    @GET("bank/account/transactions")
    suspend fun getTransactions(@Query("account") account: String): List<TransactionItem>

    @POST("transaction/initiate")
    suspend fun initiate(@Body req: InitiateRequest): InitiateResponse

    @POST("transaction/confirm")
    suspend fun confirm(@Body req: ConfirmRequest): ConfirmResponse

    @POST("bank/device/register")
    suspend fun registerDevice(@Body body: Map<String, String>): Map<String, Any>
}
