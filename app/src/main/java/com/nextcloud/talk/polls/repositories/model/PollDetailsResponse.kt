package com.nextcloud.talk.polls.repositories.model

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class PollDetailsResponse(
    @JsonField(name = ["actorType"])
    var actorType: String? = null,

    @JsonField(name = ["actorId"])
    var actorId: String,

    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String,

    @JsonField(name = ["optionId"])
    var optionId: Int,

    ) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, "", "", 0)
}
