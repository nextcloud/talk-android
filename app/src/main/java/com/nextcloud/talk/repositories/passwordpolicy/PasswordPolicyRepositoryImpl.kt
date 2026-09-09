/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.repositories.passwordpolicy

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.passwordResult.PasswordResult
import javax.inject.Inject

class PasswordPolicyRepositoryImpl @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) :
    PasswordPolicyRepository {

    override suspend fun validatePassword(credentials: String, url: String, password: String): PasswordResult =
        ncApiCoroutines.validatePassword(credentials, url, password).ocs?.data
            ?: throw IllegalStateException("The password validation response carried no result")
}
