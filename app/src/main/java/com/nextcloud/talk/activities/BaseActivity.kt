/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.transition.Slide
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.SslErrorHandler
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.events.CertificateEvent
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.ssl.TrustManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.security.cert.CertificateParsingException
import java.security.cert.X509Certificate
import java.text.DateFormat
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
open class BaseActivity : AppCompatActivity() {

    enum class AppBarLayoutType {
        TOOLBAR, SEARCH_BAR, EMPTY
    }

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

    open val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.TOOLBAR

    open val view: View?
        get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.enterTransition = Slide(Gravity.END)
        window.exitTransition = Slide(Gravity.START)
        super.onCreate(savedInstanceState)
    }

    public override fun onStart() {
        super.onStart()
        eventBus.register(this)
    }

    public override fun onResume() {
        super.onResume()

        if (appPreferences.isScreenSecured) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    public override fun onStop() {
        super.onStop()
        eventBus.unregister(this)
    }

    fun setupSystemColors() {
        colorizeStatusBar()
        colorizeNavigationBar()
    }

    open fun colorizeStatusBar() {
        if (resources != null) {
            if (appBarLayoutType == AppBarLayoutType.SEARCH_BAR) {
                viewThemeUtils.platform.resetStatusBar(this)
            } else {
                viewThemeUtils.platform.themeStatusBar(this)
            }
        }
    }

    fun colorizeNavigationBar() {
        if (resources != null) {
            DisplayUtils.applyColorToNavigationBar(
                this.window,
                ResourcesCompat.getColor(resources, R.color.bg_default, null)
            )
        }
    }

    fun showCertificateDialog(
        cert: X509Certificate,
        trustManager: TrustManager,
        sslErrorHandler: SslErrorHandler?
    ) {
        val formatter = DateFormat.getDateInstance(DateFormat.LONG)
        val validFrom = formatter.format(cert.notBefore)
        val validUntil = formatter.format(cert.notAfter)

        val issuedBy = cert.issuerDN.toString()
        val issuedFor: String

        try {
            if (cert.subjectAlternativeNames != null) {
                val stringBuilder = StringBuilder()
                for (o in cert.subjectAlternativeNames) {
                    val list = o as List<*>
                    val type = list[0] as Int
                    if (type == 2) {
                        val name = list[1] as String
                        stringBuilder.append("[").append(type).append("]").append(name).append(" ")
                    }
                }
                issuedFor = stringBuilder.toString()
            } else {
                issuedFor = cert.subjectDN.name
            }

            @SuppressLint("StringFormatMatches")
            val dialogText = String.format(
                resources.getString(R.string.nc_certificate_dialog_text),
                issuedBy,
                issuedFor,
                validFrom,
                validUntil
            )

            val dialogBuilder = MaterialAlertDialogBuilder(this)
                .setIcon(viewThemeUtils.dialog.colorMaterialAlertDialogIcon(context, R.drawable.ic_security_white_24dp))
                .setTitle(R.string.nc_certificate_dialog_title)
                .setMessage(dialogText)
                .setPositiveButton(R.string.nc_yes) { _, _ ->
                    trustManager.addCertInTrustStore(cert)
                    sslErrorHandler?.proceed()
                }
                .setNegativeButton(R.string.nc_no) { _, _ ->
                    sslErrorHandler?.cancel()
                }

            viewThemeUtils.dialog.colorMaterialAlertDialogBackground(context, dialogBuilder)

            val dialog = dialogBuilder.show()

            viewThemeUtils.platform.colorTextButtons(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            )
        } catch (e: CertificateParsingException) {
            Log.d(TAG, "Failed to parse the certificate")
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: CertificateEvent) {
        showCertificateDialog(event.x509Certificate, event.magicTrustManager, event.sslErrorHandler)
    }

    companion object {
        private val TAG = "BaseActivity"
    }
}
