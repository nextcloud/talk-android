package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User

object UserIdUtils {
    private const val NO_ID: Long = -1

    fun getIdForUser(user: User?): Long {
        return if (user?.id != null) {
            user.id!!
        } else {
            NO_ID
        }
    }
}