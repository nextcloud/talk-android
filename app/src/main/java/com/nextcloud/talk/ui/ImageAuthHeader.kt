/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import androidx.compose.runtime.staticCompositionLocalOf
import coil.request.ImageRequest

/**
 * Credentials of the account a screen belongs to, for images it loads from that account's server, like file previews
 * or conversation avatars. Requests with credentials don't use session cookies, so these images need them.
 */
val LocalImageAuthHeader = staticCompositionLocalOf<String?> { null }

/**
 * Adds [authHeader] as Authorization header, if there is one.
 */
fun ImageRequest.Builder.withAuthHeader(authHeader: String?): ImageRequest.Builder =
    apply { authHeader?.let { addHeader("Authorization", it) } }
