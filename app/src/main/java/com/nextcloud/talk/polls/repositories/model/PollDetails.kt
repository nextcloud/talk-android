package com.nextcloud.talk.polls.repositories.model

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class PollDetails(
    @JsonField(name = ["actorType"])
    var actorType: String? = null,

    @JsonField(name = ["actorId"])
    var actorId: String? = null,

    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String? = null,

    @JsonField(name = ["optionId"])
    var optionId: Int? = 0,

    ) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, 0)
}
