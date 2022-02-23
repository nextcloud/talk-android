package com.nextcloud.talk.models.json.status

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class ClearAt(
    @JsonField(name = ["type"])
    var type: String,
    @JsonField(name = ["time"])
    var time: String
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("type", "time")
}
