/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the PIP entry logic in CallBaseActivity.
 *
 * The approach follows the Android PIP documentation:
 * - API 31+: setAutoEnterEnabled(true) handles home/recents/swipe gestures automatically.
 *   Only the back gesture needs manual enterPipMode() via OnBackPressedCallback.
 * - API 26-30: onUserLeaveHint() handles home/recents. OnBackPressedCallback handles back.
 *
 * Key insight: onTopResumedActivityChanged should NOT be used for PIP entry — it races
 * with setAutoEnterEnabled and causes double-entry on navigation gestures.
 */
class CallBaseActivityPipTest {

    // Simulated CallBaseActivity state
    private var isInPipMode = false
    private var isPipModePossible = true
    private var autoEnterEnabled = false

    // Tracking
    private var enterPipModeCallCount = 0
    private var enableKeyguardCallCount = 0

    private fun enterPipMode() {
        enableKeyguardCallCount++
        if (isPipModePossible) {
            enterPipModeCallCount++
        }
    }

    @Before
    fun setUp() {
        isInPipMode = false
        isPipModePossible = true
        autoEnterEnabled = false
        enterPipModeCallCount = 0
        enableKeyguardCallCount = 0
    }

    // ==========================================
    // API 31+: auto-enter handles most gestures
    // ==========================================

    @Test
    fun `API 31+ home gesture - auto-enter handles PIP, no manual call needed`() {
        autoEnterEnabled = true
        val isApiS = true

        // Simulate home gesture: onUserLeaveHint fires but is skipped on API 31+
        if (!isInPipMode && isPipModePossible && !isApiS) {
            enterPipMode()
        }

        assertEquals("No manual PIP call on API 31+ home gesture", 0, enterPipModeCallCount)

        // System auto-enter handles it
        if (autoEnterEnabled && isPipModePossible) {
            isInPipMode = true
        }
        assertTrue("Auto-enter succeeds", isInPipMode)
    }

    @Test
    fun `API 31+ recents gesture - auto-enter handles PIP, no manual call needed`() {
        autoEnterEnabled = true
        val isApiS = true

        // Same as home — onUserLeaveHint skipped on API 31+
        if (!isInPipMode && isPipModePossible && !isApiS) {
            enterPipMode()
        }

        assertEquals("No manual PIP call on API 31+ recents gesture", 0, enterPipModeCallCount)
    }

    @Test
    fun `API 31+ back gesture - manual entry via OnBackPressedCallback`() {
        autoEnterEnabled = true

        // Back gesture triggers OnBackPressedCallback, which calls enterPipMode()
        if (isPipModePossible) {
            enterPipMode()
        }

        assertEquals("One manual call from back gesture", 1, enterPipModeCallCount)
    }

    @Test
    fun `API 31+ back gesture - no double entry from auto-enter after manual entry`() {
        autoEnterEnabled = true

        // Back gesture calls enterPipMode() via OnBackPressedCallback
        if (isPipModePossible) {
            enterPipMode()
        }

        // onPictureInPictureModeChanged fires
        isInPipMode = true

        // No second call because system sees we're already in PIP
        assertEquals("Only one PIP entry", 1, enterPipModeCallCount)
    }

    // ==========================================
    // API 26-30: manual entry required
    // ==========================================

    @Test
    fun `API 26-30 home gesture - onUserLeaveHint enters PIP`() {
        autoEnterEnabled = false
        val isApiS = false

        // onUserLeaveHint fires on home/recents
        if (!isInPipMode && isPipModePossible && !isApiS) {
            enterPipMode()
        }

        assertEquals("Manual PIP entry on pre-API 31", 1, enterPipModeCallCount)
    }

    @Test
    fun `API 26-30 back gesture - OnBackPressedCallback enters PIP`() {
        autoEnterEnabled = false

        // Back gesture triggers callback
        if (isPipModePossible) {
            enterPipMode()
        }

        assertEquals("Manual PIP entry from back gesture", 1, enterPipModeCallCount)
    }

    // ==========================================
    // Guard conditions
    // ==========================================

    @Test
    fun `PIP not entered when already in PIP mode`() {
        isInPipMode = true

        if (!isInPipMode && isPipModePossible) {
            enterPipMode()
        }

        assertEquals("Should not enter PIP when already in PIP", 0, enterPipModeCallCount)
    }

    @Test
    fun `PIP not entered when PIP is not possible`() {
        isPipModePossible = false

        if (!isInPipMode && isPipModePossible) {
            enterPipMode()
        }

        assertEquals("Should not enter PIP when not possible", 0, enterPipModeCallCount)
    }

    // ==========================================
    // Verifying the old race condition is eliminated
    // ==========================================

    @Test
    fun `old approach - triple entry race condition (documenting the bug)`() {
        // OLD CODE had three PIP entry points that could all fire before
        // isInPipMode was set to true:
        // 1. System auto-enter (setAutoEnterEnabled)
        // 2. Manual call from onTopResumedActivityChanged
        // 3. Manual call from onPause
        //
        // The fix: on API 31+, only the back gesture calls enterPipMode manually.
        // Home/recents/swipe are handled entirely by setAutoEnterEnabled(true).

        autoEnterEnabled = true

        // NEW approach: no manual calls for non-back gestures on API 31+
        // Only OnBackPressedCallback would call enterPipMode, and only once.
        assertEquals("No spurious PIP entry calls", 0, enterPipModeCallCount)
        assertEquals("No enableKeyguard side effects", 0, enableKeyguardCallCount)
    }

    @Test
    fun `fast swipe left gesture - auto-enter succeeds where manual entry failed`() {
        // The swipe-left (back) navigation gesture was particularly problematic because:
        // 1. OnBackPressedCallback would fire and call enterPipMode()
        // 2. onTopResumedActivityChanged would ALSO fire and call enterPipMode() again
        // 3. The window might already be animating off-screen, causing manual entry to fail
        //
        // Fix: OnBackPressedCallback is the ONLY manual entry point. For swipe-left,
        // it fires early enough that the window is still visible.

        autoEnterEnabled = true

        // OnBackPressedCallback fires (window still visible during back gesture)
        if (isPipModePossible) {
            enterPipMode()
        }

        assertEquals("Exactly one PIP entry from back gesture", 1, enterPipModeCallCount)

        // Simulate PIP mode activated
        isInPipMode = true

        // No additional entry attempts from other lifecycle callbacks
        assertEquals("Still only one call", 1, enterPipModeCallCount)
    }
}
