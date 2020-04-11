package com.nextcloud.talk.models.json.websocket

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
class ControlOverallWebSocketMessage : BaseWebSocketMessage() {
    @JsonField(name = ["control"])
    var controlWebSocketMessage: ControlWebSocketMessage? = null
}