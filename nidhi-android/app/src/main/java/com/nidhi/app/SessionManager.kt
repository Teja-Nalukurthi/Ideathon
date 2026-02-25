package com.nidhi.app

import android.content.Context

object SessionManager {

    private const val PREFS = "nidhi_session"

    data class UserSession(
        val phone: String,
        val fullName: String,
        val accountNumber: String,
        val languageCode: String,
        val balancePaise: Long = 0L
    )

    fun save(context: Context, session: UserSession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString("phone", session.phone)
            putString("fullName", session.fullName)
            putString("accountNumber", session.accountNumber)
            putString("languageCode", session.languageCode)
            putLong("balancePaise", session.balancePaise)
            apply()
        }
    }

    fun get(context: Context): UserSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val phone = prefs.getString("phone", null) ?: return null
        return UserSession(
            phone         = phone,
            fullName      = prefs.getString("fullName", "") ?: "",
            accountNumber = prefs.getString("accountNumber", "") ?: "",
            languageCode  = prefs.getString("languageCode", "hi") ?: "hi",
            balancePaise  = prefs.getLong("balancePaise", 0L)
        )
    }

    fun updateBalance(context: Context, balancePaise: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("balancePaise", balancePaise).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun isLoggedIn(context: Context): Boolean = get(context) != null
}
