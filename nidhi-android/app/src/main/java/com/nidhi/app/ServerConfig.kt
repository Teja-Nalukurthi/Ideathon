package com.nidhi.app

import android.content.Context

object ServerConfig {

    private const val PREFS = "nidhi_prefs"
    private const val KEY_URL = "server_url"
    private const val DEFAULT_URL = "http://10.0.2.2:8081/"

    fun getUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun saveUrl(context: Context, url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, normalized).apply()
    }

    fun isConfigured(context: Context): Boolean {
        val url = getUrl(context)
        return url != DEFAULT_URL && url.isNotBlank()
    }
}
