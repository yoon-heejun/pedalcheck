package io.pedalcheck.app

/** Pure Kotlin. Every timestamp uses the SAME monotonic clock, never wall time. */
data class PedalSample(
    val sequence: Long,
    val sampledAtMs: Long,
    val acceleratorPercent: Double,
    val brakePedalPressed: Boolean,
    val speedKmh: Double? = null,
    val available: Boolean = true,
)
enum class PedalState { UNKNOWN, NEUTRAL, ACCELERATOR, BRAKE, BOTH }
enum class Attention(val message: String) {
    NONE(""), SIMULTANEOUS("두 페달 입력이 함께 감지됩니다"),
    FULL_ACCELERATOR("가속 페달을 계속 깊게 밟고 있습니다"),
    HIGH_SPEED_FULL("설정 속도 이상에서 가속 페달 입력이 지속됩니다")
}
data class PedalOutput(val state: PedalState, val attention: Attention = Attention.NONE, val reason: String = "")
data class EngineOptions(
    val experimentalWarnings: Boolean = false,
    val debounceMs: Long = 100,
    val staleMs: Long = 500,
    val fullHoldMs: Long = 3000,
    val simultaneousHoldMs: Long = 300,
    val highSpeedHoldMs: Long = 2000,
    val highSpeedKmh: Double = 80.0,
)

class PedalEngine(private val options: EngineOptions = EngineOptions()) {
    init {
        require(options.debounceMs >= 0 && options.staleMs > 0)
        require(options.fullHoldMs > 0 && options.simultaneousHoldMs > 0 && options.highSpeedHoldMs > 0)
        require(options.highSpeedKmh.isFinite() && options.highSpeedKmh >= 0)
    }
    private var latest: PedalSample? = null
    private var lastSequence = -1L
    private var lastTimestamp = -1L
    private var acceleratorDown = false
    private var candidate = PedalState.UNKNOWN
    private var candidateSince = 0L
    private var committed = PedalState.UNKNOWN
    private var fullSince: Long? = null
    private var bothSince: Long? = null
    private var highSince: Long? = null
    private var lastNow = -1L
    private var lastOutput = PedalOutput(PedalState.UNKNOWN, reason = "신호 대기")

    fun accept(sample: PedalSample, nowMs: Long): PedalOutput {
        if (nowMs < 0 || nowMs < lastNow) return invalidate("시계 기준 오류")
        lastNow = nowMs
        if (!sample.available) return invalidate("신호 수신 중단")
        if (sample.sequence < 0 || sample.sequence <= lastSequence || sample.sampledAtMs <= lastTimestamp)
            return invalidate("중복 또는 역순 신호")
        if (sample.sampledAtMs < 0 || sample.sampledAtMs > nowMs || nowMs - sample.sampledAtMs > options.staleMs)
            return invalidate("신호 시간 오류 또는 지연")
        if (!sample.acceleratorPercent.isFinite() || sample.acceleratorPercent !in 0.0..100.0 ||
            (sample.speedKmh != null && (!sample.speedKmh.isFinite() || sample.speedKmh < 0)))
            return invalidate("신호 값 오류")
        val previous = latest
        if (previous != null && sample.sampledAtMs - previous.sampledAtMs > options.staleMs) clearContinuity()
        latest = sample
        lastSequence = sample.sequence
        lastTimestamp = sample.sampledAtMs
        acceleratorDown = if (acceleratorDown) sample.acceleratorPercent > 3.0 else sample.acceleratorPercent >= 5.0
        val raw = when {
            acceleratorDown && sample.brakePedalPressed -> PedalState.BOTH
            sample.brakePedalPressed -> PedalState.BRAKE
            acceleratorDown -> PedalState.ACCELERATOR
            else -> PedalState.NEUTRAL
        }
        if (candidate != raw) { candidate = raw; candidateSince = sample.sampledAtMs }
        // Show UNKNOWN while a changed state is settling; do not preserve the old pedal label.
        committed = if (sample.sampledAtMs - candidateSince >= options.debounceMs) raw else PedalState.UNKNOWN
        fullSince = sustained(sample.acceleratorPercent >= 95, fullSince, sample.sampledAtMs)
        bothSince = sustained(raw == PedalState.BOTH, bothSince, sample.sampledAtMs)
        highSince = sustained(sample.acceleratorPercent >= 95 && (sample.speedKmh ?: -1.0) >= options.highSpeedKmh, highSince, sample.sampledAtMs)
        val warning = if (!options.experimentalWarnings || committed == PedalState.UNKNOWN) Attention.NONE else when {
            held(bothSince, sample.sampledAtMs, options.simultaneousHoldMs) -> Attention.SIMULTANEOUS
            held(highSince, sample.sampledAtMs, options.highSpeedHoldMs) -> Attention.HIGH_SPEED_FULL
            held(fullSince, sample.sampledAtMs, options.fullHoldMs) -> Attention.FULL_ACCELERATOR
            else -> Attention.NONE
        }
        lastOutput = PedalOutput(committed, warning, if (committed == PedalState.UNKNOWN) "입력 안정화 확인 중" else "")
        return lastOutput
    }

    /** Must be called independently of incoming callbacks. A silent source must become UNKNOWN. */
    fun tick(nowMs: Long): PedalOutput {
        if (nowMs < 0 || nowMs < lastNow) return invalidate("시계 기준 오류")
        lastNow = nowMs
        val sample = latest ?: return lastOutput
        return if (nowMs - sample.sampledAtMs > options.staleMs) invalidate("${options.staleMs}ms 초과 신호 없음") else lastOutput
    }
    fun disconnected(reason: String = "신호 수신 중단"): PedalOutput = invalidate(reason)
    private fun sustained(active: Boolean, start: Long?, now: Long): Long? = if (active) start ?: now else null
    private fun held(start: Long?, now: Long, duration: Long) = start != null && now - start >= duration
    private fun clearContinuity() {
        acceleratorDown = false; candidate = PedalState.UNKNOWN; committed = PedalState.UNKNOWN
        fullSince = null; bothSince = null; highSince = null
    }
    private fun invalidate(reason: String): PedalOutput {
        latest = null; clearContinuity()
        lastOutput = PedalOutput(PedalState.UNKNOWN, reason = reason)
        return lastOutput
    }
}
