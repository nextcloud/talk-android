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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import butterknife.ButterKnife
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.nextcloud.talk.controllers.base.BaseController

abstract class BaseView : BaseController(), LifecycleOwner, ViewModelStoreOwner {

  private val viewModelStore = ViewModelStore()
  private val lifecycleOwner = ControllerLifecycleOwner(this)

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup
  ): View {
    val view = inflater.inflate(getLayoutId(), container, false)
    unbinder = ButterKnife.bind(this, view)
    onViewBound(view)
    return view
  }

  override fun inflateView(
    inflater: LayoutInflater,
    container: ViewGroup
  ): View {
    return inflateView(inflater, container)
  }

  override fun onDestroy() {
    super.onDestroy()
    viewModelStore.clear()
  }

  override fun getLifecycle(): Lifecycle {
    return lifecycleOwner.lifecycle
  }

  fun viewModelProvider(): ViewModelProvider {
    return viewModelProvider(ViewModelProvider.AndroidViewModelFactory(activity!!.application))
  }

  fun viewModelProvider(factory: ViewModelProvider.NewInstanceFactory): ViewModelProvider {
    return ViewModelProvider(viewModelStore, factory)
  }

  fun viewModelProvider(factory: ViewModelProvider.Factory): ViewModelProvider {
    return ViewModelProvider(viewModelStore, factory)
  }

  override fun getViewModelStore(): ViewModelStore {
    return viewModelStore
  }

  @LayoutRes
  protected abstract fun getLayoutId(): Int
}