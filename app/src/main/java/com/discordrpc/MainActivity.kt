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
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dotLive: View
    private lateinit var tvStatusSub: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvNowEyebrow: TextView
    private lateinit var tvNowTitle: TextView
    private lateinit var tvNowApp: TextView
    private lateinit var tvTimeStart: TextView
    private lateinit var tvTimeEnd: TextView
    private lateinit var barFill: View
    private lateinit var nowArt: View
    private lateinit var activityList: LinearLayout
    private lateinit var fabStartStop: TextView

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
                    tvNowEyebrow.text = ""
                }
                is EventBus.Event.Connected -> {
                    tvUsername.text = event.username
                    tvStatusSub.text = "Conectado"
                    dotLive.setBackgroundResource(R.drawable.dot_green)
                    tvNowTitle.text = "Esperando..."
                    tvNowEyebrow.text = "CONECTADO"
                    tvNowApp.text = "Discord Gateway activo"
                }
                is EventBus.Event.Disconnected -> {
                    tvStatusSub.text = "Desconectado"
                    dotLive.setBackgroundResource(R.drawable.dot_red)
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
                        addActivityItem(event.name, "Estado enviado por WebSocket")
                    } else {
                        tvNowEyebrow.text = ""
                        tvNowTitle.text = "Inactivo"
                        tvNowApp.text = ""
                        tvTimeStart.text = "00:00"
                        startTime = 0L
                        handler.removeCallbacks(elapsedRunnable)
                    }
                }
                is EventBus.Event.Error -> {
                    tvNowTitle.text = event.message
                    tvStatusSub.text = "Error"
                    dotLive.setBackgroundResource(R.drawable.dot_red)
                }
                is EventBus.Event.Log -> {}
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
        fabStartStop = findViewById(R.id.fabStartStop)
    }

    private fun loadSavedData() {
        val savedToken = PrefsManager.getToken(this)
        if (PrefsManager.getAppId(this).isEmpty()) {
            PrefsManager.saveAppId(this, DEFAULT_APP_ID)
        }

        if (savedToken.isEmpty()) {
            showTokenDialog()
        }
    }

    private fun showTokenDialog() {
        val input = EditText(this).apply {
            hint = "Pega tu Discord token..."
            setPadding(48, 32, 48, 32)
            setTextColor(getColor(R.color.orbit_text))
            setHintTextColor(getColor(R.color.orbit_muted2))
            setBackgroundColor(getColor(R.color.orbit_surface2))
        }

        android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Discord Token")
            .setMessage("Necesitas tu token de Discord.\n\n1. Abre discord.com en navegador\n2. F12 → Network → gateway.discord.gg\n3. Headers → Authorization\n4. Copia el valor completo")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val token = input.text.toString().trim()
                if (token.isNotEmpty()) {
                    PrefsManager.saveToken(this, token)
                    Toast.makeText(this, "Token guardado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupButtons() {
        findViewById<View>(R.id.navSettings).setOnClickListener {
            showSettingsDialog()
        }

        findViewById<View>(R.id.navHome).setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) stopRpc() else startRpc()
        }

        fabStartStop.setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) stopRpc() else startRpc()
        }

        findViewById<TextView>(R.id.btnVerLog).setOnClickListener {
            showLogDialog()
        }
    }

    private fun showSettingsDialog() {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val tvTokenLabel = TextView(this).apply {
            text = "Token"
            setTextColor(getColor(R.color.orbit_muted))
            textSize = 13f
        }
        view.addView(tvTokenLabel)

        val etToken = EditText(this).apply {
            val saved = PrefsManager.getToken(this@MainActivity)
            setText(saved)
            hint = "token..."
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
            val btnPerm = TextView(this).apply {
                text = "Abrir Configuración"
                setTextColor(getColor(R.color.orbit_periwinkle))
                textSize = 12f
                setPadding(0, 8, 0, 0)
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            view.addView(btnPerm)
        }

        val tvIntervalLabel = TextView(this).apply {
            text = "Frecuencia de detección"
            setTextColor(getColor(R.color.orbit_muted))
            textSize = 12f
            setPadding(0, 24, 0, 8)
        }
        view.addView(tvIntervalLabel)

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
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showLogDialog() {
        android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
            .setTitle("Log de actividad")
            .setMessage(buildString {
                appendLine("Servicio: ${if (PrefsManager.isServiceRunning(this@MainActivity)) "Activo" else "Inactivo"}")
                appendLine("Token: ${if (PrefsManager.getToken(this@MainActivity).isNotEmpty()) "Guardado" else "Sin token"}")
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
            Toast.makeText(this, "Configura tu token primero", Toast.LENGTH_SHORT).show()
            showTokenDialog()
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
    }

    private fun stopRpc() {
        val svc = Intent(this, AppDetectionService::class.java).apply { action = "STOP" }
        startService(svc)
        PrefsManager.setServiceRunning(this, false)
        updateUI(false)
    }

    private fun updateUI(running: Boolean) {
        if (running) {
            fabStartStop.text = "■"
            fabStartStop.visibility = View.VISIBLE
            tvStatusSub.text = "Conectando..."
            dotLive.setBackgroundResource(R.drawable.dot_yellow)
            tvNowEyebrow.text = "CONECTANDO"
            tvNowTitle.text = "Espere..."
            tvNowApp.text = "Estableciendo conexión..."
        } else {
            fabStartStop.text = "▶"
            fabStartStop.visibility = View.VISIBLE
            tvStatusSub.text = "Inactivo"
            dotLive.setBackgroundResource(R.drawable.dot_gray)
            tvNowEyebrow.text = ""
            tvNowTitle.text = "Inactivo"
            tvNowApp.text = "Toca ▶ para iniciar"
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
