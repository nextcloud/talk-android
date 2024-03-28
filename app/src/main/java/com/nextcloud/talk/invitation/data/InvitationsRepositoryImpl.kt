/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.invitation.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable

class InvitationsRepositoryImpl(private val ncApi: NcApi) :
    InvitationsRepository {

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
