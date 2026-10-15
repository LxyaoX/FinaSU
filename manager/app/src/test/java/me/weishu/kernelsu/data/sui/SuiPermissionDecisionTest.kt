package me.weishu.kernelsu.data.sui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuiPermissionDecisionTest {
    @Test fun lastingGrantsWriteARule() {
        val root = SuiPermissionDecision.ALLOW_ROOT
        assertTrue(root.allowed)
        assertFalse(root.shell)
        assertFalse(root.onetime)

        val shell = SuiPermissionDecision.ALLOW_SHELL
        assertTrue(shell.allowed)
        assertTrue(shell.shell)
        assertFalse(shell.onetime)
    }

    @Test fun oneTimeGrantIsARootGrantForThisRequestOnly() {
        val once = SuiPermissionDecision.ALLOW_ONCE
        assertTrue(once.allowed)
        assertFalse(once.shell)
        assertTrue(once.onetime)
    }

    @Test fun denyIsRememberedWhileDismissalOnlyRefusesThisRequest() {
        val deny = SuiPermissionDecision.DENY
        assertFalse(deny.allowed)
        assertFalse(deny.shell)
        assertFalse(deny.onetime)

        val dismissed = SuiPermissionDecision.DISMISS
        assertFalse(dismissed.allowed)
        assertFalse(dismissed.shell)
        assertTrue(dismissed.onetime)
    }
}
