package com.nextcloud.talk.repositories

import com.nextcloud.talk.models.database.UserEntity

data class SharedItem(
    val id: String,
    val name: String,
    val fileSize: Int,
    val date: Long,
    val path: String,
    val link: String,
    val mimeType: String,
    val previewAvailable: Boolean,
    val previewLink: String,
    val userEntity: UserEntity,
)
