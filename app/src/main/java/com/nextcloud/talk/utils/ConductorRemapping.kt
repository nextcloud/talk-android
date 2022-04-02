/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils

import android.os.Bundle
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.controllers.ConversationsListController

object ConductorRemapping {

    fun remapChatController(
        router: Router,
        internalUserId: Long,
        roomTokenOrId: String,
        bundle: Bundle,
        replaceTop: Boolean
    ) {
        remapChatController(router, internalUserId, roomTokenOrId, bundle, replaceTop, false)
    }

    fun remapChatController(
        router: Router,
        internalUserId: Long,
        roomTokenOrId: String,
        bundle: Bundle,
        replaceTop: Boolean,
        pushImmediately: Boolean
    ) {
        val tag = "$internalUserId@$roomTokenOrId"
        if (router.getControllerWithTag(tag) != null) {
            val backstack = router.backstack
            var routerTransaction: RouterTransaction? = null
            for (i in 0 until router.backstackSize) {
                if (tag == backstack[i].tag()) {
                    routerTransaction = backstack[i]
                    backstack.remove(routerTransaction)
                    break
                }
            }

            backstack.add(routerTransaction)
            router.setBackstack(backstack, HorizontalChangeHandler())
        } else {
            val pushChangeHandler = if (pushImmediately) {
                SimpleSwapChangeHandler()
            } else {
                HorizontalChangeHandler()
            }
            if (!replaceTop) {
                if (!router.hasRootController()) {
                    val newBackstack = listOf(
                        RouterTransaction.with(ConversationsListController(Bundle()))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()),
                        RouterTransaction.with(ChatController(bundle))
                            .pushChangeHandler(HorizontalChangeHandler())
                            .popChangeHandler(HorizontalChangeHandler()).tag(tag)
                    )
                    router.setBackstack(newBackstack, SimpleSwapChangeHandler())
                } else {
                    router.pushController(
                        RouterTransaction.with(ChatController(bundle))
                            .pushChangeHandler(pushChangeHandler)
                            .popChangeHandler(HorizontalChangeHandler()).tag(tag)
                    )
                }
            } else {
                router.replaceTopController(
                    RouterTransaction.with(ChatController(bundle))
                        .pushChangeHandler(pushChangeHandler)
                        .popChangeHandler(HorizontalChangeHandler()).tag(tag)
                )
            }
        }
    }
}
