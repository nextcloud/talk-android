package com.nextcloud.talk.newarch.utils

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.capybaralabs.swipetoreply.ISwipeControllerActions
import com.capybaralabs.swipetoreply.SwipeController
import com.nextcloud.talk.newarch.features.chat.ChatElement
import com.nextcloud.talk.newarch.features.chat.ChatElementTypes
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element

class ChatSwipeCallback(private val adapter: Adapter, context: Context?, swipeControllerActions: ISwipeControllerActions?) : SwipeController(context, swipeControllerActions) {
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position: Int = viewHolder.adapterPosition
        val element = adapter.elementAt(position)
        if (element != null) {
            val adapterChatElement = element.element as Element<ChatElement>
            if (adapterChatElement.data is ChatElement) {
                val chatElement = adapterChatElement.data as ChatElement
                if (chatElement.elementType == ChatElementTypes.CHAT_MESSAGE) {
                    return super.getMovementFlags(recyclerView, viewHolder)
                }
            }
        }
        return 0
    }
}