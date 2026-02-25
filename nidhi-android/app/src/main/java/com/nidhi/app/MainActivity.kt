package com.nidhi.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private val languages     = listOf("en","hi","te","ta","kn","ml","mr","bn")
    private val languageNames = listOf("English","Hindi","Telugu","Tamil","Kannada","Malayalam","Marathi","Bengali")

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@registerForActivityResult
        // if the recognized text looks like a command, handle locally instead of treating it as a transaction
        if (!handleVoiceCommand(text)) {
            binding.etInput.setText(text)
        }
    }

    /**
     * Returns true if the text was consumed as a command.
     */
    private fun handleVoiceCommand(raw: String): Boolean {
        val lower = raw.lowercase()
        val acct = SessionManager.get(this)?.accountNumber ?: return false
        when {
            lower.contains("balance") -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val info = NidhiClient.api(this@MainActivity).getAccountInfo(acct)
                        val rupee = formatRupees(info.balancePaise)
                        withContext(Dispatchers.Main) {
                            speakConfirmation("Your account balance is $rupee")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            speakConfirmation("Unable to fetch balance. Check your connection.")
                        }
                    }
                }
                return true
            }
            lower.contains("transaction") || lower.contains("history") -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val txs = NidhiClient.api(this@MainActivity).getTransactions(acct)
                        val summary = if (txs.isEmpty()) {
                            "You have no recent transactions."
                        } else {
                            txs.take(3).map { tx ->
                                val amt = formatRupees(tx.amountPaise)
                                if (tx.toAccount == acct) {
                                    "received $amt from ${tx.fromName ?: tx.fromAccount ?: "someone"}"
                                } else {
                                    "sent $amt to ${tx.toName ?: tx.toAccount ?: "someone"}"
                                }
                            }.joinToString("; ")
                        }
                        withContext(Dispatchers.Main) {
                            speakConfirmation("Here are your latest transactions: $summary")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            speakConfirmation("Could not load transactions. Try again later.")
                        }
                    }
                }
                return true
            }
            else -> return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Redirect to login if no session
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }

        // Initialize TTS
        tts = TextToSpeech(this) { status -> ttsReady = (status == TextToSpeech.SUCCESS) }

        // Check connectivity to the embedded bank server and update status badge
        checkServerConnectivity()

        // Handle extras from HomeActivity
        val prefilledText = intent.getStringExtra(HomeActivity.EXTRA_INPUT_TEXT)
        if (!prefilledText.isNullOrBlank()) {
            binding.etInput.setText(prefilledText)
            binding.etInput.setSelection(prefilledText.length)
        }
        if (intent.getBooleanExtra(HomeActivity.EXTRA_VOICE_MODE, false)) {
            binding.root.post { startVoiceInput() }
        }

        // Show current URL and wire change button
        binding.tvServerUrl.text = ServerConfig.getUrl(this)
        binding.btnChangeServer.setOnClickListener { showServerUrlDialog(firstTime = false) }

        // Pre-select language from session
        val sessionLang = SessionManager.get(this)?.languageCode ?: "en"
        val langIdx = languages.indexOf(sessionLang).coerceAtLeast(0)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageNames)
        binding.actvLanguage.setAdapter(adapter)
        binding.actvLanguage.setText(languageNames[langIdx], false)

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
                        val deviceId = SessionManager.get(this@MainActivity)?.accountNumber
                            ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                        // Launch confirm screen immediately for minimum latency on challenge timer
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
                        // Speak AFTER launching so the screen transition is not delayed
                        speakConfirmation(resp.confirmationText)
                        viewModel.resetInitiate()
                    }
                    is InitiateState.Error -> {
                        showIdle()
                        val friendly = friendlyInitiateError(state.message)
                        Snackbar.make(binding.root, friendly, Snackbar.LENGTH_LONG)
                            .setBackgroundTint(getColor(R.color.error)).show()
                        viewModel.resetInitiate()
                    }
                }
            }
        }
    }

    private fun speakConfirmation(text: String?) {
        if (!ttsReady || text.isNullOrBlank()) return
        val langCode = SessionManager.get(this)?.languageCode ?: "en"
        val locale = when (langCode) {
            "hi" -> Locale("hi", "IN")
            "te" -> Locale("te", "IN")
            "ta" -> Locale("ta", "IN")
            "kn" -> Locale("kn", "IN")
            "ml" -> Locale("ml", "IN")
            "mr" -> Locale("mr", "IN")
            "bn" -> Locale("bn", "IN")
            else -> Locale.ENGLISH
        }
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tx_confirm")
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                SessionManager.clear(this)
                if (::tts.isInitialized) tts.stop()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun friendlyInitiateError(raw: String): String {
        return when {
            raw.contains("account", ignoreCase = true) &&
                    raw.contains("not found", ignoreCase = true) ->
                "Account not found. Please ensure your phone is registered with the bank."
            raw.contains("parse", ignoreCase = true) ||
                    raw.contains("understand", ignoreCase = true) ->
                "Could not understand the command. Try: \"Send 500 rupees to Ramu\""
            raw.contains("network", ignoreCase = true) ||
                    raw.contains("connect", ignoreCase = true) ->
                "Cannot reach bank server. Check your WiFi connection."
            else -> raw
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    private fun checkServerConnectivity() {
        val serverUrl = ServerConfig.getUrl(this)
        binding.tvServerUrl.text = serverUrl
        lifecycleScope.launch(Dispatchers.IO) {
            val connected = try {
                val conn = URL("${serverUrl}api/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout  = 4000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                binding.tvServerStatus.text =
                    if (connected) "●  Connected to Nidhi Bank Server"
                    else           "●  Bank server unreachable (• demo network only)"
                binding.tvServerStatus.setTextColor(
                    getColor(if (connected) R.color.accent else R.color.error)
                )
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
        val deviceId = SessionManager.get(this)?.accountNumber
            ?: Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        viewModel.initiate(text, languages[idx], deviceId)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_server_url -> { showServerUrlDialog(firstTime = false); true }
            R.id.action_logout     -> { confirmLogout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showServerUrlDialog(firstTime: Boolean) {
        val current = ServerConfig.getUrl(this)
        val input = EditText(this).apply {
            setText(current)
            hint = ServerConfig.BASE_URL
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(if (firstTime) "Bank Server URL" else "Change Server URL")
            .setMessage("Hardcoded to ${ServerConfig.BASE_URL}\nOnly change if running on a different network.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    ServerConfig.saveUrl(this, url)
                    binding.tvServerUrl.text = ServerConfig.getUrl(this)
                    Snackbar.make(binding.root, "Server URL saved: $url", Snackbar.LENGTH_LONG).show()
                    checkServerConnectivity()
                }
            }
            .setNegativeButton("Cancel", null)
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
