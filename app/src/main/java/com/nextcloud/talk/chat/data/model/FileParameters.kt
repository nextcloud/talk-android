/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

class FileParameters(messageParameters: HashMap<String?, HashMap<String?, String?>>?) :
    RichObjectParameters(messageParameters, "file") {

    val id = string("id")
    val name = string("name")
    val path = string("path")
    val link = string("link")
    val mimetype = string("mimetype")

    val size = long("size")
    val mtime = long("mtime")

    val etag = string("etag")
    val permissions = int("permissions")

    val width = int("width")
    val height = int("height")

    val blurhash = string("blurhash")

    val previewAvailable = yesNo("preview-available")
    val hideDownload = yesNo("hide-download")
}
