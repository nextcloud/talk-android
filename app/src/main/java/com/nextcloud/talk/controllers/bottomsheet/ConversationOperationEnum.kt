package com.nextcloud.talk.controllers.bottomsheet

enum class ConversationOperationEnum {
    RENAME_ROOM, // 2
    MAKE_PUBLIC, // 3
    CHANGE_PASSWORD, // 4
    CLEAR_PASSWORD, // 5
    SET_PASSWORD, // 6
    SHARE_LINK, // 7
    MAKE_PRIVATE, // 8
    GET_JOIN_ROOM, // 10          diff to 99?!
    INVITE_USERS, // 11
    MARK_AS_READ, // 96
    REMOVE_FAVORITE, // 97
    ADD_FAVORITE, // 98
    JOIN_ROOM, // 99             diff to 10?!
}