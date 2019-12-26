/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import butterknife.BindView
import butterknife.ButterKnife
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.DisplayUtils.getMessageSelector
import com.nextcloud.talk.utils.DisplayUtils.searchAndColor
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders.IncomingTextMessageViewHolder
import com.stfalcon.chatkit.utils.DateFormatter
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.*

class MagicSystemMessageViewHolder(itemView: View) : IncomingTextMessageViewHolder<ChatMessage>(itemView), KoinComponent {
    val appPreferences: AppPreferences by inject()
    val context: Context by inject()

    @JvmField
    @BindView(R.id.messageTime)
    var messageTime: TextView? = null

    init {
        ButterKnife.bind(
                this,
                itemView
        )
    }

    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        val resources = itemView.resources
        val normalColor = resources.getColor(R.color.bg_message_list_incoming_bubble)
        val pressedColor: Int
        val mentionColor: Int
        pressedColor = normalColor
        mentionColor = resources.getColor(R.color.nc_author_text)
        val bubbleDrawable = getMessageSelector(normalColor,
                resources.getColor(R.color.transparent), pressedColor,
                R.drawable.shape_grouped_incoming_message)
        ViewCompat.setBackground(bubble, bubbleDrawable)
        var messageString: Spannable = SpannableString(message.text)
        if (message.messageParameters != null && message.messageParameters.size > 0) {
            for (key in message.messageParameters.keys) {
                val individualHashMap: HashMap<String, String>? = message.messageParameters[key]
                if (individualHashMap != null && (individualHashMap["type"] == "user" || individualHashMap["type"] == "guest" || individualHashMap["type"] == "call")) {
                    messageString = searchAndColor(messageString, "@" + individualHashMap["name"],
                            mentionColor)
                }
            }
        }
        text.text = messageString
        messageTime?.text = DateFormatter.format(message.createdAt, DateFormatter.Template.TIME)
    }
}
