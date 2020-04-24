/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.nextcloud.talk.newarch.features.settingsflow.looknfeel

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.RingtoneSelectionController
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.utils.DoNotDisturbUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.settings_looknfeel_view.view.*
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import java.io.IOException

class SettingsLookNFeelView(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)
    private var themeChangeListener: OnPreferenceValueChangedListener<String> = ThemeChangeListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        setHasOptionsMenu(true)
        appPreferences.registerThemeChangeListener(themeChangeListener)
        val view = super.onCreateView(inflater, container)

        view.settings_call_sound.setOnClickListener { v ->
            showRingtoneSelectionScreen(true)
        }

        view.settings_message_sound.setOnClickListener { v ->
            showRingtoneSelectionScreen(false)
        }

        view.settings_always_vibrate.isVisible = DoNotDisturbUtils.hasVibrator()

        return view
    }

    override fun onAttach(view: View) {
        super.onAttach(view)

        var ringtoneName = ""
        var ringtoneSettings: RingtoneSettings

        if (!TextUtils.isEmpty(appPreferences.callRingtoneUri)) {
            try {
                ringtoneSettings =
                        LoganSquare.parse(appPreferences.callRingtoneUri, RingtoneSettings::class.java)
                ringtoneName = ringtoneSettings.ringtoneName
            } catch (e: IOException) {
            }

            view.settings_call_sound.setSummary(ringtoneName)
        } else {
            view.settings_call_sound.setSummary(R.string.nc_settings_default_ringtone)
        }

        ringtoneName = ""

        if (!TextUtils.isEmpty(appPreferences.messageRingtoneUri)) {
            try {
                ringtoneSettings =
                        LoganSquare.parse(appPreferences.messageRingtoneUri, RingtoneSettings::class.java)
                ringtoneName = ringtoneSettings.ringtoneName
            } catch (e: IOException) {
            }

            view.settings_message_sound.setSummary(ringtoneName)
        } else {
            view.settings_message_sound.setSummary(R.string.nc_settings_default_ringtone)
        }
    }

    override fun onDestroyView(view: View) {
        appPreferences.unregisterThemeChangeListener(themeChangeListener)
        super.onDestroyView(view)
    }

    private fun showRingtoneSelectionScreen(callSounds: Boolean) {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_ARE_CALL_SOUNDS, callSounds)
        router.pushController(
                RouterTransaction.with(RingtoneSelectionController(bundle))
                        .pushChangeHandler(HorizontalChangeHandler())
                        .popChangeHandler(HorizontalChangeHandler())
        )
    }

    override fun onDestroy() {
        appPreferences.unregisterThemeChangeListener(themeChangeListener)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            router.popController(this)
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun getLayoutId(): Int {
        return R.layout.settings_looknfeel_view
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_look_and_feel)
    }

    private inner class ThemeChangeListener : OnPreferenceValueChangedListener<String> {
        override fun onChanged(newValue: String) {
            NextcloudTalkApplication.setAppTheme(newValue)
            activity?.recreate()
        }
    }
}
