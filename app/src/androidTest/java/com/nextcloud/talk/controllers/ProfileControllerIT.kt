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

package com.nextcloud.talk.controllers

import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextcloud.talk.R
import com.nextcloud.talk.test.BaseIT
import com.nextcloud.talk.test.ControllerTestActivity
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileControllerIT : BaseIT() {

    @Test
    @Suppress("Detekt.MagicNumber")
    fun testClickEdit() {
        launchActivity<ControllerTestActivity>().use { scenario ->

            val controller = ProfileController()

            scenario.onActivity { activity ->
                activity.setController(controller)
            }
            Thread.sleep(2000) // TODO find a workaround for waiting until controller is set

            // editing options not visible on launch
            onView(withId(R.id.avatar_buttons)).check(matches(not(isDisplayed())))

            onView(withId(R.id.edit)).perform(click())

            // editing options now visible
            onView(withId(R.id.avatar_buttons)).check(matches(isDisplayed()))
        }
    }
}
