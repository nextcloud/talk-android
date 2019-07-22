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

package com.nextcloud.talk.utils;

import android.os.Bundle;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.controllers.ChatController;

import java.util.List;

public class ConductorRemapping {
    public static void remapChatController(Router router, long internalUserId, String roomTokenOrId, Bundle bundle, boolean replaceTop) {
        String tag = internalUserId + "@" + roomTokenOrId;
        if (router.getControllerWithTag(tag) != null) {
            List<RouterTransaction> backstack = router.getBackstack();
            RouterTransaction routerTransaction = null;
            for (int i = 0; i < router.getBackstackSize(); i++) {
                if (tag.equals(backstack.get(i).tag())) {
                    routerTransaction = backstack.get(i);
                    backstack.remove(routerTransaction);
                    break;
                }
            }

            backstack.add(routerTransaction);
            router.setBackstack(backstack, new HorizontalChangeHandler());
        } else {
            if (!replaceTop) {
                router.pushController(RouterTransaction.with(new ChatController(bundle))
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()).tag(tag));
            } else {
                router.replaceTopController(RouterTransaction.with(new ChatController(bundle))
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()).tag(tag));
            }
        }
    }
}
