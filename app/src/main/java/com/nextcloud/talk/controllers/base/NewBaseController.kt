/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author BlueLine Labs, Inc.
 * @author Mario Danic
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2021 BlueLine Labs, Inc.
 * Copyright (C) 2020 Mario Danic (mario@lovelyhq.com)
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
package com.nextcloud.talk.controllers.base

import android.animation.AnimatorInflater
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.appbar.AppBarLayout
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.controllers.AccountVerificationController
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.SwitchAccountController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import java.util.ArrayList
import javax.inject.Inject
import kotlin.jvm.internal.Intrinsics

@AutoInjector(NextcloudTalkApplication::class)
abstract class NewBaseController(@LayoutRes var layoutRes: Int, args: Bundle? = null) : Controller(args) {
    enum class AppBarLayoutType {
        TOOLBAR, SEARCH_BAR, EMPTY
    }

    @Inject
    @JvmField
    var appPreferences: AppPreferences? = null

    @Inject
    @JvmField
    var context: Context? = null

    protected open val title: String?
        get() = null

    protected val actionBar: ActionBar?
        get() {
            var actionBarProvider: ActionBarProvider? = null
            if (this.activity is ActionBarProvider) {
                try {
                    actionBarProvider = this.activity as ActionBarProvider?
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to fetch the action bar provider", e)
                }
            }
            return actionBarProvider?.supportActionBar
        }

    init {
        addLifecycleListener(object : LifecycleListener() {
            override fun postCreateView(controller: Controller, view: View) {
                onViewBound(view)
                actionBar?.let { setTitle() }
            }
        })
        cleanTempCertPreference()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        return inflater.inflate(layoutRes, container, false)
    }

    protected open fun onViewBound(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences!!.isKeyboardIncognito) {
            disableKeyboardPersonalisedLearning(view as ViewGroup)
            if (activity != null && activity is MainActivity) {
                val activity = activity as MainActivity?
                disableKeyboardPersonalisedLearning(activity!!.binding.appBar)
            }
        }
    }

    override fun onAttach(view: View) {
        showSearchOrToolbar()
        setTitle()
        if (actionBar != null) {
            actionBar!!.setDisplayHomeAsUpEnabled(parentController != null || router.backstackSize > 1)
        }
        super.onAttach(view)
    }

    protected fun showSearchOrToolbar() {
        if (activity != null && activity is MainActivity) {
            val showSearchBar = appBarLayoutType == AppBarLayoutType.SEARCH_BAR
            val activity = activity as MainActivity?
            if (appBarLayoutType == AppBarLayoutType.EMPTY) {
                activity!!.binding.toolbar.visibility = View.GONE
                activity.binding.searchToolbar.visibility = View.GONE
            } else {
                val layoutParams = activity!!.binding.searchToolbar.layoutParams as AppBarLayout.LayoutParams
                if (showSearchBar) {
                    activity.binding.searchToolbar.visibility = View.VISIBLE
                    activity.binding.searchText.hint = searchHint
                    activity.binding.toolbar.visibility = View.GONE
                    //layoutParams.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
                    layoutParams.scrollFlags = 0
                    activity.binding.appBar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                        activity.binding.appBar.context,
                        R.animator.appbar_elevation_off
                    )
                } else {
                    activity.binding.searchToolbar.visibility = View.GONE
                    activity.binding.toolbar.visibility = View.VISIBLE
                    layoutParams.scrollFlags = 0
                    activity.binding.appBar.stateListAnimator = AnimatorInflater.loadStateListAnimator(
                        activity.binding.appBar.context,
                        R.animator.appbar_elevation_on
                    )
                }
                activity.binding.searchToolbar.layoutParams = layoutParams
                if (resources != null) {
                    if (showSearchBar) {
                        DisplayUtils.applyColorToStatusBar(
                            activity, ResourcesCompat.getColor(
                                resources!!,
                                R.color.bg_default, null
                            )
                        )
                    } else {
                        DisplayUtils.applyColorToStatusBar(
                            activity, ResourcesCompat.getColor(
                                resources!!,
                                R.color.appbar, null
                            )
                        )
                    }
                }
            }
            if (resources != null) {
                DisplayUtils.applyColorToNavigationBar(
                    activity.window,
                    ResourcesCompat.getColor(resources!!, R.color.bg_default, null)
                )
            }
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    protected fun setTitle() {
        if (title != null && actionBar != null) {
            run {
                var parentController = parentController
                while (parentController != null) {
                    if (parentController is BaseController && parentController.title != null) {
                        return
                    }
                    parentController = parentController.parentController
                }
            }
            actionBar!!.title = title
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            router.popCurrentController()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onChangeStarted(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        super.onChangeStarted(changeHandler, changeType)
        if (changeType.isEnter && actionBar != null) {
            configureMenu(actionBar!!)
        }
    }

    fun configureMenu(toolbar: ActionBar) {
        Intrinsics.checkNotNullParameter(toolbar, "toolbar")
    }

    private fun cleanTempCertPreference() {
        sharedApplication!!.componentApplication.inject(this)
        val temporaryClassNames: MutableList<String> = ArrayList()
        temporaryClassNames.add(ServerSelectionController::class.java.name)
        temporaryClassNames.add(AccountVerificationController::class.java.name)
        temporaryClassNames.add(WebViewLoginController::class.java.name)
        temporaryClassNames.add(SwitchAccountController::class.java.name)
        if (!temporaryClassNames.contains(javaClass.name)) {
            appPreferences!!.removeTemporaryClientCertAlias()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun disableKeyboardPersonalisedLearning(viewGroup: ViewGroup) {
        var view: View?
        var editText: EditText
        for (i in 0 until viewGroup.childCount) {
            view = viewGroup.getChildAt(i)
            if (view is EditText) {
                editText = view
                editText.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            } else if (view is ViewGroup) {
                disableKeyboardPersonalisedLearning(view)
            }
        }
    }

    val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.TOOLBAR
    val searchHint: String
        get() = context!!.getString(R.string.appbar_search_in, context!!.getString(R.string.nc_app_name))

    companion object {
        private val TAG = BaseController::class.java.simpleName
    }
}