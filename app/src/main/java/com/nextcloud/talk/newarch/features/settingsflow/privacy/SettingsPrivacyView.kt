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
package com.nextcloud.talk.newarch.features.settingsflow.privacy

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.utils.SecurityUtils
import com.nextcloud.talk.utils.preferences.MagicUserInputModule
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.settings_privacy_view.view.*
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener
import java.util.*


class SettingsPrivacyView(private val bundle: Bundle? = null) : BaseView() {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private var proxyTypeChangeListener: OnPreferenceValueChangedListener<String> = ProxyTypeChangeListener()
    private var proxyCredentialsChangeListener: OnPreferenceValueChangedListener<Boolean> = ProxyCredentialsChangeListener()
    private var screenSecurityChangeListener: OnPreferenceValueChangedListener<Boolean> = ScreenSecurityChangeListener()

    private var screenLockListener: OnPreferenceValueChangedListener<Boolean> = ScreenLockListener()
    private var screenLockTimeoutListener: OnPreferenceValueChangedListener<String?> = ScreenLockTimeoutListener()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        setHasOptionsMenu(true)
        val view = super.onCreateView(inflater, container)

        view.settings_incognito_keyboard.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        view.settings_screen_lock.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        view.settings_screen_lock_timeout.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.settings_screen_lock.setSummary(
                    String.format(
                            Locale.getDefault(),
                            resources!!.getString(R.string.nc_settings_screen_lock_desc),
                            resources!!.getString(R.string.nc_app_name)
                    )
            )

            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val keyguardIsSecure = keyguardManager.isKeyguardSecure
            view.settings_screen_lock.isEnabled = keyguardIsSecure
            view.settings_screen_lock_timeout.isEnabled = keyguardIsSecure

            if (keyguardIsSecure) {
                if (appPreferences.isScreenLocked) {
                    view.settings_screen_lock_timeout.alpha = 1.0f
                } else {
                    view.settings_screen_lock_timeout.alpha = 0.38f
                }

                view.settings_screen_lock.alpha = 1.0f
            } else {
                view.settings_screen_lock.alpha = 0.38f
                view.settings_screen_lock_timeout.alpha = 0.38f
            }
        }

        val listWithIntFields = ArrayList<String>()
        listWithIntFields.add("proxy_port")
        view.privacy_screen.setUserInputModule(MagicUserInputModule(activity, listWithIntFields))

        appPreferences.registerProxyTypeListener(proxyTypeChangeListener)
        appPreferences.registerProxyCredentialsListener(proxyCredentialsChangeListener)
        appPreferences.registerScreenSecurityListener(screenSecurityChangeListener)
        appPreferences.registerScreenLockListener(screenLockListener)
        appPreferences.registerScreenLockTimeoutListener(screenLockTimeoutListener)

        setupProxySection(view)
        return view
    }

    override fun onDestroy() {
        appPreferences.unregisterProxyCredentialsListener(proxyCredentialsChangeListener)
        appPreferences.unregisterProxyTypeListener(proxyTypeChangeListener)
        appPreferences.unregisterScreenSecurityListener(screenSecurityChangeListener)
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
        return R.layout.settings_privacy_view
    }

    override fun getTitle(): String? {
        return resources?.getString(R.string.nc_privacy)
    }

    private fun setupProxySection(view: View?) {
        if ("No proxy" == appPreferences.proxyType || appPreferences.proxyType == null) {
            toggleProxySettingsVisibility(view, false)
        } else {
            toggleCredentialsSettingsVisibility(view, appPreferences.proxyCredentials)
        }
    }

    private fun toggleProxySettingsVisibility(view: View?, shouldBeVisible: Boolean) {
        view?.settings_proxy_host_edit?.isVisible = shouldBeVisible
        view?.settings_proxy_port_edit?.isVisible = shouldBeVisible
        view?.settings_proxy_use_credentials?.isVisible = shouldBeVisible
        if (!shouldBeVisible) {
            appPreferences.setProxyNeedsCredentials(false)
            toggleCredentialsSettingsVisibility(view, shouldBeVisible)
        }

    }

    private fun toggleCredentialsSettingsVisibility(view: View?, shouldBeVisible: Boolean) {
        view?.settings_proxy_username_edit?.isVisible = shouldBeVisible
        view?.settings_proxy_password_edit?.isVisible = shouldBeVisible
    }

    private inner class ProxyCredentialsChangeListener : OnPreferenceValueChangedListener<Boolean> {

        override fun onChanged(newValue: Boolean) {
            toggleCredentialsSettingsVisibility(view, newValue)
            if (!newValue) {
                appPreferences.proxyUsername = ""
                appPreferences.proxyPassword = ""
            }
        }
    }

    private inner class ProxyTypeChangeListener : OnPreferenceValueChangedListener<String> {

        override fun onChanged(newValue: String) {
            if ("No proxy" == newValue) {
                toggleProxySettingsVisibility(view, false)
            } else {
                when (newValue) {
                    "HTTP" -> {
                        view?.settings_proxy_port_edit?.value = "3128"
                    }
                    "DIRECT" -> {
                        view?.settings_proxy_port_edit?.value = "8080"
                    }
                    "SOCKS" -> {
                        view?.settings_proxy_port_edit?.value = "1080"
                    }
                    else -> {
                    }
                }

                toggleProxySettingsVisibility(view, true)
            }
        }
    }


    private inner class ScreenSecurityChangeListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            if (newValue) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }


    private inner class ScreenLockTimeoutListener : OnPreferenceValueChangedListener<String?> {
        override fun onChanged(newValue: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecurityUtils.createKey(appPreferences.screenLockTimeout)
            }
        }
    }

    private inner class ScreenLockListener : OnPreferenceValueChangedListener<Boolean> {
        override fun onChanged(newValue: Boolean) {
            if (newValue) {
                view?.settings_screen_lock_timeout?.alpha = 1.0f
            } else {
                view?.settings_screen_lock_timeout?.alpha = 0.38f
            }
        }
    }
}
