package com.nextcloud.talk.newarch.local.models

import android.os.Parcelable
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettings
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import kotlinx.android.parcel.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class User(
        var id: Long? = null,
        var userId: String,
        var username: String,
        var baseUrl: String,
        var token: String? = null,
        var displayName: String? = null,
        var pushConfiguration: PushConfiguration? = null,
        var capabilities: Capabilities? = null,
        var clientCertificate: String? = null,
        var signalingSettings: SignalingSettings? = null,
        var status: UserStatus? = null
) : Parcelable

fun User.toUserEntity(): UserNgEntity {
    var userNgEntity: UserNgEntity? = null
    this.id?.let {
        userNgEntity = UserNgEntity(it, userId, username, baseUrl)
    } ?: run {
        userNgEntity = UserNgEntity(userId = this.userId, username = this.username, baseUrl = this.baseUrl)
    }

    userNgEntity!!.token = this.token
    userNgEntity!!.displayName = this.displayName
    userNgEntity!!.pushConfiguration = this.pushConfiguration
    userNgEntity!!.capabilities = this.capabilities
    userNgEntity!!.clientCertificate = this.clientCertificate
    userNgEntity!!.signalingSettings = this.signalingSettings
    userNgEntity!!.status = status

    return userNgEntity!!
}