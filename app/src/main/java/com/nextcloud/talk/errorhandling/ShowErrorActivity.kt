/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.errorhandling

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.core.view.WindowCompat
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShowErrorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_REPORT = "crash_report"
        const val EXTRA_CRASH_TITLE = "crash_title"
        const val EXTRA_CRASH_DIAGNOSIS = "crash_diagnosis"
        const val EXTRA_CRASH_STACKTRACE = "crash_stacktrace"
    }

    private lateinit var crashDiagnosis: String
    private lateinit var mailSubject: String
    private var crashStacktrace: String? = null

    private val saveZipLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) writeZipToUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val crashReport = intent.getStringExtra(EXTRA_CRASH_REPORT) ?: ""
        val crashTitle = intent.getStringExtra(EXTRA_CRASH_TITLE)
        crashDiagnosis = intent.getStringExtra(EXTRA_CRASH_DIAGNOSIS) ?: ""
        crashStacktrace = intent.getStringExtra(EXTRA_CRASH_STACKTRACE)
        val appName = getString(R.string.nc_app_product_name)
        mailSubject = getString(R.string.error_crash_title, appName) + " - " + crashTitle

        setContent {
            MaterialTheme {
                val menuItems = listOf(
                    stringResource(R.string.nc_logs_share) to { sendMail() },
                    stringResource(R.string.nc_logs_download_zip) to { saveZipLauncher.launch("nc_talk_logs.zip") }
                )
                CrashScreen(errorText = crashReport, menuItems = menuItems)
            }
        }
    }

    private fun sendMail() {
        shareLogsAndDiagnosis(
            context = this,
            subject = mailSubject,
            diagnosisText = crashDiagnosis,
            crashInfo = crashStacktrace
        )
    }

    private fun writeZipToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.use { os ->
                saveLogsAsZip(this@ShowErrorActivity, os, crashDiagnosis)
            }
        }
    }
}

@Composable
private fun CrashScreen(errorText: String, menuItems: List<Pair<String, () -> Unit>>) {
    Scaffold(
        topBar = {
            StandardAppBar(
                title = stringResource(R.string.error_crash_title, stringResource(R.string.nc_app_product_name)),
                menuItems = menuItems
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.error_crash_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.nc_logs_advanced_logging_privacy_warning),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = errorText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}
