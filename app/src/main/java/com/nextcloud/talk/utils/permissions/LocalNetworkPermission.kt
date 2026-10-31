/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * From Android 17 on, an app targeting API 37 needs ACCESS_LOCAL_NETWORK to reach the local network. Without it a
 * request to a server on the LAN doesn't fail, it times out, so ask before talking to such a server.
 *
 * Create it as a property of the activity, so the permission launcher is registered before the activity starts.
 */
class LocalNetworkPermission(private val activity: AppCompatActivity) {

    private var onDone: ((Boolean) -> Unit)? = null
    private var viewThemeUtils: ViewThemeUtils? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            finish(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            showDeniedDialog()
        } else {
            finish(false)
        }
    }

    /**
     * Calls [onDone] with true when [serverUrl] can be reached: the permission isn't needed, is granted, or the server
     * isn't on the local network. With false when the user declined.
     */
    fun ensure(serverUrl: String, viewThemeUtils: ViewThemeUtils, onDone: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN || isGranted()) {
            onDone(true)
        } else {
            requestIfLocal(serverUrl, viewThemeUtils, onDone)
        }
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun requestIfLocal(serverUrl: String, viewThemeUtils: ViewThemeUtils, onDone: (Boolean) -> Unit) {
        activity.lifecycleScope.launch {
            if (withContext(Dispatchers.IO) { isLocalServer(serverUrl) }) {
                this@LocalNetworkPermission.onDone = onDone
                this@LocalNetworkPermission.viewThemeUtils = viewThemeUtils
                launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                onDone(true)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
    private fun showDeniedDialog() {
        if (activity.isFinishing || activity.isDestroyed) {
            finish(false)
            return
        }
        // After the second denial the system no longer shows its prompt, only the app settings can grant it
        val canAskAgain = activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)
        val positiveText = if (canAskAgain) R.string.nc_permissions_ask else R.string.nc_permissions_settings
        var retried = false
        val dialogBuilder = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.nc_local_network_permission_title)
            .setMessage(R.string.nc_local_network_permission_message)
            .setPositiveButton(positiveText) { _, _ ->
                if (canAskAgain) {
                    retried = true
                    launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", activity.packageName, null)
                    activity.startActivity(intent)
                }
            }
            .setNegativeButton(R.string.nc_common_dismiss, null)
            .setOnDismissListener {
                if (!retried) {
                    finish(false)
                }
            }
        viewThemeUtils?.dialog?.colorMaterialAlertDialogBackground(activity, dialogBuilder)
        dialogBuilder.show()
    }

    private fun finish(granted: Boolean) {
        val callback = onDone
        onDone = null
        viewThemeUtils = null
        callback?.invoke(granted)
    }

    companion object {
        private val TAG = LocalNetworkPermission::class.java.simpleName
        private const val BYTE_MASK = 0xFF
        private const val CGNAT_FIRST_OCTET = 100
        private const val CGNAT_SECOND_OCTET_MASK = 0xC0
        private const val CGNAT_SECOND_OCTET = 0x40
        private const val ULA_MASK = 0xFE
        private const val ULA_PREFIX = 0xFC

        /**
         * Whether [serverUrl] points to the local network as Android 17 defines it: a `.local` name, or a host that
         * resolves to a private, link-local or CGNAT IPv4 address or to a link-local or unique local IPv6 address.
         * Resolves the host, so call it off the main thread.
         */
        fun isLocalServer(serverUrl: String): Boolean {
            val host = runCatching { URI(serverUrl).host }.getOrNull() ?: return false
            return host.endsWith(".local", ignoreCase = true) ||
                try {
                    InetAddress.getAllByName(host).any(::isLocalAddress)
                } catch (e: UnknownHostException) {
                    // The server check reports an unknown host itself
                    Log.d(TAG, "Could not resolve $host", e)
                    false
                }
        }

        fun isLocalAddress(address: InetAddress): Boolean {
            if (address.isSiteLocalAddress || address.isLinkLocalAddress) {
                return true
            }
            val bytes = address.address
            val first = bytes[0].toInt() and BYTE_MASK
            return when (address) {
                is Inet4Address ->
                    first == CGNAT_FIRST_OCTET &&
                        (bytes[1].toInt() and CGNAT_SECOND_OCTET_MASK) == CGNAT_SECOND_OCTET
                is Inet6Address -> (first and ULA_MASK) == ULA_PREFIX
                else -> false
            }
        }
    }
}
