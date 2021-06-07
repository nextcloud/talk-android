package com.nextcloud.talk.interfaces

import com.stfalcon.chatkit.commons.models.IMessage

interface ExtendedIMessage : IMessage {

    fun isLocationMessage() : Boolean

}