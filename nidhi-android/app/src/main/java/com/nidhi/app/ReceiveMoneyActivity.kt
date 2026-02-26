package com.nidhi.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.nidhi.app.databinding.ActivityReceiveMoneyBinding
import java.io.File
import java.io.FileOutputStream

class ReceiveMoneyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiveMoneyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveMoneyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Receive Money"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val session = SessionManager.get(this) ?: run { finish(); return }
        val accountNumber = session.accountNumber

        binding.tvReceiveName.text    = session.fullName
        binding.tvReceiveAcct.text    = accountNumber

        try {
            val encoder = BarcodeEncoder()
            val bitmap  = encoder.encodeBitmap("nidhi:$accountNumber", BarcodeFormat.QR_CODE, 600, 600)
            binding.ivQrCode.setImageBitmap(bitmap)

            binding.btnShare.setOnClickListener { shareQr(bitmap, accountNumber) }
        } catch (e: Exception) {
            binding.tvQrError.visibility = View.VISIBLE
            binding.tvQrError.text = "QR generation failed: ${e.message}"
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun shareQr(bitmap: Bitmap, accountNumber: String) {
        try {
            val file = File(cacheDir, "nidhi_qr.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Pay me on Nidhi Bank — account: $accountNumber")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share QR Code"))
        } catch (e: Exception) {
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "Share failed: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        }
    }
}
