package io.pedalcheck.app

import android.content.Context
import android.os.SystemClock

data class DemoInput(val accelerator: Double = 0.0, val brake: Boolean = false, val speed: Double = 0.0, val connected: Boolean = true, val demoSource: Boolean = true)

/** Deterministic 20-second, five-state fixture. Unknown means stopped samples, not zero pedals. */
object DemoCycle {
    fun at(nowMs: Long, anchorMs: Long): DemoInput {
        val elapsed = (nowMs - anchorMs).coerceAtLeast(0)
        return when ((elapsed % 20000L) / 4000L) {
            0L -> DemoInput()
            1L -> DemoInput(accelerator = 35.0)
            2L -> DemoInput(brake = true)
            3L -> DemoInput(accelerator = 35.0, brake = true)
            else -> DemoInput(connected = false)
        }
    }
}

/** Same-process Activity/service use one atomically applied preferences snapshot. No vehicle data. */
class SharedDemoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("overlay_demo", Context.MODE_PRIVATE)
    fun read(): DemoInput {
        if (!prefs.getBoolean("demo", true)) return DemoInput(demoSource = false, connected = false)
        if (prefs.getBoolean("cycle", false)) return DemoCycle.at(SystemClock.elapsedRealtime(), prefs.getLong("anchor", 0))
        return DemoInput(prefs.getFloat("accelerator", 0f).toDouble(), prefs.getBoolean("brake", false), prefs.getFloat("speed", 0f).toDouble(), prefs.getBoolean("connected", true))
    }
    fun manual(accelerator: Double? = null, brake: Boolean? = null, speed: Double? = null, connected: Boolean? = null) {
        prefs.edit().apply {
            accelerator?.let { putFloat("accelerator", it.toFloat()) }; brake?.let { putBoolean("brake", it) }
            speed?.let { putFloat("speed", it.toFloat()) }; connected?.let { putBoolean("connected", it) }
        }.apply()
    }
    fun cycle(enabled: Boolean) { prefs.edit().putBoolean("cycle", enabled).putLong("anchor", SystemClock.elapsedRealtime()).apply() }
    fun isCycling() = prefs.getBoolean("cycle", false)
    fun setDemoSource(enabled: Boolean) { prefs.edit().putBoolean("demo", enabled).apply() }
}
