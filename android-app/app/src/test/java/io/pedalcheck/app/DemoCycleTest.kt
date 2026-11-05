package io.pedalcheck.app

import org.junit.Assert.*
import org.junit.Test

class DemoCycleTest {
    @Test fun fiveDistinctStatesAndExactLoopBoundary() {
        assertEquals(DemoInput(), DemoCycle.at(1000, 1000))
        assertEquals(35.0, DemoCycle.at(5000, 1000).accelerator, 0.0)
        assertTrue(DemoCycle.at(9000, 1000).brake)
        assertTrue(DemoCycle.at(13000, 1000).brake)
        assertEquals(35.0, DemoCycle.at(13000, 1000).accelerator, 0.0)
        assertFalse(DemoCycle.at(17000, 1000).connected)
        assertEquals(DemoInput(), DemoCycle.at(21000, 1000))
    }
    @Test fun discontinuityNeverInventsPedalInput() {
        assertEquals(DemoInput(), DemoCycle.at(0, 1000))
        val missing = DemoCycle.at(16000, 0)
        assertFalse(missing.connected)
        val engine = PedalEngine()
        engine.accept(PedalSample(1, 14900, 35.0, true), 14900)
        engine.accept(PedalSample(2, 15000, 35.0, true), 15000)
        assertEquals(PedalState.UNKNOWN, engine.tick(16000).state)
        assertEquals(Attention.NONE, engine.tick(16000).attention)
    }
}
