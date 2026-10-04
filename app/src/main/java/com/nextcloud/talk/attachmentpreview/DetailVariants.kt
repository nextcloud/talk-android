/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

/**
 * [current] is the detail text for the file's active compress setting, [alternate] is what it
 * would read as under the other setting. Both are computed regardless of [FileDescription]'s own
 * `compress` flag, so the large preview's detail chip can reserve width for whichever is wider and
 * never resize when the HQ toggle flips which one is actually shown.
 */
internal data class DetailVariants(val current: String, val alternate: String)
