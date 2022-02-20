/**
 * Nextcloud Talk application
 *
 * @author BlueLine Labs, Inc.
 * Copyright (C) 2016 BlueLine Labs, Inc.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextcloud.talk.controllers.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.ButterKnife
import butterknife.Unbinder
import com.bluelinelabs.conductor.Controller

abstract class ButterKnifeController : Controller {

    private var unbinder: Unbinder? = null

    constructor()

    constructor(args: Bundle) : super(args)

    protected abstract fun inflateView(inflater: LayoutInflater, container: ViewGroup): View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup, savedViewState: Bundle?): View {
        val view = inflateView(inflater, container)
        unbinder = ButterKnife.bind(this, view)
        onViewBound(view)
        return view
    }

    protected open fun onViewBound(view: View) {
        // unused atm
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        unbinder!!.unbind()
        unbinder = null
    }
}
