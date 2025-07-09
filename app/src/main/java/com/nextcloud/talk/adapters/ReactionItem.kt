/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import com.nextcloud.talk.models.json.reactions.ReactionVoter

data class ReactionItem(val reactionVoter: ReactionVoter, val reaction: String?)
