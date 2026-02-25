package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val languages     = listOf("en","hi","te","ta","kn","ml","mr","bn")
    private val languageNames = listOf("English","Hindi","Telugu","Tamil","Kannada","Malayalam","Marathi","Bengali")

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@registerForActivityResult
        binding.etInput.setText(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Prompt for server URL on first launch
        if (!ServerConfig.isConfigured(this)) {
            showServerUrlDialog(firstTime = true)
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageNames)
        binding.actvLanguage.setAdapter(adapter)
        binding.actvLanguage.setText(languageNames[0], false)

        binding.btnMic.setOnClickListener { startVoiceInput() }
        binding.btnSend.setOnClickListener { submitTransaction() }

        lifecycleScope.launch {
            viewModel.initiateState.collect { state ->
                when (state) {
                    is InitiateState.Idle    -> showIdle()
                    is InitiateState.Loading -> showLoading()
                    is InitiateState.Ready   -> {
                        showIdle()
                        val resp = state.response
                        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                        startActivity(Intent(this@MainActivity, ConfirmActivity::class.java).apply {
                            putExtra("txId",              resp.txId)
                            putExtra("recipient",         resp.recipient)
                            putExtra("formattedAmount",   resp.formattedAmount)
                            putExtra("confirmationText",  resp.confirmationText)
                            putExtra("signingPayload",    resp.signingPayload)
                            putExtra("privateKeyBase64",  resp.privateKeyBase64)
                            putExtra("deviceId",          deviceId)
                            putExtra("nonce",             viewModel.lastNonce)
                            putExtra("expiresAtMs",       resp.expiresAtMs)
                            putExtra("sourcesActive",     resp.sourcesActive.toTypedArray())
                        })
                        viewModel.resetInitiate()
                    }
                    is InitiateState.Error -> {
                        showIdle()
                        Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                            .setBackgroundTint(getColor(R.color.error)).show()
                        viewModel.resetInitiate()
                    }
                }
            }
        }
    }

    private fun startVoiceInput() {
        val idx = languageNames.indexOf(binding.actvLanguage.text.toString()).coerceAtLeast(0)
        val locale = when (languages[idx]) {
            "hi" -> "hi-IN"; "te" -> "te-IN"; "ta" -> "ta-IN"
            "kn" -> "kn-IN"; "ml" -> "ml-IN"; "mr" -> "mr-IN"; "bn" -> "bn-IN"
            else -> "en-IN"
        }
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say: Send 500 rupees to Ramu")
        })
    }

    private fun submitTransaction() {
        val text = binding.etInput.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            Snackbar.make(binding.root, "Please enter or speak a command", Snackbar.LENGTH_SHORT).show()
            return
        }
        val idx = languageNames.indexOf(binding.actvLanguage.text.toString()).coerceAtLeast(0)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        viewModel.initiate(text, languages[idx], deviceId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_server_url) {
            showServerUrlDialog(firstTime = false)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showServerUrlDialog(firstTime: Boolean) {
        val current = ServerConfig.getUrl(this)
        val input = EditText(this).apply {
            setText(current)
            hint = "https://abc123.ngrok.io"
            setSingleLine()
        }
        val title = if (firstTime) "Enter Server URL" else "Change Server URL"
        val msg   = if (firstTime)
            "Paste your ngrok URL (e.g. https://abc123.ngrok.io)"
        else
            "Current: $current"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    ServerConfig.saveUrl(this, url)
                    Snackbar.make(binding.root, "Server URL saved: $url", Snackbar.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(if (firstTime) "Use Local" else "Cancel", null)
            .show()
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.visibility = View.GONE
        binding.btnSend.isEnabled = true
        binding.btnMic.isEnabled = true
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.msg_processing)
        binding.btnSend.isEnabled = false
        binding.btnMic.isEnabled = false
    }
}
