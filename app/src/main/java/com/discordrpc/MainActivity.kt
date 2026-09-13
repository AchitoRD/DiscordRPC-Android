package com.discordrpc

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDot: View
    private lateinit var tvCurrentApp: TextView
    private lateinit var tvAppName: TextView
    private lateinit var etToken: EditText
    private lateinit var tvTokenHint: TextView
    private lateinit var seekInterval: SeekBar
    private lateinit var tvInterval: TextView
    private lateinit var tvPermStatus: View
    private lateinit var tvPermLabel: TextView
    private lateinit var btnPermissions: Button
    private lateinit var btnStartStop: Button
    private lateinit var tvLog: TextView
    private lateinit var cardStatus: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val intervals = longArrayOf(2000, 3000, 5000, 10000, 30000, 60000)
    private val intervalLabels = arrayOf("2s", "3s", "5s", "10s", "30s", "60s")

    companion object {
        private const val DEFAULT_APP_ID = "1548810919365054575"
    }

    private val eventHandler: (EventBus.Event) -> Unit = { event ->
        handler.post {
            when (event) {
                is EventBus.Event.Status -> tvStatus.text = event.message
                is EventBus.Event.Connected -> {
                    tvStatus.text = event.username
                    tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                }
                is EventBus.Event.Disconnected -> {
                    tvStatus.text = "Desconectado"
                    tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                    updateUI(false)
                }
                is EventBus.Event.AppDetected -> {
                    tvCurrentApp.text = event.name
                    tvAppName.text = event.packageName
                    tvAppName.visibility = View.VISIBLE
                }
                is EventBus.Event.Error -> {
                    tvStatus.text = event.message
                    tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                }
                is EventBus.Event.Log -> appendLog(event.line)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        loadSavedData()
        setupInterval()
        setupButtons()
        checkPermissions()
        updateUI(PrefsManager.isServiceRunning(this))
    }

    override fun onResume() {
        super.onResume()
        EventBus.onEvent = eventHandler
        checkPermissions()
    }

    override fun onPause() { super.onPause(); EventBus.onEvent = null }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvStatusDot = findViewById(R.id.tvStatusDot)
        tvCurrentApp = findViewById(R.id.tvCurrentApp)
        tvAppName = findViewById(R.id.tvAppName)
        etToken = findViewById(R.id.etToken)
        tvTokenHint = findViewById(R.id.tvTokenHint)
        seekInterval = findViewById(R.id.seekInterval)
        tvInterval = findViewById(R.id.tvInterval)
        tvPermStatus = findViewById(R.id.tvPermStatus)
        tvPermLabel = findViewById(R.id.tvPermLabel)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvLog = findViewById(R.id.tvLog)
        cardStatus = findViewById(R.id.cardStatus)
    }

    private fun loadSavedData() {
        val savedToken = PrefsManager.getToken(this)
        if (savedToken.isNotEmpty()) {
            etToken.setText(savedToken)
            tvTokenHint.text = "Token guardado"
            tvTokenHint.setTextColor(getColor(R.color.ios_green))
        }

        // Application ID pre-cargado
        if (PrefsManager.getAppId(this).isEmpty()) {
            PrefsManager.saveAppId(this, DEFAULT_APP_ID)
        }

        findViewById<Button>(R.id.btnShowToken).setOnClickListener {
            val isPassword = etToken.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
            etToken.inputType = if (isPassword) android.text.InputType.TYPE_CLASS_TEXT
            else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etToken.setSelection(etToken.text.length)
            findViewById<Button>(R.id.btnShowToken).text = if (isPassword) "Ocultar" else "Mostrar"
        }

        findViewById<Button>(R.id.btnClearToken).setOnClickListener {
            etToken.setText(""); PrefsManager.saveToken(this, "")
            tvTokenHint.text = ""
        }

        findViewById<Button>(R.id.btnHowToken).setOnClickListener {
            android.app.AlertDialog.Builder(this, R.style.iOSDialog)
                .setTitle("Obtener Token")
                .setMessage("1. Abre discord.com en el navegador\n2. Presiona F12\n3. Pestaña Network\n4. Busca gateway.discord.gg\n5. Headers > Authorization\n6. Copia el valor completo")
                .setPositiveButton("OK", null).show()
        }

        findViewById<Button>(R.id.btnHowAsset).setOnClickListener {
            android.app.AlertDialog.Builder(this, R.style.iOSDialog)
                .setTitle("Subir tu icono (1 vez)")
                .setMessage("1. discord.com/developers/applications\n2. Selecciona tu Application\n3. Rich Presence > Art Assets\n4. Add Image(s)\n5. Nombre: default\n6. Sube UN icono (el de tu Application)\n7. Save\n\nListo - ese icono se usara para todas las apps")
                .setPositiveButton("OK", null).show()
        }
    }

    private fun setupInterval() {
        val saved = PrefsManager.getInterval(this)
        val idx = intervals.indexOf(saved).coerceAtLeast(1)
        seekInterval.progress = idx
        tvInterval.text = intervalLabels[idx]
        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { tvInterval.text = intervalLabels[p] }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                PrefsManager.setInterval(this@MainActivity, intervals[s?.progress ?: 1])
            }
        })
    }

    private fun setupButtons() {
        btnPermissions.setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        btnStartStop.setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) stopRpc() else startRpc()
        }
    }

    private fun startRpc() {
        val token = etToken.text.toString().trim()
        if (token.isEmpty()) { Toast.makeText(this, "Pega tu token", Toast.LENGTH_SHORT).show(); return }
        if (!hasUsageAccess()) { Toast.makeText(this, "Otorga Usage Access", Toast.LENGTH_SHORT).show(); return }

        PrefsManager.saveToken(this, token)
        PrefsManager.saveAppId(this, DEFAULT_APP_ID)
        PrefsManager.setServiceRunning(this, true)

        val svc = Intent(this, AppDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        updateUI(true)
    }

    private fun stopRpc() {
        val svc = Intent(this, AppDetectionService::class.java).apply { action = "STOP" }
        startService(svc)
        PrefsManager.setServiceRunning(this, false)
        updateUI(false)
    }

    private fun updateUI(running: Boolean) {
        if (running) {
            btnStartStop.text = "DETENER"
            btnStartStop.setBackgroundColor(getColor(R.color.ios_red))
            tvStatus.text = "Conectando..."
            tvStatusDot.setBackgroundResource(R.drawable.dot_yellow)
        } else {
            btnStartStop.text = "INICIAR"
            btnStartStop.setBackgroundColor(getColor(R.color.ios_blue))
            tvStatus.text = "Inactivo"
            tvStatusDot.setBackgroundResource(R.drawable.dot_gray)
            tvCurrentApp.text = ""
            tvAppName.visibility = View.GONE
        }
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION") appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkPermissions() {
        if (hasUsageAccess()) {
            tvPermStatus.setBackgroundResource(R.drawable.dot_green)
            tvPermLabel.text = "Usage Access"
            tvPermLabel.setTextColor(getColor(R.color.ios_green))
            btnPermissions.isEnabled = false
        } else {
            tvPermStatus.setBackgroundResource(R.drawable.dot_red)
            tvPermLabel.text = "Usage Access (requerido)"
            tvPermLabel.setTextColor(getColor(R.color.ios_red))
            btnPermissions.isEnabled = true
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("$time $message\n")
        val scroll = (tvLog.layout?.let { it.getLineTop(tvLog.lineCount) - tvLog.height } ?: 0)
        if (scroll > 0) tvLog.scrollTo(0, scroll)
    }
}
