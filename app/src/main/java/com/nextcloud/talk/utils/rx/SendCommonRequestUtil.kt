package com.nextcloud.talk.utils.rx

import android.util.Log
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.json.generic.GenericOverall
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class SendCommonRequestUtil(val ncApi: NcApi, val credentials: String) {

    fun sendRequest(type: String, link: String) {
        if (type == "POST") {
            ncApi.sendCommonPostRequest(credentials, link)
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Request failed", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else if (type == "DELETE") {
            ncApi.sendCommonDeleteRequest(credentials, link)
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Request failed", e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    companion object {
        private val TAG = SendCommonRequestUtil::class.java.simpleName
    }
}
