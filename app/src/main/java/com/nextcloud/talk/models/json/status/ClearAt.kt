package com.nextcloud.talk.models.json.status

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class ClearAt(
    @SerialName("type")
    var type: String,
    @SerialName("time")
    var time: String
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("type", "time")
}
