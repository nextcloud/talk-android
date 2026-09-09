/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.repositories.passwordpolicy

import com.nextcloud.talk.models.json.passwordResult.PasswordResult

interface PasswordPolicyRepository {
    /**
     * Asks the server whether [password] satisfies the password policy it advertises.
     *
     * @param url the validation endpoint taken from the password_policy capability
     * @throws IllegalStateException if the server answers without a result
     */
    suspend fun validatePassword(credentials: String, url: String, password: String): PasswordResult

    /**
     * Asks the server for a password that satisfies the policy it advertises.
     *
     * @param url the generation endpoint taken from the password_policy capability
     * @throws IllegalStateException if the server answers without a password
     */
    suspend fun generatePassword(credentials: String, url: String): String
}
