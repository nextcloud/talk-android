/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import coil.load
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ReferenceInsideMessageBinding
import com.nextcloud.talk.models.json.opengraph.OpenGraphOverall
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class LinkPreview {

    fun showLink(message: ChatMessage, ncApi: NcApi, binding: ReferenceInsideMessageBinding, context: Context) {
        if (message.extractedUrlToPreview.isNullOrEmpty()) {
            binding.referenceName.visibility = View.GONE
            binding.referenceDescription.visibility = View.GONE
            binding.referenceLink.visibility = View.GONE
            binding.referenceThumbImage.visibility = View.GONE
            binding.referenceIndentedSideBar.visibility = View.GONE
            binding.referenceWrapper.tag = null
            return
        }

        if (binding.referenceWrapper.tag == message.extractedUrlToPreview) {
            return
        } else {
            binding.referenceWrapper.tag = message.extractedUrlToPreview
        }

        binding.referenceName.text = ""
        binding.referenceDescription.text = ""
        binding.referenceLink.text = ""
        binding.referenceThumbImage.setImageDrawable(null)

        val credentials: String = ApiUtils.getCredentials(message.activeUser?.username, message.activeUser?.token)!!
        val openGraphLink = ApiUtils.getUrlForOpenGraph(message.activeUser?.baseUrl!!)
        ncApi.getOpenGraph(
            credentials,
            openGraphLink,
            message.extractedUrlToPreview
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<OpenGraphOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(openGraphOverall: OpenGraphOverall) {
                    val reference = openGraphOverall.ocs?.data?.references?.entries?.iterator()?.next()?.value

                    if (reference != null) {
                        val referenceName = reference.openGraphObject?.name
                        if (!referenceName.isNullOrEmpty()) {
                            binding.referenceName.visibility = View.VISIBLE
                            binding.referenceName.text = referenceName
                        } else {
                            binding.referenceName.visibility = View.GONE
                        }

                        val referenceDescription = reference.openGraphObject?.description
                        if (!referenceDescription.isNullOrEmpty()) {
                            binding.referenceDescription.visibility = View.VISIBLE
                            binding.referenceDescription.text = referenceDescription
                        } else {
                            binding.referenceDescription.visibility = View.GONE
                        }

                        val referenceLink = reference.openGraphObject?.link
                        if (!referenceLink.isNullOrEmpty()) {
                            binding.referenceLink.visibility = View.VISIBLE
                            binding.referenceLink.text = referenceLink.replace(HTTPS_PROTOCOL, "")
                        } else {
                            binding.referenceLink.visibility = View.GONE
                        }

                        val referenceThumbUrl = reference.openGraphObject?.thumb
                        if (!referenceThumbUrl.isNullOrEmpty()) {
                            binding.referenceThumbImage.visibility = View.VISIBLE
                            binding.referenceThumbImage.load(referenceThumbUrl)
                        } else {
                            binding.referenceThumbImage.visibility = View.GONE
                        }

                        binding.referenceWrapper.setOnClickListener {
                            val browserIntent = Intent(Intent.ACTION_VIEW, referenceLink!!.toUri())
                            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(browserIntent)
                        }
                    }
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to get openGraph data", e)
                    binding.referenceName.visibility = View.GONE
                    binding.referenceDescription.visibility = View.GONE
                    binding.referenceLink.visibility = View.GONE
                    binding.referenceThumbImage.visibility = View.GONE
                    binding.referenceIndentedSideBar.visibility = View.GONE
                    binding.referenceWrapper.tag = null
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    companion object {
        private val TAG = LinkPreview::class.java.simpleName
        private const val HTTPS_PROTOCOL = "https://"
    }
}
