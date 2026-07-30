<!--
 ~ SPDX-FileCopyrightText: 2021-2026 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# Use push notifications without google play services
This is an example how to use push notifications on a phone without google play services by using the UnifiedPush distributor app Sunup (which uses Mozillas push server).
For other distributors (e.g. self hosted), please follow the documentation at https://unifiedpush.org/users/distributors/

## on server
1. In administration settings, go to Notifications and enable "Allow web push to be used"

## on phone
1. install android talk 24.0.2 or higher (e.g. from f-droid)

2. install UnifiedPush distributor app Sunup
   https://unifiedpush.org/users/distributors/sunup/

3. In the android talk app, go to settings and enable UnifiedPush (This option is only available when a UnifiedPush distributor app is installed). When enabling this, you should see that Android Talk is listed inside the distributor app as a registered application.

4. Please remember to also set the recommended notification options for the talk app (see [notification checklist](https://github.com/nextcloud/talk-android/blob/master/docs/notifications.md))

Your android talk app will now receive push notifications via the Sunup distributor app.
