/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.account.AccountVerificationActivity
import com.nextcloud.talk.account.ServerSelectionActivity
import com.nextcloud.talk.account.SwitchAccountActivity
import com.nextcloud.talk.account.WebViewLoginActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.events.CertificateEvent
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.FileViewerUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.adjustUIForAPILevel35
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
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
        TOOLBAR,
        SEARCH_BAR,
        EMPTY
    }

    @Inject
    lateinit var eventBus: EventBus

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    open val appBarLayoutType: AppBarLayoutType
        get() = AppBarLayoutType.TOOLBAR

    open val view: View?
        get() = null

    override fun onCreate(savedInstanceState: Bundle?) {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        adjustUIForAPILevel35()
        super.onCreate(savedInstanceState)

        cleanTempCertPreference()
    }

    public override fun onStart() {
        super.onStart()
        eventBus.register(this)
    }

    public override fun onResume() {
        super.onResume()

        if (appPreferences.isKeyboardIncognito) {
            val viewGroup = (findViewById<View>(android.R.id.content) as ViewGroup).getChildAt(0) as ViewGroup
            disableKeyboardPersonalisedLearning(viewGroup)
        }

        if (appPreferences.isScreenSecured || appPreferences.isScreenLocked) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    public override fun onStop() {
        super.onStop()
        eventBus.unregister(this)
    }

    /*
     * May be aligned with android-common lib in the future: .../ui/util/extensions/AppCompatActivityExtensions.kt
     */
    fun initSystemBars() {
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                val statusBarHeight = insets.getInsets(WindowInsets.Type.statusBars()).top
                view.setPadding(0, statusBarHeight, 0, 0)
                val color = ResourcesCompat.getColor(resources, R.color.bg_default, context.theme)
                view.setBackgroundColor(color)
            } else {
                colorizeStatusBar()
                colorizeNavigationBar()
            }
            insets
        }
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

    @Suppress("Detekt.NestedBlockDepth")
    private fun showCertificateDialog(
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

            val dialogBuilder = MaterialAlertDialogBuilder(this).setIcon(
                viewThemeUtils.dialog.colorMaterialAlertDialogIcon(
                    context,
                    R.drawable.ic_security_white_24dp
                )
            ).setTitle(R.string.nc_certificate_dialog_title)
                .setMessage(dialogText)
                .setPositiveButton(R.string.nc_yes) { _, _ ->
                    trustManager.addCertInTrustStore(cert)
                    sslErrorHandler?.proceed()
                }.setNegativeButton(R.string.nc_no) { _, _ ->
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

    private fun cleanTempCertPreference() {
        val temporaryClassNames: MutableList<String> = ArrayList()
        temporaryClassNames.add(ServerSelectionActivity::class.java.name)
        temporaryClassNames.add(AccountVerificationActivity::class.java.name)
        temporaryClassNames.add(WebViewLoginActivity::class.java.name)
        temporaryClassNames.add(SwitchAccountActivity::class.java.name)
        if (!temporaryClassNames.contains(javaClass.name)) {
            appPreferences.removeTemporaryClientCertAlias()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: CertificateEvent) {
        showCertificateDialog(event.x509Certificate, event.trustManager, event.sslErrorHandler)
    }

    override fun startActivity(intent: Intent) {
        val user = currentUserProvider.currentUser.blockingGet()
        if (intent.data != null && TextUtils.equals(intent.action, Intent.ACTION_VIEW)) {
            val uri = intent.data.toString()
            if (user?.baseUrl != null && uri.startsWith(user.baseUrl!!)) {
                if (UriUtils.isInstanceInternalFileShareUrl(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/f/41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileShareFileId(uri))
                } else if (UriUtils.isInstanceInternalFileUrl(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileFileId(uri))
                } else if (UriUtils.isInstanceInternalFileUrlNew(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileFileIdNew(uri))
                } else if (UriUtils.isInstanceInternalTalkUrl(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/call/123456789
                    val bundle = Bundle()
                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, UriUtils.extractRoomTokenFromTalkUrl(uri))
                    val chatIntent = Intent(context, ChatActivity::class.java)
                    chatIntent.putExtras(bundle)
                    chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(chatIntent)
                } else {
                    super.startActivity(intent)
                }
            } else {
                super.startActivity(intent)
            }
        } else {
            super.startActivity(intent)
        }
    }

    companion object {
        private val TAG = BaseActivity::class.java.simpleName
    }
}
