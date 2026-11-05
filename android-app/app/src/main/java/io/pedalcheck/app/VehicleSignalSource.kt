package io.pedalcheck.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

interface VehicleSignalSource {
    val description: String
    fun start(onSample: (PedalSample) -> Unit, onUnavailable: (String) -> Unit)
    fun stop()
}

/** Simulation ONLY. No vehicle reads/writes and no network. */
class DemoSignalSource(private val snapshotProvider: (() -> DemoInput)? = null) : VehicleSignalSource {
    override val description = "모의 데이터 · 정차 시연 전용"
    var acceleratorPercent = 0.0
    var brakePedalPressed = false
    var speedKmh = 0.0
    var connected = true
    private val handler = Handler(Looper.getMainLooper())
    private var sequence = 0L
    private var running = false
    private var sampleListener: ((PedalSample) -> Unit)? = null
    private var unavailableListener: ((String) -> Unit)? = null
    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            val input = snapshotProvider?.invoke() ?: DemoInput(acceleratorPercent, brakePedalPressed, speedKmh, connected)
            if (!input.demoSource) unavailableListener?.invoke("모의 소스 해제 · 실차 미연동")
            else if (input.connected) sampleListener?.invoke(PedalSample(++sequence, SystemClock.elapsedRealtime(), input.accelerator, input.brake, input.speed))
            handler.postDelayed(this, 100)
        }
    }
    override fun start(onSample: (PedalSample) -> Unit, onUnavailable: (String) -> Unit) {
        stop(); sampleListener = onSample; unavailableListener = onUnavailable; running = true; handler.post(loop)
    }
    override fun stop() { running = false; handler.removeCallbacks(loop); sampleListener = null; unavailableListener = null }
}

/** Explicit integration boundary. Never substitutes simulated values for a missing vehicle. */
class UnavailablePleosSignalSource : VehicleSignalSource {
    override val description = "Pleos 실차 연동 미구현"
    override fun start(onSample: (PedalSample) -> Unit, onUnavailable: (String) -> Unit) {
        onUnavailable("공식 SDK의 실제 가속/브레이크 페달 신호·권한·샘플 주기 검증 필요")
    }
    override fun stop() = Unit
}
