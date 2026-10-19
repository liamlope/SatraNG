package com.v2ray.ang.ui.compose.liquid

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AtmosphereStateMachineTest {

    @Test
    fun mapsConnectionFlags() {
        assertEquals(
            ConnectionAtmosphereState.Connected,
            AtmosphereStateMachine.fromConnection(isRunning = true, connecting = false, failed = false),
        )
        assertEquals(
            ConnectionAtmosphereState.Connecting,
            AtmosphereStateMachine.fromConnection(isRunning = false, connecting = true, failed = false),
        )
        assertEquals(
            ConnectionAtmosphereState.Failed,
            AtmosphereStateMachine.fromConnection(isRunning = false, connecting = false, failed = true),
        )
        assertEquals(
            ConnectionAtmosphereState.Idle,
            AtmosphereStateMachine.fromConnection(isRunning = false, connecting = false, failed = false),
        )
        assertEquals(
            ConnectionAtmosphereState.Connecting,
            AtmosphereStateMachine.fromConnection(isRunning = true, connecting = true, failed = false),
        )
    }

    @Test
    fun failHoldReturnsToIdle() {
        assertEquals(
            ConnectionAtmosphereState.Failed,
            AtmosphereStateMachine.afterFailHold(ConnectionAtmosphereState.Failed, elapsedMs = 500),
        )
        assertEquals(
            ConnectionAtmosphereState.Idle,
            AtmosphereStateMachine.afterFailHold(ConnectionAtmosphereState.Failed, elapsedMs = 2300),
        )
        assertEquals(
            ConnectionAtmosphereState.Failed,
            AtmosphereStateMachine.afterFailHold(
                ConnectionAtmosphereState.Failed,
                elapsedMs = 5000,
                failedPersistent = true,
            ),
        )
    }
}
