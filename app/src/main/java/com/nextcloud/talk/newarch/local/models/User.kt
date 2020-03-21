package com.nextcloud.talk.newarch.local.models

import android.os.Parcelable
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettings
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.utils.ApiUtils
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

fun User.getMaxMessageLength(): Int {
    return capabilities?.spreedCapability?.config?.get("chat")?.get("max-length")?.toInt() ?: 1000
}


fun User.getAttachmentsConfig(key: String): Any? {
    return capabilities?.spreedCapability?.config?.get("attachments")?.get(key)
}

fun User.canUserCreateGroupConversations(): Boolean {
    val canCreateValue = capabilities?.spreedCapability?.config?.get("conversations")?.get("can-create")
    canCreateValue?.let {
        return it.toBoolean()
    }
    return true
}


fun User.getCredentials(): String = ApiUtils.getCredentials(username, token)

fun User.hasSpreedFeatureCapability(capabilityName: String): Boolean {
    return capabilities?.spreedCapability?.features?.contains(capabilityName) ?: false
}

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