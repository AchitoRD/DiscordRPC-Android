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
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dotLive: android.view.View
    private lateinit var tvStatusSub: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvNowEyebrow: TextView
    private lateinit var tvNowTitle: TextView
    private lateinit var tvNowApp: TextView
    private lateinit var tvTimeStart: TextView
    private lateinit var tvTimeEnd: TextView
    private lateinit var barFill: android.view.View
    private lateinit var nowArt: android.view.View
    private lateinit var activityList: LinearLayout
    private lateinit var tvLog: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private val elapsedRunnable = object : Runnable {
        override fun run() {
            if (PrefsManager.isServiceRunning(this@MainActivity) && startTime > 0) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                val min = elapsed / 60
                val sec = elapsed % 60
                tvTimeStart.text = String.format("%02d:%02d", min, sec)
                handler.postDelayed(this, 1000)
            }
        }
    }

    companion object {
        private const val DEFAULT_APP_ID = "1548810919365054575"
    }

    private val eventHandler: (EventBus.Event) -> Unit = { event ->
        handler.post {
            when (event) {
                is EventBus.Event.Status -> {
                    tvNowTitle.text = event.message
                }
                is EventBus.Event.Connected -> {
                    tvUsername.text = event.username
                    tvStatusSub.text = "Conectado"
                    dotLive.setBackgroundResource(R.drawable.dot_green)
                    tvNowTitle.text = "Esperando..."
                    tvNowEyebrow.text = "CONECTADO"
                    tvNowApp.text = "Gateway activo"
                    appendLog("Conectado como ${event.username}")
                }
                is EventBus.Event.Disconnected -> {
                    tvStatusSub.text = "Desconectado"
                    dotLive.setBackgroundResource(R.drawable.dot_red)
                    tvNowTitle.text = "Desconectado"
                    tvNowApp.text = event.reason
                    appendLog("Desconectado: ${event.reason}")
                    updateUI(false)
                }
                is EventBus.Event.AppDetected -> {
                    if (event.name.isNotEmpty()) {
                        tvNowEyebrow.text = "DETECTADO AHORA"
                        tvNowTitle.text = event.name
                        tvNowApp.text = "${event.packageName} · en primer plano"
                        startTime = System.currentTimeMillis()
                        handler.removeCallbacks(elapsedRunnable)
                        handler.post(elapsedRunnable)
                        addActivityItem(event.name, "Presence actualizado")
                        appendLog("→ ${event.name} (${event.packageName})")
                    } else {
                        tvNowEyebrow.text = ""
                        tvNowTitle.text = "Inactivo"
                        tvNowApp.text = "App cerrada - presence limpiado"
                        tvTimeStart.text = "00:00"
                        startTime = 0L
                        handler.removeCallbacks(elapsedRunnable)
                        appendLog("Presence limpiado")
                    }
                }
                is EventBus.Event.Error -> {
                    tvNowTitle.text = event.message
                    tvStatusSub.text = "Error"
                    dotLive.setBackgroundResource(R.drawable.dot_red)
                    appendLog("ERROR: ${event.message}")
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
        setupButtons()
        checkPermissions()
        updateUI(PrefsManager.isServiceRunning(this))
    }

    override fun onResume() {
        super.onResume()
        EventBus.onEvent = eventHandler
        checkPermissions()
    }

    override fun onPause() {
        super.onPause()
        EventBus.onEvent = null
    }

    private fun initViews() {
        dotLive = findViewById(R.id.dotLive)
        tvStatusSub = findViewById(R.id.tvStatusSub)
        tvUsername = findViewById(R.id.tvUsername)
        tvNowEyebrow = findViewById(R.id.tvNowEyebrow)
        tvNowTitle = findViewById(R.id.tvNowTitle)
        tvNowApp = findViewById(R.id.tvNowApp)
        tvTimeStart = findViewById(R.id.tvTimeStart)
        tvTimeEnd = findViewById(R.id.tvTimeEnd)
        barFill = findViewById(R.id.barFill)
        nowArt = findViewById(R.id.nowArt)
        activityList = findViewById(R.id.activityList)
        tvLog = findViewById(R.id.tvLog)
    }

    private fun loadSavedData() {
        if (PrefsManager.getAppId(this).isEmpty()) {
            PrefsManager.saveAppId(this, DEFAULT_APP_ID)
        }
        val savedToken = PrefsManager.getToken(this)
        if (savedToken.isNotEmpty()) {
            appendLog("Token guardado")
        } else {
            appendLog("Configura tu token desde Ajustes")
        }
    }

    private fun setupButtons() {
        findViewById<android.view.View>(R.id.navHome).setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) stopRpc() else startRpc()
        }

        findViewById<android.view.View>(R.id.navSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<android.view.View>(R.id.navActivity).setOnClickListener {
            tvLog.text = ""
            appendLog("Log limpiado")
        }

        findViewById<TextView>(R.id.btnVerLog).setOnClickListener {
            showLogDialog()
        }

        findViewById<TextView>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
        }

        findViewById<android.view.View>(R.id.btnSettings).setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun showSettingsDialog() {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            setBackgroundColor(getColor(R.color.orbit_surface))
        }

        TextView(this).apply {
            text = "Discord Token"
            setTextColor(getColor(R.color.orbit_muted))
            textSize = 13f
            view.addView(this)
        }

        val etToken = EditText(this).apply {
            setText(PrefsManager.getToken(this@MainActivity))
            hint = "pega tu token..."
            setTextColor(getColor(R.color.orbit_text))
            setHintTextColor(getColor(R.color.orbit_muted2))
            setBackgroundColor(getColor(R.color.orbit_surface2))
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(32, 24, 32, 24)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        view.addView(etToken)

        val tvPerm = TextView(this).apply {
            text = if (hasUsageAccess()) "✓ Usage Access OK" else "✗ Usage Access REQUERIDO"
            setTextColor(getColor(if (hasUsageAccess()) R.color.orbit_green else R.color.orbit_coral))
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        view.addView(tvPerm)

        if (!hasUsageAccess()) {
            TextView(this).apply {
                text = "→ Abrir Configuración"
                setTextColor(getColor(R.color.orbit_periwinkle))
                textSize = 12f
                setPadding(0, 8, 0, 0)
                setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                view.addView(this)
            }
        }

        TextView(this).apply {
            text = "Frecuencia"
            setTextColor(getColor(R.color.orbit_muted))
            textSize = 12f
            setPadding(0, 24, 0, 8)
            view.addView(this)
        }

        val intervals = arrayOf("2s", "3s", "5s", "10s", "30s", "60s")
        val intervalMs = longArrayOf(2000, 3000, 5000, 10000, 30000, 60000)
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, intervals)
            val currentIdx = intervalMs.indexOf(PrefsManager.getInterval(this@MainActivity)).coerceAtLeast(0)
            setSelection(currentIdx)
        }
        view.addView(spinner)

        android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Ajustes")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val token = etToken.text.toString().trim()
                if (token.isNotEmpty()) PrefsManager.saveToken(this, token)
                PrefsManager.setInterval(this, intervalMs[spinner.selectedItemPosition])
                Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
                appendLog("Ajustes actualizados")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showLogDialog() {
        android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Estado")
            .setMessage(buildString {
                appendLine("Servicio: ${if (PrefsManager.isServiceRunning(this@MainActivity)) "Activo" else "Inactivo"}")
                appendLine("Token: ${if (PrefsManager.getToken(this@MainActivity).isNotEmpty()) "OK" else "Sin token"}")
                appendLine("App ID: ${PrefsManager.getAppId(this@MainActivity)}")
                appendLine("Intervalo: ${PrefsManager.getInterval(this@MainActivity) / 1000}s")
                appendLine("Permisos: ${if (hasUsageAccess()) "OK" else "Sin permiso"}")
            })
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startRpc() {
        val token = PrefsManager.getToken(this)
        if (token.isEmpty()) {
            Toast.makeText(this, "Configura tu token desde Ajustes", Toast.LENGTH_SHORT).show()
            showSettingsDialog()
            return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, "Otorga Usage Access", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        PrefsManager.setServiceRunning(this, true)
        val svc = Intent(this, AppDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
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
            tvStatusSub.text = "Conectando..."
            dotLive.setBackgroundResource(R.drawable.dot_yellow)
            tvNowEyebrow.text = "CONECTANDO"
            tvNowTitle.text = "Espere..."
            tvNowApp.text = "Estableciendo conexión..."
        } else {
            tvStatusSub.text = "Inactivo"
            dotLive.setBackgroundResource(R.drawable.dot_gray)
            tvNowEyebrow.text = ""
            tvNowTitle.text = "Inactivo"
            tvNowApp.text = "Toca Inicio para iniciar"
            tvTimeStart.text = "00:00"
            tvTimeEnd.text = ""
            startTime = 0L
            handler.removeCallbacks(elapsedRunnable)
        }
    }

    private fun addActivityItem(title: String, subtitle: String) {
        val item = LayoutInflater.from(this).inflate(R.layout.item_activity, activityList, false)
        item.findViewById<TextView>(R.id.tvItemTitle).text = title
        item.findViewById<TextView>(R.id.tvItemSub).text = subtitle
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        item.findViewById<TextView>(R.id.tvItemTime).text = time
        activityList.addView(item, 0)
        while (activityList.childCount > 5) {
            activityList.removeViewAt(activityList.childCount - 1)
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("$time  $message\n")
        val scroll = (tvLog.layout?.let { it.getLineTop(tvLog.lineCount) - tvLog.height } ?: 0)
        if (scroll > 0) tvLog.scrollTo(0, scroll)
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
            tvStatusSub.text = if (PrefsManager.isServiceRunning(this)) "Activo" else "Inactivo"
        }
    }
}
