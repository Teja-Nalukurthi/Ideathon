package com.nidhi.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.nidhi.app.databinding.ActivityContactsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val CONTACTS_PERMISSION_CODE = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Pay Contacts"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CONTACTS), CONTACTS_PERMISSION_CODE
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, perms, grants)
        if (code == CONTACTS_PERMISSION_CODE && grants.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadContacts()
        } else {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = "Contacts permission denied.\nGrant it in Settings to use this feature."
        }
    }

    private fun loadContacts() {
        binding.progressContacts.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. Read phone contacts
            val phoneMap = mutableMapOf<String, String>() // normalized phone → display name
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx   = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name  = it.getString(nameIdx) ?: continue
                    val phone = normalizePhone(it.getString(numberIdx) ?: continue)
                    if (phone.length == 10) phoneMap[phone] = name
                }
            }

            if (phoneMap.isEmpty()) {
                withContext(Dispatchers.Main) {
                    binding.progressContacts.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "No contacts found on this device."
                }
                return@launch
            }

            // 2. Find which contacts have Nidhi accounts
            val nidhiContacts = try {
                NidhiClient.api(this@ContactsActivity).lookupBatch(phoneMap.keys.toList())
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressContacts.visibility = View.GONE
                    Snackbar.make(binding.root, "Could not reach server: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.progressContacts.visibility = View.GONE
                if (nidhiContacts.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "None of your contacts are on Nidhi Bank yet."
                } else {
                    binding.rvContacts.visibility = View.VISIBLE
                    binding.rvContacts.layoutManager = LinearLayoutManager(this@ContactsActivity)
                    binding.rvContacts.adapter = ContactAdapter(nidhiContacts) { contact ->
                        startActivity(Intent(this@ContactsActivity, SendManualActivity::class.java).apply {
                            putExtra("accountNumber", contact.accountNumber)
                            putExtra("recipientName", contact.fullName)
                        })
                    }
                }
            }
        }
    }

    private fun normalizePhone(phone: String): String {
        var digits = phone.filter { it.isDigit() }
        if (digits.length == 12 && digits.startsWith("91")) digits = digits.drop(2)
        if (digits.length == 11 && digits.startsWith("0"))  digits = digits.drop(1)
        return digits
    }
}

private class ContactAdapter(
    private val items: List<NidhiContact>,
    private val onPay: (NidhiContact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val tvName:    TextView = root.findViewById(R.id.tvContactName)
        val tvAcct:    TextView = root.findViewById(R.id.tvContactAcct)
        val tvInitial: TextView = root.findViewById(R.id.tvContactInitial)
        val btnPay:    com.google.android.material.button.MaterialButton = root.findViewById(R.id.btnPayContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.tvName.text    = c.fullName
        holder.tvAcct.text    = c.accountNumber
        holder.tvInitial.text = c.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "N"
        holder.btnPay.setOnClickListener { onPay(c) }
        holder.root.setOnClickListener  { onPay(c) }
    }
}
