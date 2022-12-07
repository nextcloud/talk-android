/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.adapters.messages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import coil.load
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.databinding.ReferenceInsideMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.opengraph.OpenGraphOverall
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

class LinkPreview {

    fun showLink(
        message: ChatMessage,
        ncApi: NcApi,
        binding: ReferenceInsideMessageBinding,
        context: Context
    ) {
        binding.referenceName.text = ""
        binding.referenceDescription.text = ""
        binding.referenceLink.text = ""
        binding.referenceThumbImage.setImageDrawable(null)

        if (!message.extractedUrlToPreview.isNullOrEmpty()) {
            val credentials: String = ApiUtils.getCredentials(message.activeUser?.username, message.activeUser?.token)
            val openGraphLink = ApiUtils.getUrlForOpenGraph(message.activeUser?.baseUrl)
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
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(referenceLink))
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
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    companion object {
        private val TAG = LinkPreview::class.java.simpleName
        private const val HTTPS_PROTOCOL = "https://"
    }
}
