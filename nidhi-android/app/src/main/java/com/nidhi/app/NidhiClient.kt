package com.nidhi.app

import android.content.Context
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Retry interceptor for 2G networks.
 *
 * Retries up to [maxRetries] times with exponential back-off (1s, 2s, 4s …)
 * on any IOException (connection drop, timeout mid-read, etc.).
 *
 * /transaction/confirm is intentionally excluded: it moves money, so a
 * silent server-side success + network drop should not cause a double-send.
 * The user will see an error and can check their balance before retrying.
 */
private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Never auto-retry the payment confirm step
        if (path.contains("/transaction/confirm")) {
            return chain.proceed(request)
        }

        var attempt = 0
        var lastException: IOException? = null
        while (attempt <= maxRetries) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt > maxRetries) break
                val delayMs = (1000L shl (attempt - 1)).coerceAtMost(8000L) // 1s, 2s, 4s, 8s
                Log.w("NidhiRetry", "Attempt $attempt failed for $path, retrying in ${delayMs}ms: ${e.message}")
                Thread.sleep(delayMs)
            }
        }
        throw lastException!!
    }
}

object NidhiClient {

    // 2G-tuned timeouts:
    //  - connect: 30s  (2G handshake + DNS can be slow)
    //  - write:   30s  (contact lookup-batch payload can be a few KB)
    //  - read:    90s  (Google Speech API + translation adds ~5–10s server-side;
    //                   on 2G a round-trip easily takes 10–20s)
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(RetryInterceptor(maxRetries = 3))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC   // BODY logging wastes bytes on 2G
        })
        .build()

    // Rebuilt every call so URL changes take effect immediately
    fun api(context: Context): NidhiApi {
        val url = ServerConfig.getUrl(context)
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NidhiApi::class.java)
    }
}
