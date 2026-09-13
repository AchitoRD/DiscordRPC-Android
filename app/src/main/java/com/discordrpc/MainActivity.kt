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
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDot: View
    private lateinit var tvCurrentApp: TextView
    private lateinit var tvAppName: TextView
    private lateinit var etToken: EditText
    private lateinit var etAppId: EditText
    private lateinit var tvTokenHint: TextView
    private lateinit var seekInterval: SeekBar
    private lateinit var tvInterval: TextView
    private lateinit var tvPermStatus: View
    private lateinit var tvPermLabel: TextView
    private lateinit var btnPermissions: Button
    private lateinit var btnStartStop: Button
    private lateinit var tvLog: TextView
    private lateinit var cardStatus: CardView
    private lateinit var cardToken: CardView

    private val handler = Handler(Looper.getMainLooper())
    private val intervals = longArrayOf(2000, 3000, 5000, 10000, 30000, 60000)
    private val intervalLabels = arrayOf("2s", "3s", "5s", "10s", "30s", "60s")

    private val eventHandler: (EventBus.Event) -> Unit = { event ->
        handler.post {
            when (event) {
                is EventBus.Event.Status -> tvStatus.text = event.message
                is EventBus.Event.Connected -> {
                    tvStatus.text = event.username
                    tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                    cardStatus.setCardBackgroundColor(getColor(R.color.status_online))
                }
                is EventBus.Event.Disconnected -> {
                    tvStatus.text = "Desconectado"
                    tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                    cardStatus.setCardBackgroundColor(getColor(R.color.bg_card))
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
        etAppId = findViewById(R.id.etAppId)
        tvTokenHint = findViewById(R.id.tvTokenHint)
        seekInterval = findViewById(R.id.seekInterval)
        tvInterval = findViewById(R.id.tvInterval)
        tvPermStatus = findViewById(R.id.tvPermStatus)
        tvPermLabel = findViewById(R.id.tvPermLabel)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvLog = findViewById(R.id.tvLog)
        cardStatus = findViewById(R.id.cardStatus)
        cardToken = findViewById(R.id.cardToken)
    }

    private fun loadSavedData() {
        val savedToken = PrefsManager.getToken(this)
        if (savedToken.isNotEmpty()) {
            etToken.setText(savedToken)
            tvTokenHint.text = "Token guardado"
            tvTokenHint.setTextColor(getColor(R.color.discord_green))
        }
        val savedAppId = PrefsManager.getAppId(this)
        if (savedAppId.isNotEmpty()) etAppId.setText(savedAppId)

        findViewById<Button>(R.id.btnShowToken).setOnClickListener {
            val showing = etToken.inputType != android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etToken.inputType = if (showing) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            else android.text.InputType.TYPE_CLASS_TEXT
            etToken.setSelection(etToken.text.length)
            findViewById<Button>(R.id.btnShowToken).text = if (showing) "Mostrar" else "Ocultar"
        }

        findViewById<Button>(R.id.btnClearToken).setOnClickListener {
            etToken.setText(""); PrefsManager.saveToken(this, "")
            tvTokenHint.text = ""
        }

        findViewById<Button>(R.id.btnHowToken).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Como obtener tu token")
                .setMessage("""
                    |1. Abre Discord en el navegador
                    |2. Presiona F12
                    |3. Ve a pestaña "Network"
                    |4. Busca request a gateway.discord.gg
                    |5. Headers > Authorization > copia el valor
                """.trimMargin())
                .setPositiveButton("OK", null).show()
        }

        findViewById<Button>(R.id.btnHowAppId).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Como crear tu Application")
                .setMessage("""
                    |1. Ve a https://discord.com/developers/applications
                    |2. Click "New Application" > ponele nombre
                    |3. Copia el "Application ID" (Client ID)
                    |4. Ve a "Rich Presence" > "Art Assets"
                    |5. Sube iconos con nombres como:
                    |   app_whatsapp, app_youtube, app_spotify
                    |   (usa el nombre de la app en minusculas
                    |    con espacios reemplazados por guion bajo)
                    |6. Pega el Application ID aqui
                """.trimMargin())
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
        val appId = etAppId.text.toString().trim()
        if (token.isEmpty()) { Toast.makeText(this, "Pega tu token", Toast.LENGTH_SHORT).show(); return }
        if (appId.isEmpty()) { Toast.makeText(this, "Pega tu Application ID", Toast.LENGTH_SHORT).show(); return }
        if (!hasUsageAccess()) { Toast.makeText(this, "Dale permisos de Usage Access", Toast.LENGTH_SHORT).show(); return }

        PrefsManager.saveToken(this, token)
        PrefsManager.saveAppId(this, appId)
        PrefsManager.setServiceRunning(this, true)

        val svc = Intent(this, AppDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        updateUI(true)
        appendLog("Iniciando...")
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
            btnStartStop.setBackgroundColor(getColor(R.color.discord_red))
            tvStatus.text = "Conectando..."
            tvStatusDot.setBackgroundResource(R.drawable.dot_yellow)
            cardStatus.setCardBackgroundColor(getColor(R.color.status_connecting))
        } else {
            btnStartStop.text = "INICIAR"
            btnStartStop.setBackgroundColor(getColor(R.color.discord_green))
            tvStatus.text = "Inactivo"
            tvStatusDot.setBackgroundResource(R.drawable.dot_gray)
            tvCurrentApp.text = "Esperando..."
            tvAppName.visibility = View.GONE
            cardStatus.setCardBackgroundColor(getColor(R.color.bg_card))
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
            tvPermLabel.text = "Usage Access: OK"
            tvPermLabel.setTextColor(getColor(R.color.discord_green))
            btnPermissions.alpha = 0.5f; btnPermissions.isEnabled = false
        } else {
            tvPermStatus.setBackgroundResource(R.drawable.dot_red)
            tvPermLabel.text = "Usage Access: Requerido"
            tvPermLabel.setTextColor(getColor(R.color.discord_red))
            btnPermissions.alpha = 1.0f; btnPermissions.isEnabled = true
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("$time $message\n")
        val scroll = (tvLog.layout?.let { it.getLineTop(tvLog.lineCount) - tvLog.height } ?: 0)
        if (scroll > 0) tvLog.scrollTo(0, scroll)
    }
}
