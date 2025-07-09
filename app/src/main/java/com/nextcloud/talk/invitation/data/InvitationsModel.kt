/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.data

import com.nextcloud.talk.data.user.model.User

data class InvitationsModel(var user: User, var invitations: List<Invitation>)
