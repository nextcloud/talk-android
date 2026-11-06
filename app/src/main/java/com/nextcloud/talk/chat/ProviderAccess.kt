/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import kotlin.coroutines.cancellation.CancellationException

/** Runs a content provider call; a failing provider (missing data, revoked permission) is reported, not thrown. */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> runProviderCall(onFailure: suspend (Exception) -> Unit, block: suspend () -> T?): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onFailure(e)
        null
    }
