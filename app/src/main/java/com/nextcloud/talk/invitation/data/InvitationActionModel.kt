/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.data
enum class ActionEnum { ACCEPT, REJECT }
data class InvitationActionModel(var action: ActionEnum, var statusCode: Int, var invitation: Invitation)
