/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.bottom.sheet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.hovercard.HoverCardAction
import com.nextcloud.talk.models.json.hovercard.HoverCardOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.EMAIL
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.PROFILE
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.SPREED
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers

private const val TAG = "ProfileBottomSheet"

class ProfileBottomSheet(val ncApi: NcApi, val userModel: User, val viewThemeUtils: ViewThemeUtils) {

    private val allowedAppIds = listOf(SPREED.stringValue, PROFILE.stringValue, EMAIL.stringValue)

    fun showFor(message: ChatMessage, context: Context) {
        if (message.actorType == Participant.ActorType.FEDERATED.toString()) {
            Log.d(TAG, "no actions for federated users are shown")
            return
        }

        ncApi.hoverCard(
            ApiUtils.getCredentials(userModel.username, userModel.token),
            ApiUtils.getUrlForHoverCard(userModel.baseUrl!!, message.actorId!!)
        ).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<HoverCardOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(hoverCardOverall: HoverCardOverall) {
                    bottomSheet(
                        hoverCardOverall.ocs!!.data!!.actions!!,
                        hoverCardOverall.ocs!!.data!!.displayName!!,
                        message.actorId!!,
                        context
                    )
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to get hover card for user " + message.actorId, e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    @SuppressLint("CheckResult")
    private fun bottomSheet(actions: List<HoverCardAction>, displayName: String, userId: String, context: Context) {
        val filteredActions = actions.filter { allowedAppIds.contains(it.appId) }
        val items = filteredActions.map { configureActionListItem(it) }

        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.corner_radius)
            viewThemeUtils.material.colorBottomSheetBackground(this.view)

            title(text = displayName)
            listItemsWithImage(items = items) { _, index, _ ->

                val action = filteredActions[index]

                when (AllowedAppIds.createFor(action)) {
                    PROFILE -> openProfile(action.hyperlink!!, context)
                    EMAIL -> composeEmail(action.title!!, context)
                    SPREED -> talkTo(userId, context)
                }
            }
        }
    }

    private fun configureActionListItem(action: HoverCardAction): BasicListItemWithImage {
        val drawable = when (AllowedAppIds.createFor(action)) {
            PROFILE -> R.drawable.ic_user
            EMAIL -> R.drawable.ic_email
            SPREED -> R.drawable.ic_talk
        }

        return BasicListItemWithImage(
            drawable,
            action.title!!
        )
    }

    private fun talkTo(userId: String, context: Context) {
        val apiVersion =
            ApiUtils.getConversationApiVersion(userModel, intArrayOf(ApiUtils.API_V4, 1))
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = userModel.baseUrl!!,
            roomType = "1",
            invite = userId
        )
        val credentials = ApiUtils.getCredentials(userModel.username, userModel.token)
        ncApi.createRoom(
            credentials,
            retrofitBucket.url,
            retrofitBucket.queryMap
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)
                    // bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.ocs!!.data!!.roomId)

                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(chatIntent)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    private fun composeEmail(address: String, context: Context) {
        val addresses = arrayListOf(address)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:".toUri() // only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, addresses)
        }
        context.startActivity(intent)
    }

    private fun openProfile(hyperlink: String, context: Context) {
        val webpage: Uri = hyperlink.toUri()
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        context.startActivity(intent)
    }

    enum class AllowedAppIds(val stringValue: String) {
        SPREED("spreed"),
        PROFILE("profile"),
        EMAIL("email");

        companion object {
            fun createFor(action: HoverCardAction): AllowedAppIds = valueOf(action.appId!!.uppercase())
        }
    }
}
