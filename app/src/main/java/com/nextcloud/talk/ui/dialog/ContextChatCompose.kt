/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ContextChatCompose(val bundle: Bundle) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var dataSource: ChatNetworkDataSource

    @Inject
    lateinit var userManager: UserManager

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Composable
    fun GetDialogView(shouldDismiss: MutableState<Boolean>, context: Context) {
        if (shouldDismiss.value) {
            return
        }

        val colorScheme = viewThemeUtils.getColorScheme(context)
        MaterialTheme { // Note: using theme makes this hard to see. Should be better when I get adapter setup
            Dialog(
                onDismissRequest = {
                    shouldDismiss.value = true
                },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.animateContentSize()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        val user = userManager.currentUser.blockingGet()
                        if (!user.hasSpreedFeatureCapability("chat-get-context") ||
                            !user.hasSpreedFeatureCapability("federation-v1")
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                "Info Icon",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Text(
                                "Capabilities not found",
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            LaunchedEffect(Dispatchers.IO) {
                                val credentials = bundle.getString(BundleKeys.KEY_CREDENTIALS)
                                val baseUrl = bundle.getString(BundleKeys.KEY_BASE_URL)
                                val token = bundle.getString(BundleKeys.KEY_ROOM_TOKEN)
                                val messageId = bundle.getString(BundleKeys.KEY_MESSAGE_ID)
                                val limit = 10 // actual size is 2*limit, goes both ways

                                val messages = dataSource.getContextForChatMessage(
                                    credentials!!,
                                    baseUrl!!,
                                    token!!,
                                    messageId!!,
                                    limit
                                )

                                // TODO put messages in adapter
                            }
                        }
                    }
                }
            }
        }
    }
}
