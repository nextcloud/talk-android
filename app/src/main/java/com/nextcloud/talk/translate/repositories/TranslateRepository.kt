package com.nextcloud.talk.translate.repositories

import io.reactivex.Observable

interface TranslateRepository {

    fun translateMessage(
        authorization: String,
        url: String,
        text: String,
        toLanguage: String,
        fromLanguage: String?
    ): Observable<String>
}
