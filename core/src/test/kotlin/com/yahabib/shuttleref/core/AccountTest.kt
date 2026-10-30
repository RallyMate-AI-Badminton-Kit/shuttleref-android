package com.yahabib.shuttleref.core

import org.junit.Assert.*
import org.junit.Test

class AccountTest {
    @Test fun onlyVerifiedCurrentSessionCanSignIn() {
        val gate = AccountSessionGate()
        val request = gate.begin("https://one.example", "AY:uid-a")
        assertFalse(gate.accept(request, BusinessSession(Player("a", "球友"), "token", 2000, "legacy"), 1000))
        assertNull(gate.session)
        assertTrue(gate.accept(request, BusinessSession(Player("a", "球友"), "token", 2000, "tuya"), 1000))
        assertEquals("a", gate.session?.player?.id)
    }
    @Test fun switchedAccountAndServerDiscardOldExchange() {
        val gate = AccountSessionGate()
        val old = gate.begin("https://one.example", "AY:a")
        gate.begin("https://two.example", "AY:b")
        assertFalse(gate.accept(old, BusinessSession(Player("a", "同名"), "token", 2000, "tuya"), 1000))
        assertNull(gate.session)
    }
    @Test fun logoutInvalidatesEvenSameIdentityRelogin() {
        val gate = AccountSessionGate()
        val old = gate.begin("https://one.example", "AY:a")
        gate.clear()
        gate.begin("https://one.example", "AY:a")
        assertFalse(gate.accept(old, BusinessSession(Player("a", "同名"), "token", 2000, "tuya"), 1000))
    }
    @Test fun expiredOrEmptyCredentialsNeverSignIn() {
        val gate = AccountSessionGate()
        val request = gate.begin("https://one.example", "AY:a")
        assertFalse(gate.accept(request, BusinessSession(Player("a", "球友"), "token", 1000, "tuya"), 1000))
        assertFalse(gate.accept(request, BusinessSession(Player("a", "球友"), "", 2000, "tuya"), 1000))
    }
    @Test fun accountValidationDistinguishesPhoneAndEmail() {
        assertTrue(LoginMethod.PHONE_CODE.valid("13800138000", "86"))
        assertFalse(LoginMethod.PHONE_CODE.valid("test@example.com", "86"))
        assertFalse(LoginMethod.EMAIL_CODE.valid("not-email", "86"))
        assertTrue(LoginMethod.EMAIL_PASSWORD.valid("test@example.com", "86"))
        assertFalse(LoginMethod.EMAIL_CODE.valid("test@example.com", "+86"))
    }
    @Test fun fingerprintUsesRegionAndUidNotDisplayName() {
        assertEquals("15c60bcf35371b95", SdkIdentity("uid-test", "AY", "甲").fingerprint)
        assertEquals(SdkIdentity("uid-test", "AY", "甲").fingerprint, SdkIdentity("uid-test", "AY", "乙").fingerprint)
        assertNotEquals(SdkIdentity("uid-test", "AY", "甲").fingerprint, SdkIdentity("uid-test", "EU", "甲").fingerprint)
    }
}
