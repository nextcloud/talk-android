/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.userprofile

enum class Scope(val id: String) {
    PRIVATE("v2-private"),
    LOCAL("v2-local"),
    FEDERATED("v2-federated"),
    PUBLISHED("v2-published")
}
