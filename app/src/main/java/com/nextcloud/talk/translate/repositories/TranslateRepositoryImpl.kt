package com.nextcloud.talk.translate.repositories

import com.nextcloud.talk.api.NcApi
import io.reactivex.Observable
import javax.inject.Inject

class TranslateRepositoryImpl @Inject constructor(private val ncApi: NcApi) : TranslateRepository {

    override fun translateMessage(
        authorization: String,
        url: String,
        text: String,
        toLanguage: String,
        fromLanguage: String?
    ): Observable<String> {
        return ncApi.translateMessage(authorization, url, text, toLanguage, fromLanguage).map { it.ocs?.data!!.text }
    }
}
