/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import android.widget.EditText

interface PollCreateOptionsItemListener {

    fun onRemoveOptionsItemClick(pollCreateOptionItem: PollCreateOptionItem, position: Int)

    fun onOptionsItemTextChanged(pollCreateOptionItem: PollCreateOptionItem)

    fun requestFocus(textField: EditText)
}
