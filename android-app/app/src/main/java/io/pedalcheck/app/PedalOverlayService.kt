package io.pedalcheck.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Debug-only registered foreground service. Simulated display; never a live vehicle overlay. */
class PedalOverlayService : Service() {
    companion object { const val ACTION_STOP = "io.pedalcheck.app.STOP_MOCK_OVERLAY"; private const val CHANNEL = "mock_overlay"; private const val TAG = "PedalOverlay" }
    private val handler = Handler(Looper.getMainLooper())
    private var engine = PedalEngine(EngineOptions(experimentalWarnings = true))
    private val preferences by lazy { getSharedPreferences("display", MODE_PRIVATE) }
    private var warnings = true
    private var displayKey = ""
    private var params: WindowManager.LayoutParams? = null
    private var warning: TextView? = null
    private var brakeCard: OverlayPedal? = null
    private var accelCard: OverlayPedal? = null
    private val night get() = preferences.getBoolean("night", false)
    private val scale get() = listOf(1f, 1.2f, 1.4f)[preferences.getInt("size", 0).coerceIn(0, 2)]
    private val ink get() = PedalDesign.color(if (night) "#F4F6FA" else "#202B38")
    private val surface get() = PedalDesign.color(if (night) "#1E252E" else "#FFFFFF")
    private lateinit var windows: WindowManager
    private lateinit var source: DemoSignalSource
    private var badge: LinearLayout? = null
    private var caption: TextView? = null
    private var running = false
    private var painted: PedalOutput? = null
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            if (!Settings.canDrawOverlays(this@PedalOverlayService)) { finish("permission revoked"); return }
            if (!SharedDemoStore(this@PedalOverlayService).read().demoSource) { finish("mock source disabled"); return }
            syncSettings()
            render(engine.tick(SystemClock.elapsedRealtime()))
            handler.postDelayed(this, 50)
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish("explicit stop action"); return START_NOT_STICKY }
        if (!BuildConfig.DEBUG || !Settings.canDrawOverlays(this) || !SharedDemoStore(this).read().demoSource) {
            finish("start denied: missing permission, debug build or mock source"); return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "모의 페달 작은 표시", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 1, Intent(this, PedalOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_pedal)
                .setContentTitle(getString(R.string.overlay_notification_title)).setContentText(getString(R.string.overlay_mock_marker))
                .setOngoing(true).setContentIntent(open).addAction(Notification.Action.Builder(null, getString(R.string.overlay_stop), stop).build()).build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(14, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(14, notification)
            windows = getSystemService(WindowManager::class.java)
            warnings = preferences.getBoolean("warnings", true)
            engine = PedalEngine(EngineOptions(experimentalWarnings = warnings))
            val view = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            params = WindowManager.LayoutParams(dp((340 * scale).toInt()), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.END; x = dp(24); y = dp(160)
                title = "PedalCheckMockOverlay"
                alpha = if (Build.VERSION.SDK_INT >= 31) minOf(0.8f, getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch) else 0.8f
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
            badge = view
            buildBadge()
            render(PedalOutput(PedalState.UNKNOWN))
            windows.addView(view, params)
            running = true
            WarningAudio.attach(this, this)
            source = DemoSignalSource { SharedDemoStore(this).read() }
            source.start({ sample -> if (running) render(engine.accept(sample, SystemClock.elapsedRealtime())) }, { reason -> finish(reason) })
            handler.post(watchdog)
            Log.i(TAG, "started MOCK pedal panel, no touch/focus, x=24dp y=160dp")
        } catch (error: RuntimeException) { Log.e(TAG, "overlay start failed", error); finish("overlay or foreground service failed") }
        return START_NOT_STICKY
    }
    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size * scale; typeface = PedalDesign.font(this@PedalOverlayService, bold)
        setTextColor(ink); includeFontPadding = false
    }
    private fun buildBadge() {
        displayKey = "${preferences.getInt("size", 0)}:$night"
        badge?.apply {
            removeAllViews()
            addView(label("페달체크 · 시연", 15f))
            caption = label("확인 불가", 28f, true).apply { setPadding(0, dp(8), 0, dp(10)) }
            addView(caption)
            warning = label("", 22f, true).apply { setTextColor(Color.WHITE); setPadding(dp(12), dp(10), dp(12), dp(10)); background = PedalDesign.rounded(this@PedalOverlayService, PedalDesign.red, 12); visibility = View.GONE }
            addView(warning)
            val pedals = LinearLayout(this@PedalOverlayService).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(10)) }
            brakeCard = OverlayPedal(true); accelCard = OverlayPedal(false)
            pedals.addView(brakeCard!!.view, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
            pedals.addView(accelCard!!.view, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
            addView(pedals)
            addView(label(getString(R.string.overlay_mock_marker), 14f))
        }
        painted = null
    }
    private inner class OverlayPedal(val brake: Boolean) {
        val view = LinearLayout(this@PedalOverlayService).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(10), dp(6), dp(10)) }
        val name = label(if (brake) "브레이크" else "액셀", 20f, true)
        val glyph = PedalGlyph(this@PedalOverlayService, brake)
        val state = label("밟지 않음", 15f)
        init { view.addView(name); view.addView(glyph, LinearLayout.LayoutParams(-1, dp((64 * scale).toInt()))); view.addView(state) }
        fun update(pressed: Boolean, unknown: Boolean) {
            val color = if (pressed) Color.WHITE else ink
            view.background = PedalDesign.rounded(this@PedalOverlayService, if (pressed) { if (brake) PedalDesign.orange else PedalDesign.blue } else surface, 14, PedalDesign.color(if (night) "#38424F" else "#E1E5E9"))
            name.setTextColor(color); glyph.ink = color; state.setTextColor(color)
            state.text = if (unknown) "확인 불가" else if (pressed) "● 밟고 있음" else "밟지 않음"
        }
    }
    private fun syncSettings() {
        val enabled = preferences.getBoolean("warnings", true)
        if (enabled != warnings) { warnings = enabled; engine = PedalEngine(EngineOptions(experimentalWarnings = enabled)) }
        if (displayKey != "${preferences.getInt("size", 0)}:$night") {
            buildBadge()
            params?.let { it.width = dp((340 * scale).toInt()); badge?.let { view -> windows.updateViewLayout(view, it) } }
        }
    }
    private fun render(output: PedalOutput) {
        if (running) WarningAudio.update(this, output.attention)
        if (painted == output) return
        painted = output
        val attention = output.attention != Attention.NONE
        caption?.text = when (output.state) {
            PedalState.ACCELERATOR -> "액셀"
            PedalState.BRAKE -> "브레이크"
            PedalState.BOTH -> "두 페달 함께"
            PedalState.NEUTRAL -> "밟은 페달 없음"
            PedalState.UNKNOWN -> "페달 확인 불가"
        }
        caption?.setTextColor(if (attention) PedalDesign.red else ink)
        warning?.visibility = if (attention) View.VISIBLE else View.GONE
        warning?.text = getString(R.string.overlay_warning, when (output.attention) {
            Attention.SIMULTANEOUS -> "액셀·브레이크 동시 입력"
            Attention.HIGH_SPEED_FULL -> "높은 속도 · 깊은 액셀 지속"
            else -> "깊은 액셀 지속"
        })
        badge?.background = PedalDesign.rounded(this, surface, 22, if (attention) PedalDesign.red else PedalDesign.color(if (night) "#38424F" else "#E1E5E9"))
        brakeCard?.update(output.state == PedalState.BRAKE || output.state == PedalState.BOTH, output.state == PedalState.UNKNOWN)
        accelCard?.update(output.state == PedalState.ACCELERATOR || output.state == PedalState.BOTH, output.state == PedalState.UNKNOWN)
        Log.i(TAG, "MOCK state=${output.state}, warning=${output.attention}, reason=${output.reason}")
    }
    private fun finish(reason: String) {
        Log.i(TAG, "stopping: $reason")
        cleanup(); stopSelf()
    }
    private fun cleanup() {
        running = false; WarningAudio.detach(this); handler.removeCallbacksAndMessages(null)
        if (::source.isInitialized) source.stop()
        badge?.let { view -> if (::windows.isInitialized) try { windows.removeViewImmediate(view) } catch (_: IllegalArgumentException) { /* Already detached by permission revocation. */ } }
        badge = null; caption = null; stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { cleanup(); Log.i(TAG, "destroyed; badge removed"); super.onDestroy() }
    override fun onTaskRemoved(rootIntent: Intent?) { finish("task removed"); super.onTaskRemoved(rootIntent) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
