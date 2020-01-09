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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.controllers.AccountVerificationController
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.SwitchAccountController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.utils.FABAwareScrollingViewBehavior
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.search_layout.*
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.android.inject
import java.util.*

abstract class BaseController : ButterKnifeController(), ComponentCallbacks {

    open val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

    val appPreferences: AppPreferences by inject()
    val context: Context by inject()
    val eventBus: EventBus by inject()

    // Note: This is just a quick demo of how an ActionBar *can* be accessed, not necessarily how it *should*
    // be accessed. In a production app, this would use Dagger instead.
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSearchOrToolbar() {
        val value = getIsUsingSearchLayout()
        activity?.let {
            if (it is MainActivity) {
                it.searchCardView.isVisible = value
                it.floatingActionButton.isVisible = value
                it.inputEditText.hint = getSearchHint()

                val layoutParamsForContainer = it.container.layoutParams as CoordinatorLayout.LayoutParams
                val layoutParams = it.toolbar.layoutParams as AppBarLayout.LayoutParams
                if (value) {
                    layoutParamsForContainer.behavior = FABAwareScrollingViewBehavior()
                    layoutParams.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                    it.appBar.setBackgroundResource(R.color.transparent)
                } else {
                    layoutParamsForContainer.behavior = AppBarLayout.ScrollingViewBehavior()
                    layoutParams.scrollFlags = 0
                    it.appBar.setBackgroundResource(R.color.colorPrimary)
                }
                it.container.layoutParams = layoutParamsForContainer
                it.toolbar.layoutParams = layoutParams
                it.toolbar.forceLayout()
            }
        }

    }

    private fun cleanTempCertPreference() {
        val temporaryClassNames = ArrayList<String>()
        temporaryClassNames.add(ServerSelectionController::class.java.name)
        temporaryClassNames.add(AccountVerificationController::class.java.name)
        temporaryClassNames.add(WebViewLoginController::class.java.name)
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
                if (it is MainActivity && getIsUsingSearchLayout()) {
                    disableKeyboardPersonalisedLearning(it.appBar)
                }
            }
        }

    }

    override fun onAttach(view: View) {
        showSearchOrToolbar()
        setTitle()
        actionBar?.setDisplayHomeAsUpEnabled(parentController != null || router.backstackSize > 1)
        super.onAttach(view)
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
        if (title != null && actionBar != null) {
            actionBar.title = title
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

    open fun getIsUsingSearchLayout(): Boolean = false
    open fun getSearchHint(): String? = null
}
