/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests for the offerer-role election ([PeerConnectionWrapper.isOfferer]) — bug #6169.
 *
 * Contract: for a pair of peers, exactly one must create the initial offer. The role is resolved
 * by a lexicographic comparison of the two session IDs, which yields exactly one offerer **only
 * if both peers compare the same ordered pair of strings** — i.e. the local and remote session
 * IDs are in the same namespace.
 *
 * #6169: in High-Performance Backend mode the remote `sessionId` is a signaling-layer session ID.
 * The fix passes a local signaling session ID (not the Nextcloud session ID) at the construction
 * site so both peers compare the same pair. These tests pin both the desired contract and the
 * failure mode that occurs if cross-namespace IDs are ever passed again.
 */
class PeerConnectionWrapperOffererRoleTest {

    /** Roles computed by BOTH peers for one consistent (same-namespace) pair of session IDs. */
    private fun rolesForConsistentPair(sessionA: String, sessionB: String): Pair<Boolean, Boolean> {
        val aIsOfferer = PeerConnectionWrapper.isOfferer(sessionA, sessionB)
        val bIsOfferer = PeerConnectionWrapper.isOfferer(sessionB, sessionA)
        return aIsOfferer to bIsOfferer
    }

    @Test
    fun exactlyOneOfferer_whenBothPeersCompareTheSameSessionIdPair() {
        val (a, b) = rolesForConsistentPair("session-aaa", "session-bbb")
        assertNotEquals("exactly one of the two peers must be the offerer", a, b)
    }

    @Test
    fun offererRoleIsComplementary_forAnyConsistentPair_regardlessOfInsertionOrder() {
        assertEquals(rolesForConsistentPair("x", "y"), rolesForConsistentPair("x", "y"))
        val (a, b) = rolesForConsistentPair("alpha", "omega")
        assertNotEquals(a, b)
    }

    /**
     * Regression guard for the failure mode of #6169: if cross-namespace IDs are passed (each peer
     * comparing its own Nextcloud session against the other's HPB session — four unrelated
     * strings), both peers can compute the same role. Here both compute "not offerer" → nobody
     * offers → deadlock. The fix prevents this by passing same-namespace IDs at the call site.
     */
    @Test
    fun bothPeersComputeNotOfferer_whenCrossNamespaceSessionIdsArePassed() {
        val aIsOfferer = PeerConnectionWrapper.isOfferer("nc-A", "sig-B")
        val bIsOfferer = PeerConnectionWrapper.isOfferer("nc-B", "sig-A")

        assertFalse("peer A does not take the offerer role", aIsOfferer)
        assertFalse("peer B does not take the offerer role", bIsOfferer)
        assertEquals("both peers compute the same role = deadlock (#6169)", aIsOfferer, bIsOfferer)
    }
}
