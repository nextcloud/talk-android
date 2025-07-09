/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable

class InvitationsRepositoryImpl(private val ncApi: NcApi) : InvitationsRepository {

    override fun fetchInvitations(user: User): Observable<InvitationsModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

        return ncApi.getInvitations(
            credentials,
            ApiUtils.getUrlForInvitation(user.baseUrl!!)
        ).map { mapToInvitationsModel(user, it.ocs?.data!!) }
    }

    override fun acceptInvitation(user: User, invitation: Invitation): Observable<InvitationActionModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

        return ncApi.acceptInvitation(
            credentials,
            ApiUtils.getUrlForInvitationAccept(user.baseUrl!!, invitation.id)
        ).map { InvitationActionModel(ActionEnum.ACCEPT, it.ocs?.meta?.statusCode!!, invitation) }
    }

    override fun rejectInvitation(user: User, invitation: Invitation): Observable<InvitationActionModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

        return ncApi.rejectInvitation(
            credentials,
            ApiUtils.getUrlForInvitationReject(user.baseUrl!!, invitation.id)
        ).map { InvitationActionModel(ActionEnum.REJECT, it.ocs?.meta?.statusCode!!, invitation) }
    }

    private fun mapToInvitationsModel(
        user: User,
        invitations: List<com.nextcloud.talk.models.json.invitation.Invitation>
    ): InvitationsModel {
        val filteredInvitations = invitations.filter { it.state == OPEN_PENDING_INVITATION }

        return InvitationsModel(
            user,
            filteredInvitations.map { invitation ->
                Invitation(
                    invitation.id,
                    invitation.state,
                    invitation.localCloudId!!,
                    invitation.localToken!!,
                    invitation.remoteAttendeeId,
                    invitation.remoteServerUrl!!,
                    invitation.remoteToken!!,
                    invitation.roomName!!,
                    invitation.userId!!,
                    invitation.inviterCloudId!!,
                    invitation.inviterDisplayName!!
                )
            }
        )
    }

    companion object {
        private const val OPEN_PENDING_INVITATION = 0
    }
}
