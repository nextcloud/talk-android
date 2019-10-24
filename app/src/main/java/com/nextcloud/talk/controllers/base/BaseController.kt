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
import autodagger.AutoInjector
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.AccountVerificationController
import com.nextcloud.talk.controllers.ServerSelectionController
import com.nextcloud.talk.controllers.SwitchAccountController
import com.nextcloud.talk.controllers.WebViewLoginController
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import org.greenrobot.eventbus.EventBus
import org.koin.android.ext.android.inject
import java.util.ArrayList

@AutoInjector(NextcloudTalkApplication::class)
abstract class BaseController : ButterKnifeController(), ComponentCallbacks {

  val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)

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

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        router.popCurrentController()
        return true
      }
      else -> return super.onOptionsItemSelected(item)
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
    }
  }

  override fun onAttach(view: View) {
    super.onAttach(view)

    setTitle()
    if (actionBar != null) {
      actionBar!!.setDisplayHomeAsUpEnabled(parentController != null || router.backstackSize > 1)
    }
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
}
