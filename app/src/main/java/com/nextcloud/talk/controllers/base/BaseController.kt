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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.controllers.SwitchAccountController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.search_layout.*
import kotlinx.android.synthetic.main.search_layout.view.*
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.android.inject
import java.util.*

abstract class BaseController : ButterKnifeController(), ComponentCallbacks {
    enum class AppBarLayoutType {
        TOOLBAR,
        SEARCH_BAR
    }

    open val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    val appPreferences: AppPreferences by inject()
    val context: Context by inject()
    val eventBus: EventBus by inject()

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
        setOptionsMenuHidden(true)
        if (changeType == ControllerChangeType.POP_EXIT || changeType == ControllerChangeType.PUSH_EXIT) {
            activity?.inputEditText?.text = null
            searchLayout?.searchProgressBar?.isVisible = false
            floatingActionButton?.isVisible = false
        }
        super.onChangeStarted(changeHandler, changeType)
    }

    override fun onChangeEnded(changeHandler: ControllerChangeHandler, changeType: ControllerChangeType) {
        setOptionsMenuHidden(false)
        super.onChangeEnded(changeHandler, changeType)
    }

    private fun showSearchOrToolbar() {
        val value = getAppBarLayoutType() == AppBarLayoutType.SEARCH_BAR
        activity?.let {
            if (it is MainActivity) {
                searchLayout?.isVisible = value
                floatingActionButton?.setImageResource(getFloatingActionButtonDrawableRes())
                it.toolbar.isVisible = !value

                val layoutParams = it.searchCardView?.layoutParams as AppBarLayout.LayoutParams

                if (value) {
                    it.appBar?.setBackgroundResource(R.color.transparent)
                    layoutParams.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                } else {
                    it.appBar?.setBackgroundResource(R.color.colorPrimary)
                    layoutParams.scrollFlags = 0
                }

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

    private fun cleanTempCertPreference() {
        val temporaryClassNames = ArrayList<String>()
        temporaryClassNames.add(SwitchAccountController::class.java.name)

        if (!temporaryClassNames.contains(javaClass.name)) {
            appPreferences.removeTemporaryClientCertAlias()
        }

    }


    override fun onViewBound(view: View) {
        super.onViewBound(view)
        cleanTempCertPreference()
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
