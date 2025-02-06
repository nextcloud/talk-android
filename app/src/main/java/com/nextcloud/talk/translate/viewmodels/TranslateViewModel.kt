/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Julius Linus1 <juliuslinus1@gmail.com>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.translate.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.translate.repositories.TranslateRepository
import com.nextcloud.talk.translate.repositories.model.Language
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class TranslateViewModel @Inject constructor(
    private val repository: TranslateRepository,
    private val currentUserProvider: CurrentUserProviderNew
) : ViewModel() {

    sealed interface ViewState

    data object StartState : ViewState
    class TranslatedState(val msg: String) : ViewState

    class LanguagesRetrievedState(val list: List<Language>) : ViewState

    data object LanguagesErrorState : ViewState

    data object TranslationErrorState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(StartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun translateMessage(toLanguage: String, fromLanguage: String?, text: String) {
        val currentUser: User = currentUserProvider.currentUser.blockingGet()
        val authorization: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!
        val url: String = ApiUtils.getUrlForTranslation(currentUser.baseUrl!!)
        val calculatedFromLanguage =
            if (fromLanguage == null || fromLanguage == "") {
                null
            } else {
                fromLanguage
            }
        Log.i(TAG, "translateMessage Called")
        repository.translateMessage(
            authorization,
            url,
            text,
            toLanguage,
            calculatedFromLanguage
        )
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(TranslateObserver())
    }

    fun getLanguages() {
        val currentUser: User = currentUserProvider.currentUser.blockingGet()
        val authorization: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!
        val url: String = ApiUtils.getUrlForLanguages(currentUser.baseUrl!!)
        Log.d(TAG, "URL is: $url")
        repository.getLanguages(authorization, url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<List<Language>> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    _viewState.value = LanguagesErrorState
                    Log.e(TAG, "Error while retrieving languages: $e")
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(list: List<Language>) {
                    _viewState.value = LanguagesRetrievedState(list)
                    Log.d(TAG, "Languages retrieved: $list")
                }
            })
    }

    inner class TranslateObserver : Observer<String> {
        override fun onSubscribe(d: Disposable) {
            _viewState.value = StartState
        }

        override fun onNext(translatedMessage: String) {
            _viewState.value = TranslatedState(translatedMessage)
        }

        override fun onError(e: Throwable) {
            _viewState.value = TranslationErrorState
            Log.e(TAG, "Error while translating message", e)
        }

        override fun onComplete() {
            // nothing?
        }
    }
    companion object {
        private val TAG = TranslateViewModel::class.simpleName
    }
}
