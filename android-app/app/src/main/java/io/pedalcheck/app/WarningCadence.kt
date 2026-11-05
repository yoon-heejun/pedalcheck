package io.pedalcheck.app

/** Uses monotonic time. A cleared warning cancels its repeat immediately. */
class WarningCadence(private val repeatMs: Long = 8000) {
    private var previous = Attention.NONE
    private var nextAt = 0L
    fun update(attention: Attention, nowMs: Long): Boolean {
        if (attention == Attention.NONE) { previous = attention; nextAt = 0; return false }
        val announce = attention != previous || nowMs >= nextAt
        previous = attention
        if (announce) nextAt = nowMs + repeatMs
        return announce
    }
}
