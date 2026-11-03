/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

/** How many names (the plain one plus numbered ones) are tried when an edit output name is already taken. */
internal const val EDIT_NAME_MAX_ATTEMPTS = 100

/**
 * Claims the first free name: tries [candidate] for attempt 0 (the plain name), then 1, 2, ... up to
 * [maxAttempts] attempts in total. [claim] must reserve the candidate atomically and return false when it is taken.
 * Null when every attempt was taken.
 */
internal fun <T : Any> claimFirstFree(maxAttempts: Int, candidate: (Int) -> T, claim: (T) -> Boolean): T? =
    (0 until maxAttempts).asSequence().map(candidate).firstOrNull(claim)
