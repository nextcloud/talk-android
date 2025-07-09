/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus1 <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.translate.repositories

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.translate.repositories.model.Language
import io.reactivex.Observable
import javax.inject.Inject

class TranslateRepositoryImpl @Inject constructor(private val ncApi: NcApi) : TranslateRepository {

    override fun translateMessage(
        authorization: String,
        url: String,
        text: String,
        toLanguage: String,
        fromLanguage: String?
    ): Observable<String> =
        ncApi.translateMessage(authorization, url, text, toLanguage, fromLanguage).map {
            it.ocs?.data!!.text
        }

    override fun getLanguages(authorization: String, url: String): Observable<List<Language>> =
        ncApi.getLanguages(authorization, url).map {
            it.ocs?.data?.languages
        }
}
