/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.conversations

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.password.PasswordOverall
import com.nextcloud.talk.repositories.conversations.ConversationsRepository.AllowGuestsResult
import com.nextcloud.talk.repositories.conversations.ConversationsRepository.PasswordResult
import com.nextcloud.talk.repositories.conversations.ConversationsRepository.ResendInvitationsResult
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class ConversationsRepositoryImpl(private val api: NcApi, private val userProvider: CurrentUserProviderNew) :
    ConversationsRepository {

    private val user: User
        get() = userProvider.currentUser.blockingGet()

    private val credentials: String
        get() = ApiUtils.getCredentials(user.username, user.token)!!

    override fun allowGuests(token: String, allow: Boolean): Observable<AllowGuestsResult> {
        val url = ApiUtils.getUrlForRoomPublic(
            apiVersion(),
            user.baseUrl!!,
            token
        )

        val apiObservable = if (allow) {
            api.makeRoomPublic(
                credentials,
                url
            )
        } else {
            api.makeRoomPrivate(
                credentials,
                url
            )
        }

        return apiObservable.map { AllowGuestsResult(it.ocs!!.meta!!.statusCode == STATUS_CODE_OK && allow) }
    }

    override fun password(password: String, token: String): Observable<PasswordResult> {
        val apiObservable = api.setPassword2(
            credentials,
            ApiUtils.getUrlForRoomPassword(
                apiVersion(),
                user.baseUrl!!,
                token
            ),
            password
        )
        return apiObservable.map {
            val passwordPolicyMessage = if (it.code() == STATUS_CODE_BAD_REQUEST) {
                LoganSquare.parse(it.errorBody()!!.string(), PasswordOverall::class.java).ocs!!.data!!
                    .message!!
            } else {
                ""
            }

            PasswordResult(it.isSuccessful, passwordPolicyMessage.isNotEmpty(), passwordPolicyMessage)
        }
    }

    override fun resendInvitations(token: String): Observable<ResendInvitationsResult> {
        val apiObservable = api.resendParticipantInvitations(
            credentials,
            ApiUtils.getUrlForParticipantsResendInvitations(
                apiVersion(),
                user.baseUrl!!,
                token
            )
        )

        return apiObservable.map {
            ResendInvitationsResult(true)
        }
    }

    private fun apiVersion(): Int {
        return ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4))
    }

    companion object {
        const val STATUS_CODE_OK = 200
        const val STATUS_CODE_BAD_REQUEST = 400
    }
}
