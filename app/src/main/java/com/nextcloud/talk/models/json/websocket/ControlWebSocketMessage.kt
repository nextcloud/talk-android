package com.nextcloud.talk.models.json.websocket

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
class ControlWebSocketMessage {
    @JsonField(name = ["recipient"])
    var recipient: ActorWebSocketMessage? = null

    @JsonField(name = ["data"])
    var data: ControlDataWebSocketMessage? = null

}