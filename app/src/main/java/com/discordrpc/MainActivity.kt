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

    private lateinit var dotStatus: View
    private lateinit var tvNowPlayingLabel: TextView
    private lateinit var tvElapsed: TextView
    private lateinit var rowCurrentApp: LinearLayout
    private lateinit var tvAppName: TextView
    private lateinit var tvPackageName: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var etToken: EditText
    private lateinit var tvTokenHint: TextView
    private lateinit var tvInterval: TextView
    private lateinit var seekInterval: SeekBar
    private lateinit var dotPerm: View
    private lateinit var tvPermLabel: TextView
    private lateinit var tvLog: TextView
    private lateinit var fabStartStop: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val intervals = longArrayOf(2000, 3000, 5000, 10000, 30000, 60000)
    private val intervalLabels = arrayOf("2s", "3s", "5s", "10s", "30s", "60s")
    private var progressValue = 0
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (PrefsManager.isServiceRunning(this@MainActivity)) {
                progressValue = (progressValue + 1) % 101
                progressBar.progress = progressValue
                handler.postDelayed(this, 150)
            }
        }
    }

    companion object {
        private const val DEFAULT_APP_ID = "1548810919365054575"
    }

    private val eventHandler: (EventBus.Event) -> Unit = { event ->
        handler.post {
            when (event) {
                is EventBus.Event.Status -> tvNowPlayingLabel.text = event.message
                is EventBus.Event.Connected -> {
                    tvNowPlayingLabel.text = event.username
                    dotStatus.setBackgroundResource(R.drawable.dot_green)
                }
                is EventBus.Event.Disconnected -> {
                    tvNowPlayingLabel.text = "Desconectado"
                    dotStatus.setBackgroundResource(R.drawable.dot_red)
                    updateUI(false)
                }
                is EventBus.Event.AppDetected -> {
                    rowCurrentApp.visibility = View.VISIBLE
                    tvAppName.text = event.name
                    tvPackageName.text = event.packageName
                    progressValue = 0
                    progressBar.progress = 0
                }
                is EventBus.Event.Error -> {
                    tvNowPlayingLabel.text = event.message
                    dotStatus.setBackgroundResource(R.drawable.dot_red)
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

    override fun onPause() {
        super.onPause()
        EventBus.onEvent = null
    }

    private fun initViews() {
        dotStatus = findViewById(R.id.dotStatus)
        tvNowPlayingLabel = findViewById(R.id.tvNowPlayingLabel)
        tvElapsed = findViewById(R.id.tvElapsed)
        rowCurrentApp = findViewById(R.id.rowCurrentApp)
        tvAppName = findViewById(R.id.tvAppName)
        tvPackageName = findViewById(R.id.tvPackageName)
        progressBar = findViewById(R.id.progressBar)
        etToken = findViewById(R.id.etToken)
        tvTokenHint = findViewById(R.id.tvTokenHint)
        seekInterval = findViewById(R.id.seekInterval)
        tvInterval = findViewById(R.id.tvInterval)
        dotPerm = findViewById(R.id.dotPerm)
        tvPermLabel = findViewById(R.id.tvPermLabel)
        tvLog = findViewById(R.id.tvLog)
        fabStartStop = findViewById(R.id.fabStartStop)
    }

    private fun loadSavedData() {
        val savedToken = PrefsManager.getToken(this)
        if (savedToken.isNotEmpty()) {
            etToken.setText(savedToken)
            tvTokenHint.text = "Token guardado"
            tvTokenHint.setTextColor(getColor(R.color.orbit_green))
        }

        if (PrefsManager.getAppId(this).isEmpty()) {
            PrefsManager.saveAppId(this, DEFAULT_APP_ID)
        }

        findViewById<TextView>(R.id.btnShowToken).setOnClickListener {
            val isPassword = etToken.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
            etToken.inputType = if (isPassword) android.text.InputType.TYPE_CLASS_TEXT
            else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            etToken.setSelection(etToken.text.length)
            findViewById<TextView>(R.id.btnShowToken).text = if (isPassword) "Ocultar" else "Mostrar"
        }

        findViewById<TextView>(R.id.btnClearToken).setOnClickListener {
            etToken.setText(""); PrefsManager.saveToken(this, "")
            tvTokenHint.text = ""
        }

        findViewById<TextView>(R.id.btnHowToken).setOnClickListener {
            android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
                .setTitle("Obtener Token")
                .setMessage("1. Abre discord.com en el navegador\n2. Presiona F12\n3. Pestaña Network\n4. Busca gateway.discord.gg\n5. Headers > Authorization\n6. Copia el valor completo")
                .setPositiveButton("OK", null).show()
        }

        findViewById<TextView>(R.id.btnHowAsset).setOnClickListener {
            android.app.AlertDialog.Builder(this, R.style.OrbitDialog)
                .setTitle("Subir Art Assets")
                .setMessage("1. discord.com/developers/applications\n2. Selecciona tu Application\n3. Rich Presence > Art Assets\n4. Add Image(s)\n5. Sube iconos: whatsapp, youtube, facebook, instagram, tik-tok, default\n6. Los nombres deben ser EXACTOS\n7. Save")
                .setPositiveButton("OK", null).show()
        }

        findViewById<TextView>(R.id.btnClearLog).setOnClickListener {
            tvLog.text = ""
        }
    }

    private fun setupInterval() {
        val saved = PrefsManager.getInterval(this)
        val idx = intervals.indexOf(saved).coerceAtLeast(0)
        seekInterval.progress = idx
        tvInterval.text = intervalLabels[idx]
        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tvInterval.text = intervalLabels[p]
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                PrefsManager.setInterval(this@MainActivity, intervals[s?.progress ?: 0])
            }
        })
    }

    private fun setupButtons() {
        findViewById<TextView>(R.id.btnPermissions).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        fabStartStop.setOnClickListener {
            if (PrefsManager.isServiceRunning(this)) stopRpc() else startRpc()
        }
    }

    private fun startRpc() {
        val token = etToken.text.toString().trim()
        if (token.isEmpty()) {
            Toast.makeText(this, "Pega tu token", Toast.LENGTH_SHORT).show(); return
        }
        if (!hasUsageAccess()) {
            Toast.makeText(this, "Otorga Usage Access", Toast.LENGTH_SHORT).show(); return
        }

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
            fabStartStop.text = "■"
            fabStartStop.setBackgroundResource(R.drawable.orbit_button)
            tvNowPlayingLabel.text = "Conectando..."
            dotStatus.setBackgroundResource(R.drawable.dot_yellow)
            rowCurrentApp.visibility = View.GONE
            handler.post(progressRunnable)
        } else {
            fabStartStop.text = "▶"
            fabStartStop.setBackgroundResource(R.drawable.orbit_button)
            tvNowPlayingLabel.text = "Inactivo"
            dotStatus.setBackgroundResource(R.drawable.dot_gray)
            rowCurrentApp.visibility = View.GONE
            progressBar.progress = 0
            progressValue = 0
            handler.removeCallbacks(progressRunnable)
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
            dotPerm.setBackgroundResource(R.drawable.dot_green)
            tvPermLabel.text = "Usage Access"
            tvPermLabel.setTextColor(getColor(R.color.orbit_green))
        } else {
            dotPerm.setBackgroundResource(R.drawable.dot_red)
            tvPermLabel.text = "Usage Access (requerido)"
            tvPermLabel.setTextColor(getColor(R.color.orbit_coral))
        }
    }

    private fun appendLog(message: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("$time  $message\n")
        val scroll = (tvLog.layout?.let { it.getLineTop(tvLog.lineCount) - tvLog.height } ?: 0)
        if (scroll > 0) tvLog.scrollTo(0, scroll)
    }
}
