package com.nidhi.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nidhi.app.databinding.ActivityTransactionHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val session = SessionManager.get(this)
        if (session == null) { finish(); return }

        loadTransactions(session.accountNumber)
    }

    private fun loadTransactions(accountNumber: String) {
        binding.pbLoading.visibility = View.VISIBLE
        binding.tvEmpty.visibility   = View.GONE
        binding.scrollTransactions.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val txList = NidhiClient.api(this@TransactionHistoryActivity)
                    .getTransactions(accountNumber)

                binding.pbLoading.visibility = View.GONE

                if (txList.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                binding.scrollTransactions.visibility = View.VISIBLE
                binding.llTransactions.removeAllViews()

                txList.forEach { tx ->
                    val isCredit = tx.toAccount == accountNumber
                    addTransactionRow(tx, isCredit, accountNumber)
                }
            } catch (e: Exception) {
                binding.pbLoading.visibility = View.GONE
                binding.tvEmpty.text = "Failed to load transactions.\nCheck your connection."
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun addTransactionRow(
        tx: TransactionItem,
        isCredit: Boolean,
        myAccount: String
    ) {
        // Card container
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(10) }
            setCardBackgroundColor(getColor(R.color.surface_card))
            radius = dp(14).toFloat()
            cardElevation = 0f
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        // Row 1: counterparty name + amount
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val counterpartyName = if (isCredit)
                                    tx.fromName?.ifBlank { null } ?: tx.fromAccount ?: "Unknown"
                               else
                                    tx.toName?.ifBlank   { null } ?: tx.toAccount   ?: "Unknown"
        val directionPrefix  = if (isCredit) "From: " else "To: "

        val tvName = TextView(this).apply {
            text = "$directionPrefix$counterpartyName"
            textSize = 15f
            setTextColor(getColor(R.color.on_surface))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val amountRupees = tx.amountPaise / 100.0
        val tvAmount = TextView(this).apply {
            val sign = if (isCredit) "+" else "−"
            text = "$sign ₹%,.2f".format(amountRupees)
            textSize = 15f
            setTextColor(
                if (isCredit) getColor(R.color.accent)
                else Color.parseColor("#FF5252")
            )
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }

        row1.addView(tvName)
        row1.addView(tvAmount)
        inner.addView(row1)

        // Row 2: reference + date
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(4) }
        }

        val formattedDate = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse((tx.createdAt ?: "").take(19))
            SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(date!!)
        } catch (e: Exception) { tx.createdAt ?: "" }

        val tvRef = TextView(this).apply {
            text = (tx.referenceId?.take(12) ?: "—") + "…"
            textSize = 11f
            setTextColor(getColor(R.color.on_surface_dim))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvDate = TextView(this).apply {
            text = formattedDate
            textSize = 11f
            setTextColor(getColor(R.color.on_surface_dim))
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }

        row2.addView(tvRef)
        row2.addView(tvDate)
        inner.addView(row2)

        // Status badge (if not SUCCESS)
        if (tx.status != "SUCCESS") {
            val tvStatus = TextView(this).apply {
                text = tx.status
                textSize = 11f
                setTextColor(Color.parseColor("#FF5252"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2) }
            }
            inner.addView(tvStatus)
        }

        // Failure reason
        if (!tx.failureReason.isNullOrBlank()) {
            val tvFail = TextView(this).apply {
                text = "Reason: ${tx.failureReason}"
                textSize = 11f
                setTextColor(Color.parseColor("#FF5252"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = dp(2) }
            }
            inner.addView(tvFail)
        }

        card.addView(inner)
        binding.llTransactions.addView(card)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
