/*
 *
 *   Nextcloud Talk application
 *
 *   @author Tim Krüger
 *   Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.ui.bottom.sheet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.bluelinelabs.conductor.Router
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.controllers.bottomsheet.items.listItemsWithImage
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.hovercard.HoverCardAction
import com.nextcloud.talk.models.json.hovercard.HoverCardOverall
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.EMAIL
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.PROFILE
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet.AllowedAppIds.SPREED
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.bundle.BundleKeys
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.parceler.Parcels

private const val TAG = "ProfileBottomSheet"

class ProfileBottomSheet(val ncApi: NcApi, val userEntity: UserEntity, val router: Router) {

    private val allowedAppIds = listOf(SPREED.stringValue, PROFILE.stringValue, EMAIL.stringValue)

    fun showFor(user: String, context: Context) {

        ncApi.hoverCard(
            ApiUtils.getCredentials(userEntity.username, userEntity.token),
            ApiUtils.getUrlForHoverCard(userEntity.baseUrl, user)
        ).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : io.reactivex.Observer<HoverCardOverall> {
                override fun onSubscribe(d: Disposable) {
                }

                override fun onNext(hoverCardOverall: HoverCardOverall) {
                    bottomSheet(hoverCardOverall.ocs.data.actions, hoverCardOverall.ocs.data.displayName, user, context)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Failed to get hover card for user $user", e)
                }

                override fun onComplete() {
                }
            })
    }

    private fun bottomSheet(actions: List<HoverCardAction>, displayName: String, userId: String, context: Context) {

        val filteredActions = actions.filter { allowedAppIds.contains(it.appId) }
        val items = filteredActions.map { configureActionListItem(it) }

        MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            cornerRadius(res = R.dimen.corner_radius)

            title(text = displayName)
            listItemsWithImage(items = items) { _, index, _ ->

                val action = filteredActions[index]

                when (AllowedAppIds.createFor(action)) {
                    PROFILE -> openProfile(action.hyperlink, context)
                    EMAIL -> composeEmail(action.title, context)
                    SPREED -> talkTo(userId)
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
            action.title
        )
    }

    private fun talkTo(userId: String) {

        val apiVersion =
            ApiUtils.getConversationApiVersion(userEntity, intArrayOf(ApiUtils.APIv4, 1))
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            apiVersion,
            userEntity.baseUrl,
            "1",
            null,
            userId,
            null
        )
        val credentials = ApiUtils.getCredentials(userEntity.username, userEntity.token)
        ncApi!!.createRoom(
            credentials,
            retrofitBucket.getUrl(), retrofitBucket.getQueryMap()
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    // unused atm
                }

                override fun onNext(roomOverall: RoomOverall) {
                    val bundle = Bundle()
                    bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, userEntity)
                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken())
                    bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId())

                    // FIXME once APIv2+ is used only, the createRoom already returns all the data
                    ncApi!!.getRoom(
                        credentials,
                        ApiUtils.getUrlForRoom(
                            apiVersion, userEntity.baseUrl,
                            roomOverall.getOcs().getData().getToken()
                        )
                    )
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(object : Observer<RoomOverall> {
                            override fun onSubscribe(d: Disposable) {
                                // unused atm
                            }

                            override fun onNext(roomOverall: RoomOverall) {
                                bundle.putParcelable(
                                    BundleKeys.KEY_ACTIVE_CONVERSATION,
                                    Parcels.wrap(roomOverall.getOcs().getData())
                                )
                                ConductorRemapping.remapChatController(
                                    router, userEntity.id,
                                    roomOverall.getOcs().getData().getToken(), bundle, true
                                )
                            }

                            override fun onError(e: Throwable) {
                                Log.e(TAG, e.message, e)
                            }

                            override fun onComplete() {
                                // unused atm
                            }
                        })
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
            data = Uri.parse("mailto:") // only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, addresses)
        }
        context.startActivity(intent)
    }

    private fun openProfile(hyperlink: String, context: Context) {
        val webpage: Uri = Uri.parse(hyperlink)
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        context.startActivity(intent)
    }

    enum class AllowedAppIds(val stringValue: String) {
        SPREED("spreed"),
        PROFILE("profile"),
        EMAIL("email");

        companion object {
            fun createFor(action: HoverCardAction): AllowedAppIds = valueOf(action.appId.uppercase())
        }
    }
}
