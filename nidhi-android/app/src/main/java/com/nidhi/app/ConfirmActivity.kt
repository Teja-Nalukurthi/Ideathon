package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivityConfirmBinding
import kotlinx.coroutines.launch

class ConfirmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmBinding
    private val viewModel: ConfirmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recipient    = intent.getStringExtra("recipient") ?: "Unknown"
        val amount       = intent.getStringExtra("formattedAmount") ?: "--"
        val confirmText  = intent.getStringExtra("confirmationText") ?: ""
        val sigPayload   = intent.getStringExtra("signingPayload") ?: ""
        val privateKey   = intent.getStringExtra("privateKeyBase64") ?: ""
        val txId         = intent.getStringExtra("txId") ?: ""
        val deviceId     = intent.getStringExtra("deviceId") ?: ""
        val nonce        = intent.getStringExtra("nonce") ?: ""
        val expiresAtMs  = intent.getLongExtra("expiresAtMs", 0L)
        val sources      = intent.getStringArrayExtra("sourcesActive") ?: emptyArray()

        viewModel.setPending(txId, deviceId, nonce, sigPayload, privateKey)

        binding.tvRecipient.text  = recipient
        binding.tvAmount.text     = amount
        binding.tvConfirmText.text = confirmText

        val expiresIn = ((expiresAtMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        binding.tvExpiry.text = "Expires in ${expiresIn}s"

        bindEntropyChips(sources.toList())

        binding.btnConfirm.setOnClickListener { viewModel.confirm() }
        binding.btnCancel.setOnClickListener { finish() }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                when (state) {
                    is ConfirmState.Idle    -> showIdle()
                    is ConfirmState.Loading -> showLoading()
                    is ConfirmState.Success -> {
                        showIdle()
                        startActivity(Intent(this@ConfirmActivity, SuccessActivity::class.java).apply {
                            putExtra("referenceNumber",  state.result.referenceNumber)
                            putExtra("recipient",        state.result.recipient)
                            putExtra("formattedAmount",  state.result.formattedAmount)
                            putExtra("seedHex",          state.result.seedHex)
                            putExtra("responseTimeMs",   state.result.responseTimeMs)
                            putExtra("sourcesActive",    state.result.sourcesActive.toTypedArray())
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                        })
                        viewModel.reset()
                        finish()
                    }
                    is ConfirmState.Error -> {
                        showIdle()
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                            .setBackgroundTint(getColor(R.color.error)).show()
                        viewModel.reset()
                    }
                }
            }
        }
    }

    private fun bindEntropyChips(sources: List<String>) {
        binding.chipTrng.alpha    = if (sources.contains("TRNG")) 1f else 0.3f
        binding.chipInsect.alpha  = if (sources.contains("INSECT")) 1f else 0.3f
        binding.chipThermal.alpha = if (sources.contains("THERMAL")) 1f else 0.3f
        binding.chipJitter.alpha  = if (sources.contains("JITTER")) 1f else 0.3f
        binding.chipClient.alpha  = if (sources.contains("CLIENT")) 1f else 0.3f
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.btnConfirm.isEnabled = true
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnConfirm.isEnabled = false
    }
}
