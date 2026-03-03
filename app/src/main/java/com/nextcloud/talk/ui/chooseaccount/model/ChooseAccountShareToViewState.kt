/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chooseaccount.model

import com.nextcloud.talk.data.user.model.User

sealed interface ChooseAccountShareToViewState

object LoadUsersStartStateChooseAccountShareTo : ChooseAccountShareToViewState
open class LoadUsersSuccessStateChooseAccountShareTo(val users: List<User>) : ChooseAccountShareToViewState
object SwitchUserSuccessStateChooseAccountShareTo : ChooseAccountShareToViewState
object SwitchUserErrorStateChooseAccountShareTo : ChooseAccountShareToViewState
