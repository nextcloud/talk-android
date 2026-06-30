/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.diagnosis

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.UnifiedPushUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class DiagnosisActivity : BaseActivity() {

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var platformPermissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var logsRepository: LogsRepository

    private val diagnosisData = mutableListOf<DiagnosisElement>()
    private val diagnosisDataState = mutableStateOf(emptyList<DiagnosisElement>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val diagnosisViewModel = ViewModelProvider(
            this,
            viewModelFactory
        )[DiagnosisViewModel::class.java]

        val colorScheme = viewThemeUtils.getColorScheme(this)
        val isGooglePlayServicesAvailable = ClosedInterfaceImpl().isGooglePlayServicesAvailable
        val useUnifiedPush = appPreferences.useUnifiedPush
        val useEmbeddedDistrib = UnifiedPushUtils.hasEmbeddedDistributor(context) && !useUnifiedPush

        setContent {
            val backgroundColor = colorResource(id = R.color.bg_default)

            val menuItems = listOf(
                stringResource(R.string.nc_common_copy) to { copyToClipboard(diagnosisData.toMarkdown()) }
            )

            MaterialTheme(
                colorScheme = colorScheme
            ) {
                val isOnline = networkMonitor.isOnline.collectAsState().value
                ColoredStatusBar()
                Scaffold(
                    modifier = Modifier
                        .statusBarsPadding()
                        .displayCutoutPadding(),
                    topBar = {
                        StandardAppBar(
                            title = stringResource(R.string.nc_settings_diagnosis_title),
                            menuItems
                        )
                    },
                    content = { paddingValues ->
                        val viewState = diagnosisViewModel.notificationViewState.collectAsState().value

                        Column(
                            Modifier
                                .background(backgroundColor)
                                .padding(
                                    0.dp,
                                    paddingValues.calculateTopPadding(),
                                    0.dp,
                                    paddingValues.calculateBottomPadding()
                                )
                                .fillMaxSize()
                        ) {
                            DiagnosisContentComposable(
                                diagnosisDataState,
                                isLoading = diagnosisViewModel.isLoading.value,
                                showDialog = diagnosisViewModel.showDialog.value,
                                viewState = viewState,
                                onTestPushClick = { diagnosisViewModel.fetchTestPushResult() },
                                onDismissDialog = { diagnosisViewModel.dismissDialog() },
                                showTestPushButton = isGooglePlayServicesAvailable ||
                                    useUnifiedPush ||
                                    useEmbeddedDistrib,
                                isOnline = isOnline
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.show()

        diagnosisData.clear()
        diagnosisData.addAll(
            buildDiagnosisElements(
                this,
                userManager,
                appPreferences,
                arbitraryStorageManager,
                logsRepository
            )
        )
        diagnosisDataState.value = diagnosisData.toList()
    }

    private fun copyToClipboard(text: String) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(
            resources?.getString(R.string.nc_app_product_name),
            text
        )
        clipboardManager.setPrimaryClip(clipData)

        Toast.makeText(
            context,
            context.resources.getString(R.string.nc_common_copy_success),
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        val TAG = DiagnosisActivity::class.java.simpleName
    }
}
