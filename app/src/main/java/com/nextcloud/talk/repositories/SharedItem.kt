package com.nextcloud.talk.repositories

data class SharedItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val link: String,
    val previewAvailable: Boolean,
    val previewLink: String
)
