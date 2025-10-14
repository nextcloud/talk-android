<!--
 ~ SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# Debugging push notifications

This list is intended to help users that have problems to receive talk notifications on their android phone. It may 
not be complete. Please contribute to this list as you gain new knowledge. Just create an issue with the 
"notification" label or create a pull request for this document. 

## 📱 Users
- Please make sure to install the app from the Google PlayStore. **The f-droid version doesn't support push 
  notifications.**
  
  [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
  alt="Download from Google Play"
  height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.talk2)

- Only talk notifications will be delivered by the Talk app, for all other notifications install the Nextcloud Files 
  app from Google PlayStore.
  
  [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
  alt="Download from Google Play"
  height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.client)

If your problem still occurs after checking all these hints, create an issue at https://github.com/nextcloud/talk-android/issues

### 🤖 Check android settings

First of all, please make sure that the following requirements are met:

- Check that your phone has internet access

- Check that your phone is not in "do not disturb" mode

#### Grant permissions on install

After the installation of Nextcloud Talk Android, dialogs appear asking you to grant the permissions.
Please note that the dialogs only appear once to respect the first decision of the users.
Depending on the Android version, only the dialogs appear that are necessary.

##### Grant notification permission

A dialog to enable notification permissions after install will only be shown for Android 13 and upwards. For older 
Android versions notifications have to be enabled in the AppInfo settings.

![Grant notification permission after install](/docs/grantNotificationPermissionAfterInstall.png "Grant notification permission after install")

##### Ignore battery optimization

For some smartphone models, ignoring battery optimization is not necessary while for others it is. If you absolutely want to be sure notifications are received, turning it off is highly recommended.

![Ignore battery optimization dialog](/docs/ignoreBatteryOptimizationDialog.png "Ignore battery optimization dialog")

Please follow the description to turn off battery optimization for the Talk app. In the available apps you can use the search so you don't have to scroll through the whole app list.
The android settings may look different depending on manufacturer and android version. Below are two examples how it may look like:

Example 1:
- Search app in list. 
  ![Ignore battery optimization - select all apps](/docs/ignoreBatteryOptimizationSelectAllApps.png "Ignore battery optimization - select all apps")
- Please note that the switch has to be turned off.
  ![Ignore battery optimization - turn off switch](/docs/ignoreBatteryOptimizationTurnOffSwitch.png "Ignore battery optimization - turn off switch")

Example 2:
- Select app from list.
- Please allow background usage. The switch has to be *turned on*. Also, please click on the text to reach the next menu.
  ![Allow background usage. Turn on switch and click on text](/docs/ignoreBatteryOptimization_newerAndroid_allowBackgroundUsage.png "Allow background usage. Turn on switch and click on text")
- Set the background usage to "unrestricted".
  ![Set unrestricted background usage](/docs/ignoreBatteryOptimization_newerAndroid_unrestricted.png "Set unrestricted background usage")

#### Grant permissions in settings

##### Regular warning

If notifications settings are not set up correctly, there will be a warning in the conversation list. This is displayed regularly unless it is changed in the settings so that you are not reminded of incorrect settings.
If you select "Not now", the warning will be shown again after a few days if the settings are still wrong. When 
selecting "Settings", the settings screen will appear. All incorrect settings will blink and their description is in 
red color. 

##### Notification settings

The notification settings can either be reached when selecting "Settings" in the regular notification warning or by 
clicking on the user avatar in the right corner of the conversation list and by selecting "Settings".

In the "Notifications" section, please change every setting that is marked with a red color.
Please take into account that the android settings might look different for each manufacturer. 
You can also reach these settings by long pressing on the Nextcloud Android Talk App in Android itself and by selecting 
"App info".

![Warning that notifications are not set up correctly](/docs/notificationsNotSetUpCorrectlyWarning.png "Warning that notifications are not set up correctly")

![Notification settings](/docs/notificationSettingsExample.png "Notification settings")

##### Further help for permission settings

If setting the above listed permission won't help, it might be that there are special settings for your phone. It 
might be worth it to check what other messaging apps recommend to get their apps running on a certain smartphone and adapt this to the talk app.
Also [https://dontkillmyapp.com/](https://dontkillmyapp.com/) might be good starting point.

### 🗨️ Check conversation settings
- In the conversation settings (in the upper right corner of a conversation), check that notifications are set to 
  "Always notify" or "Notify when mentioned"

	- Be aware that this is a per conversation setting. Set it for every conversation differently depending on your 
      needs.

- Also be aware that notifications are not generated when you have an active session for a conversation. This also 
  applies for browser tabs that are open in the background, etc.

### 🖥 Check server settings

Run the `notification:test-push` command for the user who is logged in at the device that should receive the notification:

```bash
sudo -u www-data php /var/www/yourinstance/occ notification:test-push --talk youruser
```
Alternatively, you can check if push notifications are set up correctly on the server from the app’s Diagnosis screen:

- Select the user avatar in the upper right corner of the conversation list
- Select "Settings" from the menu.
- In "Advanced" section of settings menu, select "Diagnosis"
- Click on “Test push notifications” button located at the bottom of the Diagnosis.

If google play services are not available on the device, then you cannot see "Test push notifications" button in the Diagnosis screen.

It should print something like the following:
```
Trying to push to 2 devices
  
Language is set to en
Private user key size: 1704
Public user key size: 451
Identified 1 Talk devices and 1 others.

Device token:156850
Device public key size: 451
Data to encrypt is: {"nid":525210,"app":"admin_notification_talk","subject":"Testing push notifications","type":"admin_notifications","id":"614aeee4"}
Signed encrypted push subject
Push notification sent successfully
```
This means the notifications are set up correctly on server side. A notification should be displayed on the device. 
If there is no notification shown on the device, please focus on the settings of the talk android app.

If it prints something like
```
sudo -u www-data php /var/www/yourinstance/occ notification:test-push --talk youruser
No devices found for user
```
try to remove the account from the Nextcloud Android Talk app and log in again. Afterwards try to run the command 
again.
 
If it prints
```
There are no commands defined in the "notification" namespace. 
```
then the https://github.com/nextcloud/notifications app is not installed on your nextcloud instance.
The notification app is shipped and enabled by default, but could be missing in development environments or being disabled manually.
Install and enable the app by following the instructions at https://github.com/nextcloud/notifications#developers and 
try again to execute the command.

## 🦺 Developers/testers
- Be aware that the "qa"-versions that you can install by scanning the QR-code in a github pull request don't 
  support notifications!

- When starting the talk app within Android Studio, make sure to select the "gplayDebug" build variant:
  ![gplay debug build variant](/docs/gplayDebugBuildVariant.png "gplay debug build variant")

- Especially after reinstalling the app, make sure to always check the android settings as they might be reset.
