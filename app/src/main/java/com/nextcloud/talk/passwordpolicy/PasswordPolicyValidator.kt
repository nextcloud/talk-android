/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.passwordpolicy

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.repositories.passwordpolicy.PasswordPolicyRepository
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Checks passwords against the server's policy on behalf of a screen, and holds the outcome for it
 * to render.
 *
 * @param scope the owning view model's scope; validation is cancelled with it
 * @param userProvider the account to validate against, read at validation time because a screen may
 * learn about its user only after it is created
 */
class PasswordPolicyValidator(
    private val repository: PasswordPolicyRepository,
    private val scope: CoroutineScope,
    private val userProvider: () -> User?
) {
    private val _state = MutableStateFlow<PasswordValidationState>(PasswordValidationState.None)
    val state: StateFlow<PasswordValidationState> = _state

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun validate(password: String) {
        val user = userProvider() ?: return
        val url = CapabilitiesUtil.getPasswordValidationUrl(user) ?: return
        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
        scope.launch {
            try {
                _state.value = PasswordValidationState.Success(
                    repository.validatePassword(credentials, url, password).ocs?.data!!
                )
            } catch (exception: Exception) {
                _state.value = PasswordValidationState.Error(exception.message ?: "")
            }
        }
    }

    fun reset() {
        _state.value = PasswordValidationState.None
    }
}
