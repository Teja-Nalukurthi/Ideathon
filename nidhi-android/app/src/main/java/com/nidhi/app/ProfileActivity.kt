package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.nidhi.app.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val languageDisplayNames = mapOf(
        "en" to "English",
        "hi" to "Hindi",
        "te" to "Telugu",
        "ta" to "Tamil",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "mr" to "Marathi",
        "bn" to "Bengali"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val session = SessionManager.get(this)
        if (session == null) { finish(); return }

        // Avatar initial
        val initial = session.fullName.firstOrNull()?.uppercase() ?: "?"
        binding.tvAvatar.text = initial

        // Info fields
        binding.tvFullName.text      = session.fullName.ifBlank { "—" }
        binding.tvPhone.text         = session.phone.ifBlank { "—" }
        binding.tvAccountNumber.text = session.accountNumber.ifBlank { "—" }
        binding.tvLanguage.text      = languageDisplayNames[session.languageCode] ?: session.languageCode
        binding.tvStatus.text        = "Active"

        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                SessionManager.clear(this)
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
