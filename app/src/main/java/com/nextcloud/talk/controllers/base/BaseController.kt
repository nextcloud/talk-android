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

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.newarch.utils.dp
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.search_layout.*
import kotlinx.android.synthetic.main.search_layout.view.*
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.android.inject

abstract class BaseController : ButterKnifeController(), ComponentCallbacks {
    enum class AppBarLayoutType {
        TOOLBAR,
        SEARCH_BAR,
        EMPTY
    }

    open val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    val appPreferences: AppPreferences by inject()
    val context: Context by inject()
    val eventBus: EventBus by inject()

    var transitionInProgress = false

    protected val actionBar: ActionBar?
        get() {
            var actionBarProvider: ActionBarProvider? = null
            try {
                actionBarProvider = activity as ActionBarProvider?
            } catch (exception: Exception) {
                Log.d(TAG, "Failed to fetch the action bar provider")
            }

            return actionBarProvider?.supportActionBar
        }

    protected val searchLayout: View?
        get() {
            var view: View? = null
            activity?.let {
                if (it is MainActivity) {
                    view = it.searchCardView
                }
            }
            return view
        }

    protected val toolbarProgressBar: View?
        get() {
            var view: ProgressBar? = null
            activity?.let {
                if (it is MainActivity) {
                    view = it.toolbarProgressBar
                }
            }
            return view
        }


    protected val floatingActionButton: FloatingActionButton?
        get() {
            var floatingActionButton: FloatingActionButton? = null
            activity?.let {
                if (it is MainActivity) {
                    floatingActionButton = it.floatingActionButton
                }
            }

            return floatingActionButton
        }

    protected val appBar: AppBarLayout?
        get() {
            var appBarLayout: AppBarLayout? = null
            activity?.let {
                if (it is MainActivity) {
                    appBarLayout = it.appBar
                }
            }

            return appBarLayout
        }

    protected val toolbar: MaterialToolbar?
        get() {
            var toolbar: MaterialToolbar? = null
            activity?.let {
                if (it is MainActivity) {
                    toolbar = it.toolbar
                }
            }

            return toolbar
        }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onChangeStarted(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        actionBar?.setIcon(null)
        transitionInProgress = true
        setOptionsMenuHidden(true)
        if (changeType == ControllerChangeType.POP_EXIT || changeType == ControllerChangeType.PUSH_EXIT) {
            toolbarProgressBar?.isVisible = false
            activity?.inputEditText?.text = null
            searchLayout?.searchProgressBar?.isVisible = false
            floatingActionButton?.isVisible = false
        }
        super.onChangeStarted(changeHandler, changeType)
    }

    override fun onChangeEnded(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        setOptionsMenuHidden(false)
        transitionInProgress = false
        super.onChangeEnded(changeHandler, changeType)
    }

    private fun showSearchOrToolbar() {
        val value = getAppBarLayoutType() == AppBarLayoutType.SEARCH_BAR
        activity?.let {
            if (it is MainActivity) {
                searchLayout?.isVisible = value
                val appBarLayoutParams = appBar?.layoutParams as CoordinatorLayout.LayoutParams
                floatingActionButton?.setImageResource(getFloatingActionButtonDrawableRes())

                if (getAppBarLayoutType() != AppBarLayoutType.EMPTY) {
                    it.toolbar.isVisible = !value
                    appBarLayoutParams.height = 56.dp
                } else {
                    appBarLayoutParams.height = 0
                }

                appBar?.layoutParams = appBarLayoutParams

                val layoutParams = it.searchCardView?.layoutParams as AppBarLayout.LayoutParams

                if (value) {
                    layoutParams.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                } else {
                    it.appBar.setStatusBarForegroundColor(resources!!.getColor(R.color.fg_default))
                    it.appBar?.toolbar?.setTitleTextColor(resources!!.getColor(R.color.fg_default))
                    layoutParams.scrollFlags = 0
                }
                DisplayUtils.applyColorToStatusBar(activity!!, resources!!.getColor(R.color.bg_default))
                DisplayUtils.applyColorToNavgiationBar(activity!!.window, resources!!.getColor(R.color.bg_default))
                it.searchCardView?.layoutParams = layoutParams
                it.clearButton?.setOnClickListener {
                    activity?.inputEditText?.text = null
                }
                it.leftButton?.setOnClickListener {
                    router.popCurrentController()
                }
            }
        }

    }


    override fun onViewBound(view: View) {
        super.onViewBound(view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.isKeyboardIncognito) {
            disableKeyboardPersonalisedLearning(view as ViewGroup)

            activity?.let {
                if (it is MainActivity) {
                    disableKeyboardPersonalisedLearning(it.appBar)
                }
            }
        }

    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showSearchOrToolbar()
        setTitle()
        actionBar?.setDisplayHomeAsUpEnabled(parentController != null || router.backstackSize > 1)
        searchLayout?.settingsButton?.isVisible = router.backstackSize == 1
        searchLayout?.leftButton?.isVisible = parentController != null || router.backstackSize > 1
    }

    override fun onDetach(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        super.onDetach(view)
    }

    protected fun setTitle() {
        var parentController = parentController
        while (parentController != null) {
            if (parentController is BaseController && parentController.getTitle() != null) {
                return
            }
            parentController = parentController.parentController
        }

        val title = getTitle()
        val actionBar = actionBar
        if (title != null && actionBar != null && getAppBarLayoutType() == AppBarLayoutType.TOOLBAR) {
            actionBar.title = title
        } else if (title != null && activity is MainActivity) {
            activity?.inputEditText?.hint = title
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun disableKeyboardPersonalisedLearning(viewGroup: ViewGroup) {
        var view: View
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

    override fun onLowMemory() {
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
    }

    companion object {

        private val TAG = "BaseController"
    }

    open fun getTitle(): String? {
        return null
    }

    open fun onFloatingActionButtonClick() {}
    open fun getFloatingActionButtonDrawableRes(): Int = R.drawable.ic_add_white_24px
    open fun getAppBarLayoutType(): AppBarLayoutType = AppBarLayoutType.TOOLBAR
}
