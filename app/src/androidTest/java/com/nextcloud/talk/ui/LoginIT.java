/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2020 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
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
                         Objects.requireNonNull(activity.currentUserProvider.getCurrentUser().blockingGet()).getUserId());
        });
    }
}
