package com.nidhi.app

import android.content.Context
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

object TransactionManager {

    /** Full initiate → sign → confirm flow. Must complete within 5s. */
    suspend fun sendMoney(
        context: Context,
        text: String,
        languageCode: String,
        deviceId: String
    ): ConfirmResponse {
        val nonce = generateNonce()

        // Step 1: Initiate — get challenge + signing payload
        val init = NidhiClient.api(context).initiate(
            InitiateRequest(
                textFallback = text,
                languageCode = languageCode,
                deviceId = deviceId,
                clientNonce = nonce
            )
        )

        if (!init.success || init.txId == null || init.signingPayload == null) {
            throw RuntimeException(init.errorMessage ?: "Initiation failed")
        }

        // Step 2: Sign the payload using the returned private key (demo mode)
        // In production: use Android Keystore — key never leaves device
        val signature = signPayload(
            payload = init.signingPayload,
            privateKeyB64 = init.privateKeyBase64
                ?: throw RuntimeException("No private key returned")
        )

        // Step 3: Confirm — must happen within 5s of initiate
        return NidhiClient.api(context).confirm(
            ConfirmRequest(
                txId = init.txId,
                deviceId = deviceId,
                clientNonce = nonce,
                signature = signature
            )
        )
    }

    fun signPayload(payload: String, privateKeyB64: String): String {
        val keyBytes = Base64.getDecoder().decode(privateKeyB64)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(keySpec)

        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
    }

    fun generateNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun formatAmount(paise: Int): String {
        val rupees = paise / 100
        return "₹$rupees"
    }
}
