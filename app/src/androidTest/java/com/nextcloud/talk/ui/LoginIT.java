/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger
 * Copyright (C) 2020 Tobias Kaminsky
 * Copyright (C) 2020 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.nextcloud.talk.ui;

import android.os.Bundle;

import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MainActivity;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Objects;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.web.webdriver.DriverAtoms;
import androidx.test.espresso.web.webdriver.Locator;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.web.sugar.Web.onWebView;
import static androidx.test.espresso.web.webdriver.DriverAtoms.findElement;
import static androidx.test.espresso.web.webdriver.DriverAtoms.webClick;
import static org.junit.Assert.assertEquals;


//@LargeTest
@Ignore("This test is ignored because it constantly fails on CI")
public class LoginIT {

    @Test
    public void login() throws InterruptedException {

        ActivityScenario<MainActivity> activityScenario = ActivityScenario.launch(MainActivity.class);

        Bundle arguments = androidx.test.platform.app.InstrumentationRegistry.getArguments();

        String baseUrl = arguments.getString("TEST_SERVER_URL");
        String loginName = arguments.getString("TEST_SERVER_USERNAME");
        String password = arguments.getString("TEST_SERVER_PASSWORD");

        Thread.sleep(2000);

        try {
            onView(withId(R.id.serverEntryTextInputEditText)).check(matches(isDisplayed()));
        } catch (NoMatchingViewException e) {
            try {
                // can happen that an invalid account from previous tests is existing
                onView(withText(R.string.nc_settings_remove_account)).perform(click());
                Thread.sleep(2000);
            } catch (NoMatchingViewException ie) {
                // is OK if the dialog is not shown
            }

            try {
                // Delete account if exists
                onView(withId(R.id.switch_account_button)).perform(click());
                onView(withId(R.id.manage_settings)).perform(click());
                onView(withId(R.id.settings_remove_account)).perform(click());
                onView(withText(R.string.nc_settings_remove)).perform(click());
                // The remove button must be clicked two times
                onView(withId(R.id.settings_remove_account)).perform(click());
                // And yes: The button must be clicked two times
                onView(withText(R.string.nc_settings_remove)).perform(click());
                onView(withText(R.string.nc_settings_remove)).perform(click());
            } catch (Exception ie2) {
                // ignore
            } finally {
                Thread.sleep(2000);
            }

        }

        onView(withId(R.id.serverEntryTextInputEditText)).perform(typeText(baseUrl));
        // Click on EditText's drawable right
        onView(withContentDescription(R.string.nc_server_connect)).perform(click());

        Thread.sleep(4000);

        onWebView().forceJavascriptEnabled();

        // click on login
        onWebView()
            .withElement(findElement(Locator.XPATH, "//p[@id='redirect-link']/a"))
            .perform(webClick());

        // username
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@id='user']"))
            .perform(DriverAtoms.webKeys(loginName));

        // password
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@id='password']"))
            .perform(DriverAtoms.webKeys(password));

        // click login
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@id='submit-form']"))
            .perform(webClick());

        Thread.sleep(2000);

        // grant access
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@type='submit']"))
            .perform(webClick());

        Thread.sleep(5 * 1000);

        onView(withId(R.id.switch_account_button)).perform(click());
        onView(withId(R.id.user_name)).check(matches(withText("User One")));

        activityScenario.onActivity(activity -> {
            assertEquals(loginName,
                         Objects.requireNonNull(activity.userManager.getCurrentUser().blockingGet()).getUserId());
        });
    }
}
