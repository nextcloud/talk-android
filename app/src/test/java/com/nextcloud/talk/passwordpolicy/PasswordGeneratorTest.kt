/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.passwordpolicy

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.PasswordApi
import com.nextcloud.talk.models.json.capabilities.PasswordPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a generated password comes from, and what the local fallback produces.
 */
class PasswordGeneratorTest {

    private val repository = FakePasswordPolicyRepository()
    private val generator = PasswordGenerator(repository)

    private fun user(generateUrl: String? = GENERATE_URL) =
        User(
            username = "alice",
            token = "token",
            baseUrl = "https://cloud.example.com",
            capabilities = Capabilities().apply {
                passwordPolicy = PasswordPolicy(
                    PasswordApi(validatePasswordApi = VALIDATE_URL, generatePasswordApi = generateUrl)
                )
            }
        )

    @Test
    fun `the server generates the password where the account advertises the endpoint`() =
        runTest {
            repository.generatedPassword = "correct-horse-battery-staple"

            val password = generator.generate(user())

            assertEquals("correct-horse-battery-staple", password)
            assertEquals(GENERATE_URL, repository.generationUrl)
        }

    @Test
    fun `an account without the generation endpoint is served locally`() =
        runTest {
            val password = generator.generate(user(generateUrl = null))

            assertNull(repository.generationUrl)
            assertEquals(LENGTH, password.length)
        }

    @Test
    fun `a failing request is served locally`() =
        runTest {
            repository.failGeneration = true

            val password = generator.generate(user())

            assertEquals(LENGTH, password.length)
        }

    @Test
    fun `an answer without a password is served locally`() =
        runTest {
            repository.generatedPassword = null

            val password = generator.generate(user())

            assertEquals(LENGTH, password.length)
        }

    @Test
    fun `an empty answer is served locally`() =
        runTest {
            repository.generatedPassword = ""

            val password = generator.generate(user())

            assertEquals(LENGTH, password.length)
        }

    @Test
    fun `the local password mixes every character class`() =
        runTest {
            val password = generator.generate(user(generateUrl = null))

            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isDigit() })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }

    private companion object {
        const val VALIDATE_URL = "https://cloud.example.com/ocs/v2.php/apps/password_policy/api/v1/validate"
        const val GENERATE_URL = "https://cloud.example.com/ocs/v2.php/apps/password_policy/api/v1/generate"
        const val LENGTH = 16
    }
}
