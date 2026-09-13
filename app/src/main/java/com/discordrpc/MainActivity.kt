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
    private lateinit var cardSettings: CardView

    private val handler = Handler(Looper.getMainLooper())
    private val intervals = longArrayOf(5000, 10000, 15000, 30000, 60000, 120000)
    private val intervalLabels = arrayOf("5s", "10s", "15s", "30s", "60s", "120s")

    private val eventHandler: (EventBus.Event) -> Unit = { event ->
        handler.post {
            when (event) {
                is EventBus.Event.Status -> {
                    tvStatus.text = event.message
                }
                is EventBus.Event.Connected -> {
                    tvStatus.text = event.username
                    tvStatusDot.setBackgroundResource(R.drawable.dot_green)
                    cardStatus.setCardBackgroundColor(getColor(R.color.status_online))
                }
                is EventBus.Event.Disconnected -> {
                    tvStatus.text = "Desconectado"
                    tvStatusDot.setBackgroundResource(R.drawable.dot_red)
                    cardStatus.setCardBackgroundColor(getColor(R.color.bg_card))
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
                is EventBus.Event.Log -> {
                    appendLog(event.line)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        loadSavedToken()
        setupInterval()
        setupButtons()
        checkPermissions()
        updateUI(PrefsManager.isServiceRunning(this))
    }

    override fun onResume() {
        super.onResume()
        EventBus.onEvent = eventHandler
        checkPermissions()
        if (PrefsManager.isServiceRunning(this)) {
            updateUI(true)
        }
    }

    override fun onPause() {
        super.onPause()
        EventBus.onEvent = null
    }

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
        cardToken = findViewById(R.id.cardToken)
        cardSettings = findViewById(R.id.cardSettings)
    }

    private fun loadSavedToken() {
        val saved = PrefsManager.getToken(this)
        if (saved.isNotEmpty()) {
            etToken.setText(saved)
            tvTokenHint.text = "Token guardado"
            tvTokenHint.setTextColor(getColor(R.color.discord_green))
        }

        val showTokenBtn = findViewById<Button>(R.id.btnShowToken)
        var showing = false
        showTokenBtn.setOnClickListener {
            showing = !showing
            if (showing) {
                etToken.inputType = android.text.InputType.TYPE_CLASS_TEXT
                showTokenBtn.text = "Ocultar"
            } else {
                etToken.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                showTokenBtn.text = "Mostrar"
            }
            etToken.setSelection(etToken.text.length)
        }

        findViewById<Button>(R.id.btnClearToken).setOnClickListener {
            etToken.setText("")
            PrefsManager.saveToken(this, "")
            tvTokenHint.text = ""
            Toast.makeText(this, "Token eliminado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupInterval() {
        val saved = PrefsManager.getInterval(this)
        val idx = intervals.indexOf(saved).coerceAtLeast(1)
        seekInterval.progress = idx
        tvInterval.text = intervalLabels[idx]

        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvInterval.text = intervalLabels[p]
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                val i = s?.progress ?: 1
                PrefsManager.setInterval(this@MainActivity, intervals[i])
                val label = if (intervals[i] >= 60000) "${intervals[i]/60000}min" else "${intervals[i]/1000}s"
                appendLog("Intervalo cambiado a $label")
            }
        })
    }

    private fun setupButtons() {
        btnPermissions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        btnStartStop.setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) {
                stopRpc()
            } else {
                startRpc()
            }
        }

        findViewById<Button>(R.id.btnHowToken).setOnClickListener {
            val msg = """
                |Para obtener tu token:
                |
                |1. Abre Discord en el navegador
                |2. Presiona F12 (o clic derecho > Inspeccionar)
                |3. Ve a la pestaña "Network"
                |4. Busca cualquier request a "gateway.discord.gg"
                |5. Click en el request > Headers
                |6. Busca "Authorization" y copia el valor
                |
                |WARNING: Nunca compartas tu token.
            """.trimMargin()
            android.app.AlertDialog.Builder(this)
                .setTitle("Como obtener tu token")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun startRpc() {
        val token = etToken.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "Pega tu token de Discord", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasUsageAccess()) {
            Toast.makeText(this, "Necesitas permisos de Usage Access", Toast.LENGTH_SHORT).show()
            return
        }

        PrefsManager.saveToken(this, token)
        PrefsManager.setServiceRunning(this, true)

        val svc = Intent(this, AppDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }

        updateUI(true)
        appendLog("Servicio iniciado")
    }

    private fun stopRpc() {
        val svc = Intent(this, AppDetectionService::class.java).apply { action = "STOP" }
        startService(svc)
        PrefsManager.setServiceRunning(this, false)
        updateUI(false)
        appendLog("Servicio detenido")
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
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkPermissions() {
        if (hasUsageAccess()) {
            tvPermStatus.setBackgroundResource(R.drawable.dot_green)
            tvPermLabel.text = "Usage Access: OK"
            tvPermLabel.setTextColor(getColor(R.color.discord_green))
            btnPermissions.alpha = 0.5f
            btnPermissions.isEnabled = false
        } else {
            tvPermStatus.setBackgroundResource(R.drawable.dot_red)
            tvPermLabel.text = "Usage Access: Requerido"
            tvPermLabel.setTextColor(getColor(R.color.discord_red))
            btnPermissions.alpha = 1.0f
            btnPermissions.isEnabled = true
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        tvLog.append("$time $message\n")
        val scroll = (tvLog.layout?.let { it.getLineTop(tvLog.lineCount) - tvLog.height } ?: 0)
        if (scroll > 0) tvLog.scrollTo(0, scroll)
    }
}
