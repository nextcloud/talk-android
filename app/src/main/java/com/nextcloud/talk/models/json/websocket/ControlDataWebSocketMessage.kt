package com.nextcloud.talk.models.json.websocket

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
class ControlDataWebSocketMessage {
    @JsonField(name = ["action"])
    var action: String? = null

    @JsonField(name = ["peerId"])
    var peerId: String? = null

}