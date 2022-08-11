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

package com.nextcloud.talk.ui.theme

import com.nextcloud.talk.dagger.modules.ContextModule
import com.nextcloud.talk.utils.database.user.UserModule
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.Reusable

@Module(includes = [ContextModule::class, UserModule::class])
internal abstract class ThemeModule {

    @Binds
    @Reusable
    abstract fun bindMaterialSchemesProvider(provider: MaterialSchemesProviderImpl): MaterialSchemesProvider

    companion object {
        @Provides
        fun provideCurrentMaterialSchemes(themeProvider: MaterialSchemesProvider): MaterialSchemes {
            return themeProvider.getMaterialSchemesForCurrentUser()
        }
    }
}
