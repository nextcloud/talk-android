package com.nextcloud.talk.models.json.translations

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class TranslateData(

    @JsonField(name = ["text"])
    var text: String?,
    @JsonField(name = ["from"])
    var fromLanguage: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null )
}