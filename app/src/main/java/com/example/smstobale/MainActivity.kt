package com.example.smstobale

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etSourceNumber: EditText
    private lateinit var etWebAppUrl: EditText
    private lateinit var etChatId: EditText
    private lateinit var etSecretKey: EditText

    private lateinit var tabSettings: TextView
    private lateinit var tabHistory: TextView
    private lateinit var scrollSettings: View
    private lateinit var panelHistory: View
    private lateinit var contentFrame: SwipeableFrameLayout

    private lateinit var switchAutoForward: FrameLayout
    private lateinit var switchThumb: View
    private var autoForwardOn = true

    private lateinit var permReceiveSms: LinearLayout
    private lateinit var permReceiveSmsIcon: TextView
    private lateinit var permReadSms: LinearLayout
    private lateinit var permReadSmsIcon: TextView

    private lateinit var listViewLogs: ListView
    private lateinit var dbHelper: LogDbHelper

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { updatePermissionChips() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = LogDbHelper(this)

        etSourceNumber = findViewById(R.id.etSourceNumber)
        etWebAppUrl = findViewById(R.id.etWebAppUrl)
        etChatId = findViewById(R.id.etChatId)
        etSecretKey = findViewById(R.id.etSecretKey)

        tabSettings = findViewById(R.id.tabSettings)
        tabHistory = findViewById(R.id.tabHistory)
        scrollSettings = findViewById(R.id.scrollSettings)
        panelHistory = findViewById(R.id.panelHistory)
        contentFrame = findViewById(R.id.contentFrame)

        switchAutoForward = findViewById(R.id.switchAutoForward)
        switchThumb = findViewById(R.id.switchThumb)
        listViewLogs = findViewById(R.id.listViewLogs)

        permReceiveSms = findViewById(R.id.permReceiveSms)
        permReceiveSmsIcon = findViewById(R.id.permReceiveSmsIcon)
        permReadSms = findViewById(R.id.permReadSms)
        permReadSmsIcon = findViewById(R.id.permReadSmsIcon)

        permReceiveSms.setOnClickListener { openAppSettings() }
        permReadSms.setOnClickListener { openAppSettings() }

        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        etSourceNumber.setText(prefs.getString(Prefs.SOURCE_NUMBER, ""))
        etWebAppUrl.setText(prefs.getString(Prefs.WEBAPP_URL, ""))
        etChatId.setText(prefs.getString(Prefs.CHAT_ID, ""))
        etSecretKey.setText(prefs.getString(Prefs.SECRET_KEY, ""))
        autoForwardOn = prefs.getBoolean(Prefs.AUTO_FORWARD_ENABLED, true)
        applySwitchVisual()

        tabSettings.setOnClickListener { selectTab(settings = true) }
        tabHistory.setOnClickListener { selectTab(settings = false) }

        contentFrame.onSwipeLeft = { if (scrollSettings.visibility == View.VISIBLE) selectTab(settings = false) }
        contentFrame.onSwipeRight = { if (panelHistory.visibility == View.VISIBLE) selectTab(settings = true) }

        switchAutoForward.setOnClickListener {
            autoForwardOn = !autoForwardOn
            applySwitchVisual()
            getSharedPreferences(Prefs.NAME, MODE_PRIVATE).edit()
                .putBoolean(Prefs.AUTO_FORWARD_ENABLED, autoForwardOn)
                .apply()
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            prefs.edit()
                .putString(Prefs.SOURCE_NUMBER, etSourceNumber.text.toString().trim())
                .putString(Prefs.WEBAPP_URL, etWebAppUrl.text.toString().trim())
                .putString(Prefs.CHAT_ID, etChatId.text.toString().trim())
                .putString(Prefs.SECRET_KEY, etSecretKey.text.toString().trim())
                .apply()
            Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            dbHelper.clearLogs()
            loadLogs()
        }

        requestSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionChips()
        if (panelHistory.visibility == View.VISIBLE) {
            loadLogs()
        }
    }

    private fun selectTab(settings: Boolean) {
        scrollSettings.visibility = if (settings) View.VISIBLE else View.GONE
        panelHistory.visibility = if (settings) View.GONE else View.VISIBLE

        tabSettings.setBackgroundResource(if (settings) R.drawable.bg_tab_active else 0)
        tabSettings.setTextColor(ContextCompat.getColor(this, if (settings) R.color.text_primary else R.color.text_secondary))

        tabHistory.setBackgroundResource(if (settings) 0 else R.drawable.bg_tab_active)
        tabHistory.setTextColor(ContextCompat.getColor(this, if (settings) R.color.text_secondary else R.color.text_primary))

        if (!settings) loadLogs()
    }

    private fun applySwitchVisual() {
        switchAutoForward.setBackgroundResource(if (autoForwardOn) R.drawable.bg_switch_on else R.drawable.bg_switch_off)
        val params = switchThumb.layoutParams as FrameLayout.LayoutParams
        params.gravity = if (autoForwardOn) Gravity.END else Gravity.START
        switchThumb.layoutParams = params
    }

    private fun requestSmsPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_SMS)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun updatePermissionChips() {
        val receiveGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        permReceiveSmsIcon.text = if (receiveGranted) "✓" else "✕"
        permReceiveSmsIcon.setTextColor(ContextCompat.getColor(this, if (receiveGranted) R.color.status_success else R.color.status_error))

        permReadSmsIcon.text = if (readGranted) "✓" else "✕"
        permReadSmsIcon.setTextColor(ContextCompat.getColor(this, if (readGranted) R.color.status_success else R.color.status_error))
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun loadLogs() {
        val logs = dbHelper.getAllLogs()
        listViewLogs.adapter = LogAdapter(this, logs)
    }

    private class LogAdapter(
        context: Context,
        private val items: List<LogEntry>
    ) : ArrayAdapter<LogEntry>(context, 0, items) {

        private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.list_item_log, parent, false)

            val entry = items[position]

            val tvStatus = view.findViewById<TextView>(R.id.tvStatus)
            val tvSender = view.findViewById<TextView>(R.id.tvSender)
            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            val tvTime = view.findViewById<TextView>(R.id.tvTime)
            val tvDetail = view.findViewById<TextView>(R.id.tvDetail)

            tvSender.text = "فرستنده: ${entry.sender}"
            tvMessage.text = entry.message
            tvTime.text = dateFormat.format(entry.timestamp)
            tvDetail.text = entry.detail

            val (textColor, bgRes) = when (entry.status) {
                LogDbHelper.STATUS_SUCCESS -> R.color.status_success to R.drawable.bg_pill_success
                LogDbHelper.STATUS_FAILED, LogDbHelper.STATUS_ERROR -> R.color.status_error to R.drawable.bg_pill_error
                else -> R.color.status_ignored to R.drawable.bg_pill_ignored
            }
            tvStatus.text = entry.status
            tvStatus.setTextColor(ContextCompat.getColor(context, textColor))
            tvStatus.setBackgroundResource(bgRes)

            return view
        }
    }
}
