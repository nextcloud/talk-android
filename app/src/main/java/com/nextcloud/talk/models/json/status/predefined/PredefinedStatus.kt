package com.nextcloud.talk.models.json.status.predefined

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.status.ClearAt
import kotlinx.android.parcel.Parcelize

@Parcelize
@JsonObject
data class PredefinedStatus(
    @JsonField(name = ["id"])
    var id: String,
    @JsonField(name = ["icon"])
    var icon: String,
    @JsonField(name = ["message"])
    var message: String,
    @JsonField(name = ["clearAt"])
    var clearAt: ClearAt?
    ) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this("id", "icon", "message", null)
}





// @Parcelize
// @JsonObject
// data class Status(
//     @JsonField(name = ["userId"])
//     var userId: String?,
//     @JsonField(name = ["message"])
//     var message: String?,
//     /* TODO: Change to enum */
//     @JsonField(name = ["messageId"])
//     var messageId: String?,
//     @JsonField(name = ["messageIsPredefined"])
//     var messageIsPredefined: Boolean,
//     @JsonField(name = ["icon"])
//     var icon: String?,
//     @JsonField(name = ["clearAt"])
//     var clearAt: Long = 0,
//     /* TODO: Change to enum */
//     @JsonField(name = ["status"])
//     var status: String = "offline",
//     @JsonField(name = ["statusIsUserDefined"])
//     var statusIsUserDefined: Boolean
// ) : Parcelable {
//     // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
//     constructor() : this(null, null, null, false, null, 0, "offline", false)
// }