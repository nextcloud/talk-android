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

package com.nextcloud.talk.newarch.conversationsList.mvp

import android.view.View
import androidx.annotation.LayoutRes
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.base.BaseController

@AutoInjector(NextcloudTalkApplication::class)
abstract class BaseView : BaseController() {

    override fun onDetach(view: View) {
        super.onDetach(view)
        getPresenter().stop()
    }

    override fun onDestroy() {
        getPresenter().destroy()
        super.onDestroy()
    }

    @LayoutRes
    protected abstract fun getLayoutId(): Int

    protected abstract fun getPresenter(): MvpPresenter
}