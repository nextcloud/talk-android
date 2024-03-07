package com.nextcloud.talk.models.json.status.predefined

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nextcloud.talk.models.json.status.ClearAt
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class PredefinedStatus(
    @SerialName("id")
    var id: String,
    @SerialName("icon")
    var icon: String,
    @SerialName("message")
    var message: String,
    @SerialName("clearAt")
    var clearAt: ClearAt?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("id", "icon", "message", null)
}
