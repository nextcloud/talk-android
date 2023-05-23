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

class TranslateViewModel @Inject constructor(
    private val repository: TranslateRepository,
    private val userManager: UserManager
) : ViewModel() {

    sealed interface ViewState

    object StartState : ViewState
    class TranslatedState(val msg: String) : ViewState
    object ErrorState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(StartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun translateMessage(toLanguage: String, fromLanguage: String?, text: String) {
        val currentUser: User = userManager.currentUser.blockingGet()
        val authorization: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val url: String = ApiUtils.getUrlForTranslation(currentUser.baseUrl)
        val calculatedFromLanguage = if (fromLanguage == null || fromLanguage == "") { null } else { fromLanguage }
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

    inner class TranslateObserver : Observer<String> {
        override fun onSubscribe(d: Disposable) {
            _viewState.value = StartState
        }

        override fun onNext(translatedMessage: String) {
            _viewState.value = TranslatedState(translatedMessage)
        }

        override fun onError(e: Throwable) {
            _viewState.value = ErrorState
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
