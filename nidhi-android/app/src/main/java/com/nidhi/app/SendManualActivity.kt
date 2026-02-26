package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivitySendManualBinding
import kotlinx.coroutines.launch

class SendManualActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendManualBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendManualBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Send Money"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Pre-fill from contacts/QR
        val prefillAcct = intent.getStringExtra("accountNumber")
        val prefillName = intent.getStringExtra("recipientName")
        if (!prefillAcct.isNullOrBlank()) {
            binding.etAccountNumber.setText(prefillAcct)
            binding.etAccountNumber.isEnabled = false   // lock if coming from QR/contact
        }
        if (!prefillName.isNullOrBlank()) {
            binding.tvRecipientHint.text = "To: $prefillName"
            binding.tvRecipientHint.visibility = View.VISIBLE
        }

        binding.btnProceed.setOnClickListener { proceed() }

        lifecycleScope.launch {
            viewModel.initiateState.collect { state ->
                when (state) {
                    is InitiateState.Idle    -> showIdle()
                    is InitiateState.Loading -> showLoading()
                    is InitiateState.Ready   -> {
                        showIdle()
                        val resp = state.response
                        startActivity(Intent(this@SendManualActivity, ConfirmActivity::class.java).apply {
                            putExtra("recipient",        resp.recipient ?: binding.etAccountNumber.text.toString().trim())
                            putExtra("formattedAmount",  resp.formattedAmount)
                            putExtra("confirmationText", resp.confirmationText)
                            putExtra("signingPayload",   resp.signingPayload)
                            putExtra("privateKeyBase64", resp.privateKeyBase64)
                            putExtra("txId",             resp.txId)
                            putExtra("deviceId",         SessionManager.get(this@SendManualActivity)?.accountNumber ?: "")
                            putExtra("nonce",            viewModel.lastNonce)
                            putExtra("expiresAtMs",      resp.expiresAtMs)
                            putExtra("sourcesActive",    resp.sourcesActive.toTypedArray())
                        })
                        viewModel.resetInitiate()
                    }
                    is InitiateState.Error -> {
                        showIdle()
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        viewModel.resetInitiate()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun proceed() {
        val session = SessionManager.get(this) ?: return
        val acct   = binding.etAccountNumber.text.toString().trim()
        val amtStr = binding.etAmount.text.toString().trim()

        if (acct.isBlank()) {
            binding.tilAccountNumber.error = "Enter account number"; return
        }
        binding.tilAccountNumber.error = null

        val amountRupees = amtStr.toDoubleOrNull()
        if (amountRupees == null || amountRupees <= 0) {
            binding.tilAmount.error = "Enter a valid amount"; return
        }
        binding.tilAmount.error = null

        val langCode = session.languageCode.ifBlank { "en" }
        val deviceId = session.accountNumber           // auto-TEE device id
        val text     = "send ${amountRupees.toLong()} rupees to $acct"

        viewModel.initiate(text, langCode, deviceId)
    }

    private fun showIdle() {
        binding.progressSend.visibility = View.GONE
        binding.btnProceed.isEnabled    = true
    }

    private fun showLoading() {
        binding.progressSend.visibility = View.VISIBLE
        binding.btnProceed.isEnabled    = false
    }
}
