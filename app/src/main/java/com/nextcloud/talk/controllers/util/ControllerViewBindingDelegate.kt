/*
 * Nextcloud Talk application
 *
 * @author BlueLine Labs, Inc.
 * Copyright (C) 2016 BlueLine Labs, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextcloud.talk.controllers.util

import android.view.View
import androidx.lifecycle.LifecycleObserver
import androidx.viewbinding.ViewBinding
import com.bluelinelabs.conductor.Controller
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T : ViewBinding> Controller.viewBinding(bindingFactory: (View) -> T) =
    ControllerViewBindingDelegate(this, bindingFactory)

class ControllerViewBindingDelegate<T : ViewBinding>(
    controller: Controller,
    private val viewBinder: (View) -> T
) : ReadOnlyProperty<Controller, T?>, LifecycleObserver {

    private var binding: T? = null

    init {
        controller.addLifecycleListener(object : Controller.LifecycleListener() {
            override fun postDestroyView(controller: Controller) {
                binding = null
            }
        })
    }

    override fun getValue(thisRef: Controller, property: KProperty<*>): T? {
        if (binding == null) {
            binding = thisRef.view?.let { viewBinder(it) }
        }
        return binding
    }
}
