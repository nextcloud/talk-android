/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 David Leibovych <ariedov@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.domain

data class FileUploadModel(val id: Long, val fileName: String?, var progress: Float = 0f)
