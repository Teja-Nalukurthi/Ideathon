package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.nidhi.app.databinding.ActivitySuccessBinding

class SuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val refNum        = intent.getStringExtra("referenceNumber") ?: "--"
        val recipient     = intent.getStringExtra("recipient") ?: "--"
        val amount        = intent.getStringExtra("formattedAmount") ?: "--"
        val seedHex       = intent.getStringExtra("seedHex") ?: "--"
        val responseMs    = intent.getLongExtra("responseTimeMs", 0L)
        val sources       = intent.getStringArrayExtra("sourcesActive") ?: emptyArray()

        binding.tvRefNumber.text   = refNum
        binding.tvRecipient.text   = recipient
        binding.tvAmount.text      = amount
        binding.tvResponseTime.text = "${responseMs}ms"
        binding.tvSeedHex.text     = if (seedHex.length > 16) "${seedHex.take(16)}…" else seedHex
        binding.tvSourceCount.text = "${sources.size}/5 sources"

        val allActive = sources.contains("INSECT")
        binding.tvEntropyBadge.text = if (allActive) "5-SOURCE ENTROPY" else "ENTROPY SECURED"

        binding.btnNewTx.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }
}
