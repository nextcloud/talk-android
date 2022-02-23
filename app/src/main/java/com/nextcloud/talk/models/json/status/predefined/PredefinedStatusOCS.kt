package com.nextcloud.talk.models.json.status.predefined

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericOCS
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class PredefinedStatusOCS(
    @JsonField(name = ["data"])
    var data: List<PredefinedStatus>?
) : GenericOCS(), Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null)
}