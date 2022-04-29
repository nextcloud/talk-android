package com.nextcloud.talk.repositories

class SharedMediaItems(
    val type: SharedItemType,
    val items: MutableList<SharedItem>,
    var lastSeenId: Int?,
    var moreItemsExisting: Boolean,
    val authHeader: Map<String, String>
)
