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

package com.nextcloud.talk.controllers.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import butterknife.ButterKnife
import butterknife.Unbinder
import com.bluelinelabs.conductor.Controller

abstract class ButterKnifeController : Controller {

    protected var unbinder: Unbinder? = null

    constructor()

    constructor(args: Bundle) : super(args)

    protected abstract fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        val view = inflateView(inflater, container)
        unbinder = ButterKnife.bind(this, view)
        onViewBound(view)
        return view
    }

    protected open fun onViewBound(view: View) {}

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        unbinder!!.unbind()
        unbinder = null
    }

}
