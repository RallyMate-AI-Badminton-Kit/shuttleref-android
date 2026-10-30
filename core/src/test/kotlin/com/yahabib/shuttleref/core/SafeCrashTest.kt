package com.yahabib.shuttleref.core
import org.junit.Assert.*
import org.junit.Test
class SafeCrashTest {
    @Test fun exceptionMessagesNeverEnterReport() {
        val cause = IllegalArgumentException("AppSecret=very-secret")
        val report = safeCrash(IllegalStateException("ticket=also-secret", cause))
        assertFalse(report.contains("secret")); assertFalse(report.contains("ticket="))
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertTrue(report.contains("java.lang.IllegalArgumentException"))
        assertTrue(report.contains("exceptionMessagesNeverEnterReport"))
    }
}
