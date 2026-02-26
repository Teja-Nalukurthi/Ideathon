package com.nidhi.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.nidhi.app.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VOICE_MODE  = "voice_mode"
        const val EXTRA_INPUT_TEXT  = "input_text"
    }

    private lateinit var binding: ActivityHomeBinding

    private val languages     = listOf("en","hi","te","ta","kn","ml","mr","bn")
    private val languageNames = listOf("English","Hindi","Telugu","Tamil","Kannada","Malayalam","Marathi","Bengali")

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?: return@registerForActivityResult
        // Pre-fill and hand off to MainActivity with the recognised text
        openMainActivity(inputText = text)
    }

    /** ZXing QR scanner — QR encodes raw account number (NIDHI...) */
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@registerForActivityResult
        // Support both bare account number and legacy nidhi: prefix
        val accountNumber = if (raw.startsWith("nidhi:")) raw.removePrefix("nidhi:").trim() else raw.trim()
        if (accountNumber.isBlank()) return@registerForActivityResult
        startActivity(Intent(this, SendManualActivity::class.java).apply {
            putExtra("accountNumber", accountNumber)
            // Don't pass a name — SendManualActivity will use the account number directly
        })
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: redirect to login if no session
        val session = SessionManager.get(this)
        if (session == null) {
            goToLogin()
            return
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGreeting(session)
        setupLanguageDropdown(session)
        setupListeners()
        checkServerConnectivity()
        scheduleTransactionPoller()
        requestNotificationPermission()
        // balance fetched in onResume so it refreshes after returning from MainActivity
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this)) {
            fetchAccountInfo()
            checkForNewTransactions()
            // Connect SSE for real-time push from bank server
            val acct = SessionManager.get(this)?.accountNumber
            if (!acct.isNullOrBlank()) {
                NidhiSseClient.connect(this, acct) { message ->
                    runOnUiThread {
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                            .setAction("View") {
                                startActivity(Intent(this, TransactionHistoryActivity::class.java))
                            }.show()
                        // Refresh balance to show updated amount
                        fetchAccountInfo()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        NidhiSseClient.disconnect()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }


    // ─── Setup helpers ────────────────────────────────────────────────────────

    private fun setupGreeting(session: SessionManager.UserSession) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning,"
            hour < 17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
        binding.tvGreeting.text = greeting
        binding.tvUserName.text = session.fullName.ifBlank { "NIDHI User" }
    }

    private fun setupLanguageDropdown(session: SessionManager.UserSession) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageNames)
        binding.actvLanguage.setAdapter(adapter)
        val idx = languages.indexOf(session.languageCode).coerceAtLeast(0)
        binding.actvLanguage.setText(languageNames[idx], false)

        binding.actvLanguage.setOnItemClickListener { _, _, position, _ ->
            val newLang = languages[position]
            val updated = session.copy(languageCode = newLang)
            SessionManager.save(this, updated)
        }
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener { confirmLogout() }

        binding.btnRefreshBalance.setOnClickListener { fetchAccountInfo() }

        binding.btnCopyAcct.setOnClickListener {
            val acct = SessionManager.get(this)?.accountNumber ?: return@setOnClickListener
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("account", acct))
            Snackbar.make(binding.root, "Account number copied", Snackbar.LENGTH_SHORT).show()
        }

        binding.cardSendMoney.setOnClickListener {
            openMainActivity()
        }

        binding.cardHistory.setOnClickListener {
            startActivity(Intent(this, TransactionHistoryActivity::class.java))
        }

        binding.cardProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.cardDeposit.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cash Deposit")
                .setMessage("To deposit cash, please visit your nearest Nidhi Bank branch with a valid ID.\n\nOur branches are open Monday–Saturday, 9 AM – 5 PM.")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.cardMic.setOnClickListener { startVoiceInput() }

        binding.cardContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        binding.cardReceive.setOnClickListener {
            startActivity(Intent(this, ReceiveMoneyActivity::class.java))
        }

        binding.cardScanQr.setOnClickListener {
            qrScanLauncher.launch(
                ScanOptions()
                    .setPrompt("Scan a Nidhi account QR code")
                    .setOrientationLocked(false)
                    .setBeepEnabled(true)
            )
        }

        binding.cardSendAcct.setOnClickListener {
            startActivity(Intent(this, SendManualActivity::class.java))
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text?.toString()?.trim()
            if (text.isNullOrEmpty()) {
                Snackbar.make(binding.root, "Please type a command first", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openMainActivity(inputText = text)
        }

        binding.btnChangeServer.setOnClickListener {
            showServerUrlDialog()
        }
    }

    // ─── Account info + balance ───────────────────────────────────────────────

    private fun fetchAccountInfo() {
        val session = SessionManager.get(this) ?: return
        val acct    = session.accountNumber
        if (acct.isBlank()) {
            binding.tvBalance.text       = "₹ unavailable"
            binding.tvAccountNumber.text = "No account linked"
            return
        }

        binding.balanceProgress.visibility = View.VISIBLE
        binding.tvBalance.text             = "₹ ——"
        binding.tvAccountNumber.text       = acct

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = NidhiClient.api(this@HomeActivity).getAccountInfo(acct)
                SessionManager.updateBalance(this@HomeActivity, info.balancePaise)
                withContext(Dispatchers.Main) {
                    binding.balanceProgress.visibility = View.GONE
                    binding.tvBalance.text         = formatRupees(info.balancePaise)
                    binding.tvAccountNumber.text   = info.accountNumber ?: session.accountNumber
                    binding.tvUserName.text        = info.fullName?.ifBlank { session.fullName } ?: session.fullName
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.balanceProgress.visibility = View.GONE
                    val cached = session.balancePaise
                    binding.tvBalance.text = if (cached > 0) formatRupees(cached) + " (cached)"
                                            else "₹ unavailable"
                    Snackbar.make(binding.root, "Could not refresh balance", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatRupees(paise: Long): String {
        val rupees = paise / 100.0
        return "₹ %,.2f".format(rupees)
    }

    // ─── Voice input ──────────────────────────────────────────────────────────

    private fun startVoiceInput() {
        val session  = SessionManager.get(this)
        val langCode = session?.languageCode ?: "en"
        val langIdx  = languageNames.indexOf(binding.actvLanguage.text.toString()).coerceAtLeast(0)
        val locale   = when (languages[langIdx]) {
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

    // ─── Navigation helpers ───────────────────────────────────────────────────

    private fun openMainActivity(inputText: String? = null, voiceMode: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java)
        if (inputText != null) intent.putExtra(EXTRA_INPUT_TEXT, inputText)
        if (voiceMode)         intent.putExtra(EXTRA_VOICE_MODE, true)
        startActivity(intent)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                SessionManager.clear(this)
                goToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Server status ────────────────────────────────────────────────────────

    /** Checks for new credits since last open and shows Snackbar + system notification. */
    private fun checkForNewTransactions() {
        val session = SessionManager.get(this) ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val txs = NidhiClient.api(this@HomeActivity).getTransactions(session.accountNumber)
                if (txs.isEmpty()) return@launch
                val prefs     = getSharedPreferences("nidhi_poller", Context.MODE_PRIVATE)
                val lastRef   = prefs.getString("last_seen_ref", null)
                val latestRef = txs.first().referenceId ?: return@launch
                if (lastRef == null) {
                    prefs.edit().putString("last_seen_ref", latestRef).apply()
                    return@launch
                }
                if (lastRef == latestRef) return@launch
                val newCredits = txs
                    .takeWhile { it.referenceId != lastRef }
                    .filter { it.status == "SUCCESS" }
                    .filter { it.toAccount == session.accountNumber || it.txType == "ADMIN_CREDIT" }
                prefs.edit().putString("last_seen_ref", latestRef).apply()
                if (newCredits.isEmpty()) return@launch
                val total = newCredits.sumOf { it.amountPaise }
                val body  = if (newCredits.size == 1)
                    "\u20B9${total / 100} credited to your account"
                else
                    "${newCredits.size} new credits totalling \u20B9${total / 100}"
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "\uD83D\uDCB0 $body", Snackbar.LENGTH_LONG)
                        .setAction("View") { startActivity(Intent(this@HomeActivity, TransactionHistoryActivity::class.java)) }
                        .show()
                }
            } catch (_: Exception) { /* best-effort */ }
        }
    }

    private fun scheduleTransactionPoller() {        val request = PeriodicWorkRequestBuilder<TransactionPollerWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TransactionPollerWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun checkServerConnectivity() {
        val serverUrl = ServerConfig.getUrl(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val connected = try {
                val conn = URL("${serverUrl}api/health").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout    = 4000
                conn.requestMethod  = "GET"
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) { false }
            withContext(Dispatchers.Main) {
                binding.tvServerStatus.text = if (connected) "●  Connected to Nidhi Bank"
                                              else           "●  Server unreachable (demo network only)"
                binding.tvServerStatus.setTextColor(
                    getColor(if (connected) R.color.accent else R.color.error)
                )
            }
        }
    }

    private fun showServerUrlDialog() {
        val input = android.widget.EditText(this).apply {
            setText(ServerConfig.getUrl(this@HomeActivity))
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Server URL")
            .setMessage("Enter the base URL:\ne.g. http://10.58.19.75:8081/")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim().let {
                    if (it.endsWith("/")) it else "$it/"
                }
                ServerConfig.saveUrl(this, url)
                Snackbar.make(binding.root, "URL updated", Snackbar.LENGTH_SHORT).show()
                checkServerConnectivity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
