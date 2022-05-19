package com.nextcloud.talk.shareditems.model

class SharedMediaItems(
    val items: List<SharedItem>,
    var lastSeenId: Int?,
    var moreItemsExisting: Boolean
)
