/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.translate.repositories

import com.nextcloud.talk.translate.repositories.model.Language
import io.reactivex.Observable

interface TranslateRepository {

    fun translateMessage(
        authorization: String,
        url: String,
        text: String,
        toLanguage: String,
        fromLanguage: String?
    ): Observable<String>

    fun getLanguages(authorization: String, url: String): Observable<List<Language>>
}
