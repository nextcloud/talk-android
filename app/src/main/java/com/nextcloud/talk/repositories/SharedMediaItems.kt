package com.nextcloud.talk.repositories

class SharedMediaItems(
    val items: List<SharedItem>,
    var lastSeenId: Int?,
    var moreItemsExisting: Boolean,
    val authHeader: Map<String, String>
)
