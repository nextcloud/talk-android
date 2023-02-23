package com.nextcloud.talk.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NotificationDialogData(
    var title: String = "",
    var text: String = "",
    var primaryActionDescription: String = "",
    var primaryActionUrl: String = "",
    var primaryActionMethod: String = "",
    var secondaryActionDescription: String = "",
    var secondaryActionUrl: String = "",
    var secondaryActionMethod: String = ""
) : Parcelable
