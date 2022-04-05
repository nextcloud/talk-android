package com.nextcloud.talk.repositories

class SharedMediaItems(
    val items: List<SharedItem>,
    val lastSeenId: String,
    val authHeader: Map<String, String>
)
