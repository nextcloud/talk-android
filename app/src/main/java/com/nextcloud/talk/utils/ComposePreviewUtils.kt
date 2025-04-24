/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.MaterialSchemes
import com.nextcloud.android.common.ui.theme.utils.AndroidViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.AndroidXViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.DialogViewThemeUtils
import com.nextcloud.android.common.ui.theme.utils.MaterialViewThemeUtils
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.ui.theme.MaterialSchemesProviderImpl
import com.nextcloud.talk.ui.theme.TalkSpecificViewThemeUtils
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderImpl
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.preferences.AppPreferencesImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * TODO - basically a reimplementation of common dependencies for use in Previewing Advanced Compose Views
 * It's a hard coded Dependency Injector
 *
 */
object ComposePreviewUtils {

    @OptIn(ExperimentalCoroutinesApi::class)
    val appPreferences: AppPreferences
        @Composable
        get() = AppPreferencesImpl(LocalContext.current)

    val usersDao: UsersDao
        @Composable
        get() = TalkDatabase.getInstance(LocalContext.current, appPreferences).usersDao()

    val userRepository: UsersRepository
        @Composable
        get() = UsersRepositoryImpl(usersDao)

    val userManager: UserManager
        @Composable
        get() = UserManager(userRepository)

    val userProvider: CurrentUserProviderNew
        @Composable
        get() = CurrentUserProviderImpl(userManager)

    val colorUtil: ColorUtil
        @Composable
        get() = ColorUtil(LocalContext.current)

    val materialScheme: MaterialSchemes
        @Composable
        get() = MaterialSchemesProviderImpl(userProvider, colorUtil).getMaterialSchemesForCurrentUser()

    @OptIn(ExperimentalCoroutinesApi::class)
    val viewThemeUtils: ViewThemeUtils
        @Composable
        get() {
            val android = AndroidViewThemeUtils(materialScheme, colorUtil)
            val material = MaterialViewThemeUtils(materialScheme, colorUtil)
            val androidx = AndroidXViewThemeUtils(materialScheme, android)
            val talk = TalkSpecificViewThemeUtils(materialScheme, androidx)
            val dialog = DialogViewThemeUtils(materialScheme)
            return ViewThemeUtils(materialScheme, android, material, androidx, talk, dialog)
        }

    val messageUtils: MessageUtils
        @Composable
        get() = MessageUtils(LocalContext.current)
}
