package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
        binding.btnChangeServer.setOnClickListener { showServerUrlDialog() }

        updateServerLabel()
    }

    private fun updateServerLabel() {
        binding.tvServerUrl.text = ServerConfig.getUrl(this).trimEnd('/')
    }

    private fun showServerUrlDialog() {
        val current = ServerConfig.getUrl(this).trimEnd('/')

        // Build dialog layout programmatically — no extra layout file needed
        val til = TextInputLayout(this).apply {
            hint = "Server URL  (e.g. http://192.168.1.5:8081)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 24, 48, 8)
        }
        val et = TextInputEditText(this).apply {
            setText(current)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSelection(text?.length ?: 0)
        }
        til.addView(et)

        AlertDialog.Builder(this)
            .setTitle("⚙ Server URL")
            .setMessage("Enter the base URL of the Nidhi backend. Get it from the admin dashboard's Device Connection card.")
            .setView(til)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = et.text?.toString()?.trim() ?: ""
                if (newUrl.isBlank() || !newUrl.startsWith("http")) {
                    Snackbar.make(binding.root, "Invalid URL — must start with http://", Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                ServerConfig.saveUrl(this, newUrl)
                updateServerLabel()
                Snackbar.make(binding.root, "Server URL saved", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        startActivity(Intent(this, HomeActivity::class.java).apply {
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
