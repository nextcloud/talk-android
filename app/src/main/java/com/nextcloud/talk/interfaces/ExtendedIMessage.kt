package com.nextcloud.talk.interfaces

import com.stfalcon.chatkit.commons.models.IMessage

interface ExtendedIMessage : IMessage {

    // var isLocationMessage: Boolean

    fun isLocationMessage() : Boolean

}