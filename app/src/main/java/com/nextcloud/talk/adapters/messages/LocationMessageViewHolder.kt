package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.ButterKnife
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LocationMessageViewHolder(incomingView: View) : MessageHolders
.IncomingTextMessageViewHolder<ChatMessage>(incomingView) {

    private val TAG = "LocationMessageViewHolder"

    @JvmField
    @BindView(R.id.locationText)
    var messageText: TextView? = null

    @JvmField
    @Inject
    var context: Context? = null

    init {
        ButterKnife.bind(
            this,
            itemView
        )
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        if (message.messageType == ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE) {
            Log.d(TAG, "handle geolocation here")
            messageText!!.text = "geolocation..."
        }
        if (message.messageParameters != null && message.messageParameters.size > 0) {
            for (key in message.messageParameters.keys) {
                val individualHashMap: Map<String, String> = message.messageParameters[key]!!
                val lon = individualHashMap["longitude"]
                val lat = individualHashMap["latitude"]
                Log.d(TAG, "lon $lon lat $lat")
            }
        }
    }
}