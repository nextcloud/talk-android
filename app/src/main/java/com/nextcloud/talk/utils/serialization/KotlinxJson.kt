/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.serialization

import kotlinx.serialization.json.Json

/**
 * Shared [Json] configuration for parsing server responses with kotlinx serialization.
 *
 * Lenient towards the server: unknown keys are ignored, explicit `null` values for non-nullable
 * properties fall back to their default, and `null` properties are omitted when encoding.
 */
val KotlinxJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
