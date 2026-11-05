package io.pedalcheck.app

import org.junit.Assert.*
import org.junit.Test

class WarningVoiceTest {
    @Test fun unknownOrMissingPreferenceFallsBackToMale() {
        assertEquals(WarningVoice.MALE, WarningVoice.from(null))
        assertEquals(WarningVoice.MALE, WarningVoice.from("ROBOT"))
        assertEquals(WarningVoice.FEMALE, WarningVoice.from("FEMALE"))
    }
    @Test fun everyWarningHasItsOwnClipInEachVoice() {
        val warnings = listOf(Attention.SIMULTANEOUS, Attention.FULL_ACCELERATOR, Attention.HIGH_SPEED_FULL)
        val clips = WarningVoice.entries.flatMap { voice -> warnings.map { voice.resource(it) } }
        assertFalse(clips.contains(null))
        assertEquals(clips.size, clips.toSet().size)
        WarningVoice.entries.forEach { assertNull(it.resource(Attention.NONE)) }
    }
}
