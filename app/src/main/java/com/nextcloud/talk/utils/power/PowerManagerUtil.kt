package com.nextcloud.talk.utils.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleObserver
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R

class PowerManagerUtil : LifecycleObserver {

    @RequiresApi(Build.VERSION_CODES.M)
    fun askForBatteryOptimization(context: Context) {
        if (checkIfBatteryOptimizationEnabled(context)) {
            val alertDialogBuilder = AlertDialog.Builder(context, R.style.Theme_ownCloud_Dialog)
                .setTitle("Battery optimization")
                .setMessage(
                    "Your device may have battery optimization enabled. Notifications work only properly if " +
                        "you exclude this app from it."
                )
                .setPositiveButton("disable") { _, _ ->
                    @SuppressLint("BatteryLife") val intent = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                    )
                    context.startActivity(intent)
                }
                .setNeutralButton("close") { dialog, _ -> dialog.dismiss() }
            alertDialogBuilder.show()
        }
    }

    /**
     * Check if battery optimization is enabled. If unknown, fallback to true.
     *
     * @return true if battery optimization is enabled
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkIfBatteryOptimizationEnabled(context: Context): Boolean {
        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager?
        return when {
            powerManager != null -> !powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)
            else -> true
        }
    }

    companion object {
        private val TAG = PowerManagerUtil::class.simpleName
    }
}
