/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.passwordpolicy

import com.nextcloud.talk.models.json.passwordResult.PasswordResult
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository

/**
 * Answers with what a test needs of the password policy endpoints, and records what was asked.
 */
class FakePasswordPolicyRepository : PasswordPolicyRepository {

    var validationResult = PasswordResult(passed = true, reason = null)
    var generatedPassword: String? = null
    var generationUrl: String? = null
    var failGeneration = false

    override suspend fun validatePassword(credentials: String, url: String, password: String): PasswordResult =
        validationResult

    override suspend fun generatePassword(credentials: String, url: String): String {
        generationUrl = url
        if (failGeneration) {
            error("the server has no password policy")
        }
        return generatedPassword
            ?: throw IllegalStateException("The password generation response carried no password")
    }
}
