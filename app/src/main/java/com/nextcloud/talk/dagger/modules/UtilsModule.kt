/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.dagger.modules

import android.content.Context
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtilImpl
import dagger.Module
import dagger.Provides
import dagger.Reusable

@Module(includes = [ContextModule::class])
class UtilsModule {
    @Provides
    @Reusable
    fun providePermissionUtil(context: Context): PlatformPermissionUtil {
        return PlatformPermissionUtilImpl(context)
    }

    @Provides
    @Reusable
    fun provideDateUtils(context: Context): DateUtils {
        return DateUtils(context)
    }
}
