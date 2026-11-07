/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

/** How the participants of a call are shown. */
enum class CallViewMode(val preferenceValue: String) {
    /** All participants in a grid. */
    GRID("grid"),

    /** The promoted participant fullscreen. */
    SPEAKER("speaker"),

    /** The promoted participant above a bar with the other participants. */
    SPEAKER_WITH_PARTICIPANT_BAR("speaker_with_participant_bar");

    val isSpeakerView: Boolean
        get() = this != GRID

    companion object {
        fun fromPreferenceValue(value: String?): CallViewMode =
            entries.firstOrNull { it.preferenceValue == value } ?: GRID
    }
}
