package com.nextcloud.talk.shareditems.model

import com.nextcloud.talk.models.database.UserEntity

data class SharedItem(
    val id: String,
    val name: String,
    val fileSize: Long?,
    val date: Long,
    val path: String,
    val link: String?,
    val mimeType: String?,
    val previewAvailable: Boolean?,
    val previewLink: String,
    val userEntity: UserEntity,
)
