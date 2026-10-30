package com.yahabib.shuttleref.core
import org.junit.Assert.*
import org.junit.Test
class ContractsTest {
    @Test fun oldCallbacksAreDiscarded() {
        val fence = ContextFence(); val old = fence.capture()
        fence.invalidate(); assertFalse(fence.accepts(old)); assertTrue(fence.accepts(fence.capture()))
    }
    @Test fun httpSuccessIsNotDeviceConfirmation() {
        val operation = DeviceOperation(); val id = operation.begin(); operation.accepted(id)
        assertEquals(OperationPhase.WAITING_DEVICE, operation.phase)
        operation.timeout(id); assertEquals(OperationPhase.UNKNOWN, operation.phase)
        operation.deviceResult(id, OperationPhase.CONFIRMED); assertEquals(OperationPhase.CONFIRMED, operation.phase)
    }
    @Test(expected = IllegalStateException::class) fun unknownCannotResubmit() {
        val op = DeviceOperation(); op.timeout(op.begin()); op.begin()
    }
    @Test fun unrelatedAckCannotConfirm() {
        val op = DeviceOperation(); op.begin(); op.deviceResult("other", OperationPhase.CONFIRMED)
        assertEquals(OperationPhase.SUBMITTING, op.phase)
    }
    @Test fun equalNamesDoNotMergePlayers() { assertNotEquals(Player("a", "球友"), Player("b", "球友")) }
    @Test fun originsAreNormalized() { assertEquals("https://shuttleserver.wgine.com", serverOrigin(" https://shuttleserver.wgine.com/ ")) }
    @Test(expected = IllegalArgumentException::class) fun plaintextOriginRejected() { serverOrigin("http://example.com") }
    @Test(expected = IllegalArgumentException::class) fun embeddedCredentialsRejected() { serverOrigin("https://secret@example.com") }
}
