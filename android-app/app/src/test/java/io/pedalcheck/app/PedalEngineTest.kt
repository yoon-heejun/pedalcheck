package io.pedalcheck.app

import org.junit.Assert.*
import org.junit.Test

class PedalEngineTest {
    private fun sample(sequence: Long, time: Long, accelerator: Double = 50.0, brake: Boolean = false, speed: Double? = 0.0) =
        PedalSample(sequence, time, accelerator, brake, speed)
    @Test fun startupAndChangedPedalMustSettleWithoutKeepingOldLabel() {
        val e = PedalEngine()
        assertEquals(PedalState.UNKNOWN, e.accept(sample(1, 100), 100).state)
        assertEquals(PedalState.ACCELERATOR, e.accept(sample(2, 200), 200).state)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(3, 250, 0.0, true), 250).state)
        assertEquals(PedalState.BRAKE, e.accept(sample(4, 350, 0.0, true), 350).state)
    }
    @Test fun silenceExpiresIndependentlyOfCallbacks() {
        val e = PedalEngine(); e.accept(sample(1, 100), 100); e.accept(sample(2, 200), 200)
        assertEquals(PedalState.ACCELERATOR, e.tick(700).state)
        assertEquals(PedalState.UNKNOWN, e.tick(701).state)
    }
    @Test fun invalidValuesCancelAllWarnings() {
        val e = PedalEngine(EngineOptions(experimentalWarnings = true))
        for (i in 0L..35) e.accept(sample(i, i * 100, 100.0), i * 100)
        assertEquals(Attention.FULL_ACCELERATOR, e.tick(3500).attention)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(36, 3600, Double.NaN), 3600).state)
        assertEquals(Attention.NONE, e.tick(3650).attention)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(37, 3700, 100.0), 3700).state)
        assertEquals(Attention.NONE, e.accept(sample(38, 3800, 100.0), 3800).attention)
    }
    @Test fun replayAndReorderingCannotRestoreAnOldStatus() {
        val e = PedalEngine(); e.accept(sample(5, 100), 100); e.accept(sample(6, 200), 200)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(4, 150), 250).state)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(6, 200), 300).state)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(7, 400), 400).state)
        assertEquals(PedalState.ACCELERATOR, e.accept(sample(8, 500), 500).state)
    }
    @Test fun gapResetsHoldEvenWhenWatchdogWasNotCalled() {
        val e = PedalEngine(EngineOptions(experimentalWarnings = true))
        for (i in 0L..20) e.accept(sample(i, i * 100, 100.0), i * 100)
        assertEquals(Attention.NONE, e.accept(sample(21, 3000, 100.0), 3000).attention)
        assertEquals(Attention.NONE, e.accept(sample(22, 3100, 100.0), 3100).attention)
    }
    @Test fun hysteresisRejectsThresholdChatter() {
        val e = PedalEngine(); e.accept(sample(1, 100, 5.0), 100)
        assertEquals(PedalState.ACCELERATOR, e.accept(sample(2, 200, 4.0), 200).state)
        assertEquals(PedalState.UNKNOWN, e.accept(sample(3, 300, 3.0), 300).state)
        assertEquals(PedalState.NEUTRAL, e.accept(sample(4, 400, 4.0), 400).state)
    }
    @Test fun warningIsOffByDefaultAndDoesNotCallPedalMistake() {
        val e = PedalEngine()
        var out = PedalOutput(PedalState.UNKNOWN)
        for (i in 0L..50) out = e.accept(sample(i, i * 100, 100.0, true, 100.0), i * 100)
        assertEquals(PedalState.BOTH, out.state); assertEquals(Attention.NONE, out.attention)
    }
    @Test fun simultaneousHasPriorityAndClearsOnRelease() {
        val e = PedalEngine(EngineOptions(experimentalWarnings = true))
        var out = PedalOutput(PedalState.UNKNOWN)
        for (i in 0L..35) out = e.accept(sample(i, i * 100, 100.0, true, 100.0), i * 100)
        assertEquals(Attention.SIMULTANEOUS, out.attention)
        assertEquals(Attention.NONE, e.accept(sample(36, 3600, 0.0, false), 3600).attention)
    }
    @Test fun highSpeedRequiresKnownSpeedAndContinuousInput() {
        val e = PedalEngine(EngineOptions(experimentalWarnings = true))
        var out = PedalOutput(PedalState.UNKNOWN)
        for (i in 0L..20) out = e.accept(sample(i, i * 100, 100.0, false, 80.0), i * 100)
        assertEquals(Attention.HIGH_SPEED_FULL, out.attention)
        assertEquals(Attention.NONE, e.accept(sample(21, 2100, 100.0, false, null), 2100).attention)
    }
    @Test fun futureTimestampsNegativeSpeedAndRegressedClockAreRejected() {
        assertEquals(PedalState.UNKNOWN, PedalEngine().accept(sample(1, 200), 100).state)
        assertEquals(PedalState.UNKNOWN, PedalEngine().accept(sample(1, 100, speed = -1.0), 100).state)
        val e = PedalEngine(); e.accept(sample(1, 100), 100)
        assertEquals(PedalState.UNKNOWN, e.tick(99).state)
    }
}
