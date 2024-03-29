/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.data

import com.nextcloud.talk.data.user.model.User
import io.reactivex.Observable

interface InvitationsRepository {
    fun fetchInvitations(user: User): Observable<InvitationsModel>
    fun acceptInvitation(user: User, invitation: Invitation): Observable<InvitationActionModel>
    fun rejectInvitation(user: User, invitation: Invitation): Observable<InvitationActionModel>
}
