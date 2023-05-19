package com.nextcloud.talk.translate.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.translate.repositories.TranslateRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class TranslateViewModel @Inject constructor(private val repository: TranslateRepository) : ViewModel() {

    @Inject
    lateinit var userManager: UserManager

    data class TranslateUiState(
        var enableSpinners : Boolean = false,
        var showProgressBar : Boolean = false,
        var showTranslatedMessageContainer : Boolean = false,
        var translatedMessageText : String? = null,
        var originalMessageText : String? = null,
        var errorOccurred : Boolean = false,
        var toLanguages : Array<String>? = null,
        var fromLanguages : Array<String>? = null
    )

    private val _viewState = MutableLiveData(TranslateUiState())
    val viewState : LiveData<TranslateUiState>
        get() = _viewState

    fun translateMessage(toLanguage: String, fromLanguage: String?) {
        val currentUser: User = userManager.currentUser.blockingGet()
        val authorization: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val url: String = ApiUtils.getUrlForTranslation(currentUser.baseUrl)
        val calculatedFromLanguage = if (fromLanguage == null || fromLanguage == "") { null } else { fromLanguage }

        repository.translateMessage(authorization, url,_viewState.value!!.originalMessageText!!,toLanguage,
            calculatedFromLanguage)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(TranslateObserver())
    }

    inner class TranslateObserver() : Observer<String> {
        override fun onSubscribe(d: Disposable) {
            _viewState.value!!.enableSpinners = false
            _viewState.value!!.showTranslatedMessageContainer = false
            _viewState.value!!.showProgressBar = true
        }

        override fun onNext(translatedMessage: String) {
            _viewState.value!!.showProgressBar = false
            _viewState.value!!.showTranslatedMessageContainer = true
            _viewState.value!!.translatedMessageText = translatedMessage
            _viewState.value!!.enableSpinners = true
        }

        override fun onError(e: Throwable) {
            _viewState.value!!.showProgressBar = false
            _viewState.value!!.errorOccurred = true
            _viewState.value!!.enableSpinners = true
            Log.w(TAG, "Error while translating message", e)
        }

        override fun onComplete() {
            // nothing?
        }
    }
    companion object {
        private val TAG = TranslateViewModel::class.simpleName
    }

}