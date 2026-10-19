package com.v2ray.ang

import com.v2ray.ang.ui.main.MainAction
import com.v2ray.ang.ui.main.canPingSingle
import com.v2ray.ang.ui.main.isRowPinging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePingTest {
    @Test fun singlePingSpinsOnlyItsRow() {
        val pinging = setOf("a")
        assertTrue(isRowPinging("a", 120, pinging, bulkTesting = true))
        assertFalse(isRowPinging("b", 0, pinging, bulkTesting = true))
    }

    @Test fun bulkTestSpinsUnmeasuredRows() {
        assertTrue(isRowPinging("b", 0, emptySet(), bulkTesting = true))
        assertFalse(isRowPinging("b", 88, emptySet(), bulkTesting = true))
        assertFalse(isRowPinging("b", 0, emptySet(), bulkTesting = false))
    }

    @Test fun singlePingAllowedConnectedOrNot() {
        assertTrue(canPingSingle("a", emptySet(), bulkTesting = false))
        // Another single ping in flight does not block this one.
        assertTrue(canPingSingle("a", setOf("b"), bulkTesting = true))
    }

    @Test fun singlePingRefusedWhileBulkOrDuplicate() {
        assertFalse(canPingSingle("a", emptySet(), bulkTesting = true))
        assertFalse(canPingSingle("a", setOf("a"), bulkTesting = true))
        assertFalse(canPingSingle("", emptySet(), bulkTesting = false))
    }

    @Test fun actionCarriesGuid() = assertEquals("g1", MainAction.PingServer("g1").guid)
}
