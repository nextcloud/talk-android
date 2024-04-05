/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.raisehand.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.raisehand.RequestAssistanceModel
import com.nextcloud.talk.raisehand.RequestAssistanceRepository
import com.nextcloud.talk.raisehand.WithdrawRequestAssistanceModel
import com.nextcloud.talk.users.UserManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class RaiseHandViewModel @Inject constructor(private val repository: RequestAssistanceRepository) : ViewModel() {

    @Inject
    lateinit var userManager: UserManager

    lateinit var roomToken: String
    private var isBreakoutRoom: Boolean = false

    sealed interface ViewState

    object RaisedHandState : ViewState
    object LoweredHandState : ViewState
    object ErrorState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(LoweredHandState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun clickHandButton() {
        when (viewState.value) {
            LoweredHandState -> {
                raiseHand()
            }
            RaisedHandState -> {
                lowerHand()
            }
            else -> {}
        }
    }

    private fun raiseHand() {
        _viewState.value = RaisedHandState
        if (isBreakoutRoom) {
            repository.requestAssistance(roomToken)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(RequestAssistanceObserver())
        }
    }

    fun lowerHand() {
        _viewState.value = LoweredHandState
        if (isBreakoutRoom) {
            repository.withdrawRequestAssistance(roomToken)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(WithdrawRequestAssistanceObserver())
        }
    }

    fun setData(roomToken: String, isBreakoutRoom: Boolean) {
        this.roomToken = roomToken
        this.isBreakoutRoom = isBreakoutRoom
    }

    inner class RequestAssistanceObserver : Observer<RequestAssistanceModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(requestAssistanceModel: RequestAssistanceModel) {
            // RaisedHandState was already set because it's also used for signaling message
            Log.d(TAG, "requestAssistance successful")
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in RequestAssistanceObserver", e)
            _viewState.value = ErrorState
        }

        override fun onComplete() {
            // dismiss()
        }
    }

    inner class WithdrawRequestAssistanceObserver : Observer<WithdrawRequestAssistanceModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(withdrawRequestAssistanceModel: WithdrawRequestAssistanceModel) {
            // LoweredHandState was already set because it's also used for signaling message
            Log.d(TAG, "withdrawRequestAssistance successful")
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in WithdrawRequestAssistanceObserver", e)
            _viewState.value = ErrorState
        }

        override fun onComplete() {
            // dismiss()
        }
    }

    companion object {
        private val TAG = RaiseHandViewModel::class.java.simpleName
    }
}
