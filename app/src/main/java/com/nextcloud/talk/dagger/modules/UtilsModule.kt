/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import android.content.Context
import com.nextcloud.talk.logger.FileLogHandler
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.logger.LoggerImpl
import com.nextcloud.talk.logger.LogsRepository
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtilImpl
import dagger.Module
import dagger.Provides
import dagger.Reusable
import java.io.File
import javax.inject.Singleton

@Module(includes = [ContextModule::class])
class UtilsModule {
    @Provides
    @Reusable
    fun providePermissionUtil(context: Context): PlatformPermissionUtil = PlatformPermissionUtilImpl(context)

    @Provides
    @Reusable
    fun provideDateUtils(context: Context): DateUtils = DateUtils(context)

    @Provides
    @Reusable
    fun provideMessageUtils(context: Context): MessageUtils = MessageUtils(context)

    @Provides
    @Singleton
    fun provideLoggerImpl(context: Context): LoggerImpl {
        val logDir = File(context.filesDir, LOG_DIR_NAME)
        val handler = FileLogHandler(logDir, LOG_FILE_NAME, maxSize = LOG_FILE_MAX_SIZE)
        val impl = LoggerImpl(handler = handler)
        val savedLevelName = context
            .getSharedPreferences(LoggerImpl.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LoggerImpl.PREF_LOG_LEVEL, null)
        impl.minimumLevel = savedLevelName?.let { name -> Level.entries.find { it.name == name } }
            ?: LoggerImpl.DEFAULT_LEVEL
        impl.start()
        return impl
    }

    @Provides
    @Singleton
    fun provideLogger(impl: LoggerImpl): Logger = impl

    @Provides
    @Singleton
    fun provideLogsRepository(impl: LoggerImpl): LogsRepository = impl

    companion object {
        const val LOG_DIR_NAME = "logs"
        const val LOG_FILE_NAME = "nc_talk_log.txt"
        private const val LOG_FILE_MAX_SIZE = 1_000_000L // 1 MB per file, 4 files max = 4 MB
    }
}
