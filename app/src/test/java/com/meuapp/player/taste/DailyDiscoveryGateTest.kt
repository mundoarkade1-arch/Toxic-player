package com.meuapp.player.taste

import org.junit.Assert.*
import org.junit.Test

class DailyDiscoveryGateTest {
    @Test fun firstTimeRuns() = assertTrue(DailyDiscoveryGate.shouldRun("2026-11-16", null, false))
    @Test fun sameDayDoesNotRunAgain() = assertFalse(DailyDiscoveryGate.shouldRun("2026-11-16", "2026-11-16", false))
    @Test fun nextDayRuns() = assertTrue(DailyDiscoveryGate.shouldRun("2026-11-17", "2026-11-16", false))
    @Test fun deletedByUserNeverRuns() {
        assertFalse(DailyDiscoveryGate.shouldRun("2026-11-17", "2026-11-16", true))
        assertFalse(DailyDiscoveryGate.shouldRun("2026-11-17", null, true))
    }
    @Test fun blankTodayDoesNotRun() = assertFalse(DailyDiscoveryGate.shouldRun("", null, false))
    @Test fun nameMatchIgnoresCaseSpacesAccents() {
        assertTrue(DailyDiscoveryGate.isDiscoveryName("Descobertas do dia"))
        assertTrue(DailyDiscoveryGate.isDiscoveryName("  descobertas DO dia "))
        assertFalse(DailyDiscoveryGate.isDiscoveryName("Descobertas"))
        assertFalse(DailyDiscoveryGate.isDiscoveryName("Treinar"))
    }
}
