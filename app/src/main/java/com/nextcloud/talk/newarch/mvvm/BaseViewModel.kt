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

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

abstract class BaseViewModel<V : BaseView>(application: Application) : AndroidViewModel(
    application
) {

  protected val disposables: CompositeDisposable = CompositeDisposable()
  protected val context: Context = application.applicationContext

  val backgroundAndUIScope = CoroutineScope(
      Job() + Dispatchers.Main
  )

  val backgroundScope = CoroutineScope(
      Job()
  )

  override fun onCleared() {
    super.onCleared()
    disposables.clear()
    backgroundAndUIScope.cancel()
    backgroundScope.cancel()
  }
}