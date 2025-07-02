/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.websocket

/**
 * Interface with the properties common to all websocket signaling messages.
 */
interface BaseWebSocketMessageInterface {
    var type: String?
}
