package com.nidhi.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.view.Menu
import android.view.MenuItem
import android.view.View
import java.util.Base64
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task

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
        // balance fetched in onResume so it refreshes after returning from MainActivity
    }

    override fun onResume() {
        super.onResume()
        if (SessionManager.isLoggedIn(this)) fetchAccountInfo()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.add(0, R.id.action_register_tee, 1, "Register Device").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_register_tee -> {
                setupTEE()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
                    // if device unregistered, remind user to set up TEE
                    if (info.deviceId.isNullOrBlank()) {
                        Snackbar.make(binding.root,
                            "Device not registered. Registering now...", Snackbar.LENGTH_LONG)
                            .setAction("Cancel") { /* user can dismiss */ }
                            .show()
                        // kick off registration in background automatically
                        setupTEE()
                    }
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

    private fun setupTEE() {
        val session = SessionManager.get(this) ?: return
        val phone = session.phone
        val deviceId = session.accountNumber
        val pubB64 = getOrCreateKeyPair()?.let { keypair ->
            Base64.getEncoder().encodeToString(keypair.public.encoded)
        } ?: run {
            Snackbar.make(binding.root, "Cannot access keystore", Snackbar.LENGTH_LONG).show()
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task: com.google.android.gms.tasks.Task<String> ->
            val fcm: String? = if (task.isSuccessful) task.result else null
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val body = mutableMapOf(
                        "phone" to phone,
                        "deviceId" to deviceId,
                        "publicKeyBase64" to pubB64
                    )
                    if (!fcm.isNullOrBlank()) body["fcmToken"] = fcm
                    NidhiClient.api(this@HomeActivity).registerDevice(body)
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Device registered successfully", Snackbar.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Registration failed: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun getOrCreateKeyPair(): java.security.KeyPair? {
        try {
            val alias = "nidhi_tee"
            val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(alias)) {
                val kpg = java.security.KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY
                ).setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                    .build()
                kpg.initialize(spec)
                kpg.generateKeyPair()
            }
            val publicKey = ks.getCertificate(alias).publicKey
            val privateKey = ks.getKey(alias, null) as java.security.PrivateKey
            return java.security.KeyPair(publicKey, privateKey)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
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
