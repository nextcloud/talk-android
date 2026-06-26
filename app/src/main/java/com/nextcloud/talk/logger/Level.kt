/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger

enum class Level(val tag: String) {
    DEBUG("D"),
    INFO("I"),
    WARNING("W"),
    ERROR("E"),
    NONE("-");

    companion object {
        fun fromTag(tag: String): Level? = entries.find { it.tag == tag }
    }
}
