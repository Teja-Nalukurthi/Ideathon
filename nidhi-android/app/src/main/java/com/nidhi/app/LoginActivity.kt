package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in — skip straight to main
        if (SessionManager.isLoggedIn(this)) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { doLogin() }
    }

    private fun doLogin() {
        val phone = binding.etPhone.text?.toString()?.trim() ?: ""
        val pin   = binding.etPin.text?.toString()?.trim()   ?: ""

        if (phone.isBlank()) {
            binding.tilPhone.error = "Enter your registered phone number"
            return
        }
        binding.tilPhone.error = null

        if (pin.length < 4) {
            binding.tilPin.error = "PIN must be at least 4 digits"
            return
        }
        binding.tilPin.error = null

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = NidhiClient.api(this@LoginActivity)
                    .login(LoginRequest(phone, pin))
                if (resp.success && resp.fullName != null) {
                    SessionManager.save(
                        this@LoginActivity,
                        SessionManager.UserSession(
                            phone         = resp.phone ?: phone,
                            fullName      = resp.fullName,
                            accountNumber = resp.accountNumber ?: "",
                            languageCode  = resp.languageCode ?: "hi"
                        )
                    )
                    goToMain()
                } else {
                    setLoading(false)
                    showError(resp.error ?: "Invalid phone number or PIN. Try again.")
                }
            } catch (e: Exception) {
                setLoading(false)
                showError("Cannot reach bank server. Check your connection.")
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun showError(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.error))
            .show()
    }

    private fun setLoading(on: Boolean) {
        binding.progressBar.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !on
        binding.etPhone.isEnabled  = !on
        binding.etPin.isEnabled    = !on
    }
}
