/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.passwordpolicy

import android.util.Log
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import kotlinx.coroutines.CancellationException
import java.security.SecureRandom

/**
 * Provides a password for a screen that needs one, from the server's own generator where the
 * account advertises it and locally otherwise. What it returns is still subject to
 * [PasswordPolicyValidator], which is what decides whether the server accepts it.
 */
class PasswordGenerator(private val repository: PasswordPolicyRepository) {

    private val random = SecureRandom()

    suspend fun generate(user: User): String = fromServer(user) ?: locally()

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun fromServer(user: User): String? {
        val url = CapabilitiesUtil.getPasswordGenerationUrl(user) ?: return null
        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
        return try {
            repository.generatePassword(credentials, url).takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to have the server generate a password", exception)
            null
        }
    }

    /**
     * The password policy capability is persisted with the account, so it stays absent until the
     * capabilities are refreshed, which makes this a fallback rather than an error path.
     */
    private fun locally(): String {
        val alphabet = LOWERCASE + UPPERCASE + DIGITS + SPECIAL
        val mandatory = listOf(LOWERCASE, UPPERCASE, DIGITS, SPECIAL).map { it.random() }
        val rest = List(LENGTH - mandatory.size) { alphabet.random() }
        return (mandatory + rest).shuffled(random).joinToString("")
    }

    private fun String.random(): Char = this[random.nextInt(length)]

    companion object {
        private val TAG = PasswordGenerator::class.java.simpleName
        private const val LENGTH = 16
        private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val DIGITS = "23456789"
        private const val SPECIAL = "!@#$%&*+-=?"
    }
}
