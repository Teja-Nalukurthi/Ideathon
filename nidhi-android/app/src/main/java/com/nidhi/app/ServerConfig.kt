package com.nidhi.app

import android.content.Context

object ServerConfig {

    private const val PREFS = "nidhi_prefs"
    private const val KEY_URL = "server_url"

    /** Hardcoded bank-backend URL. Update to match the laptop's current IP on your network. */
    const val BASE_URL = "http://10.58.19.75:8081/"

    fun getUrl(context: Context): String {
        // Allow runtime override via prefs (for dev/testing), otherwise use hardcoded default
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
        return if (!saved.isNullOrBlank() && saved != "http://10.0.2.2:8081/") saved else BASE_URL
    }

    fun saveUrl(context: Context, url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, normalized).apply()
    }

    /** Always considered configured since the URL is now embedded. */
    fun isConfigured(context: Context): Boolean = true
}
