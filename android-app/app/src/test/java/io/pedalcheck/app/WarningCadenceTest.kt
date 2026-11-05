package io.pedalcheck.app

import org.junit.Assert.*
import org.junit.Test

class WarningCadenceTest {
    @Test fun silentUntilWarningThenRepeatsEveryEightSeconds() {
        val gate = WarningCadence()
        assertFalse(gate.update(Attention.NONE, 0))
        assertTrue(gate.update(Attention.FULL_ACCELERATOR, 3000))
        assertFalse(gate.update(Attention.FULL_ACCELERATOR, 10999))
        assertTrue(gate.update(Attention.FULL_ACCELERATOR, 11000))
    }
    @Test fun simultaneousInterruptsFullWarningAndReleaseCancelsRepeat() {
        val gate = WarningCadence()
        assertTrue(gate.update(Attention.FULL_ACCELERATOR, 0))
        assertTrue(gate.update(Attention.SIMULTANEOUS, 300))
        assertFalse(gate.update(Attention.NONE, 500))
        assertFalse(gate.update(Attention.NONE, 8300))
        assertTrue(gate.update(Attention.SIMULTANEOUS, 8400))
    }
}
