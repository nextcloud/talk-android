package com.nextcloud.talk.shareditems.repositories

import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedMediaItems
import io.reactivex.Observable

interface SharedItemsRepository {

    fun media(parameters: Parameters, type: SharedItemType): Observable<SharedMediaItems>?

    fun media(
        parameters: Parameters,
        type: SharedItemType,
        lastKnownMessageId: Int?
    ): Observable<SharedMediaItems>?

    fun availableTypes(parameters: Parameters): Observable<Set<SharedItemType>>

    data class Parameters(
        val userName: String,
        val userToken: String,
        val baseUrl: String,
        val roomToken: String
    )
}
