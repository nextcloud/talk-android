/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.repositories.passwordpolicy

import com.nextcloud.talk.models.json.passwordResult.PasswordResultOverall

interface PasswordPolicyRepository {
    /**
     * Asks the server whether [password] satisfies the password policy it advertises.
     *
     * @param url the validation endpoint taken from the password_policy capability
     */
    suspend fun validatePassword(credentials: String, url: String, password: String): PasswordResultOverall
}
