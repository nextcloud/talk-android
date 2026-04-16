/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

abstract class RichObjectParameters(messageParameters: HashMap<String?, HashMap<String?, String?>>?, key: String) {
    protected val params: HashMap<String?, String?>? = messageParameters?.get(key)

    protected fun string(name: String): String = params?.get(name).orEmpty()

    protected fun int(name: String): Int? = params?.get(name)?.toIntOrNull()

    protected fun long(name: String): Long? = params?.get(name)?.toLongOrNull()

    protected fun double(name: String): Double? = params?.get(name)?.toDoubleOrNull()

    protected fun yesNo(name: String): Boolean = params?.get(name) == "yes"
}
