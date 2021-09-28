# Debugging push notifications

This list is intended to help users that have problems to receive talk notifications on their android phone. It may 
not be complete. Please contribute to this list as you gain new knowledge. Just create an issue with the 
"notification" label or create a pull request for this document. 

# 📱 Users
- Please make sure to install the app from the Google PlayStore. The f-droid version doesn't support push notifications.
  
  [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
  alt="Download from Google Play"
  height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.talk2)
- Only talk notifications will be delivered by the Talk app, for all other notifications install the Nextcloud Files 
  app from Google PlayStore.
  
  [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
  alt="Download from Google Play"
  height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.client)

If your problem still occurs after checking all these hints, create an issue at https://github.com/nextcloud/talk-android/issues

## 🤖 Check android settings

Please take into account that the android settings might be different for each manufacturer. It might be worth it to check what other messaging apps recommend to get their apps running on a certain smartphone and adapt this to the talk app.

- Check that your phone is not in "do not disturb" mode
- Check that your phone has internet access
- Check the android settings like **"energy saving"** and **"notifications"** regularly as they might be reset by
 android at 
  any time!
    - Energy saving options example for Xiaomi RedMi:
		- go to "Settings" 
        - "Battery & performance"
        - "App battery saver"
        - tap on the Talk app 
        - set "No restrictions"
    - Notification options example for Xiaomi RedMi:
		- go to "Settings" 
        - "Notifications" 
        - tap on the Talk app 
        - enable "Show notifications" and if you like 
          enable "Lock screen notifications"

## 🗨️ Check talk app settings
- In the settings, check if ringtones are set for calls and notifications and if vibration is activated if you would 
  like so.
- In the conversation settings (in the upper right corner of a conversation), check that notifications are set to 
  "Always notify" or "Notify when mentioned"
	- Be aware that this is a per conversation setting. Set it for every conversation differently depending on your 
      needs.
- Also be aware that notifications are not generated when you have an active session for a conversation. This also applies for tabs that are open in the background, etc.

## 🖥 Check server settings

Run the `notification:test-push` command for the user who is logged in at the device that should receive the notification:

```bash
sudo -u www-data php /var/www/yourinstance/occ notification:test-push --talk youruser
```

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
This means the notifications are set up correctly on server side. Note that there won't be any notification on the
 phone as this is just a test-message which won't be handled (As a developer you could set a breakpoint in the
  "onMessageReceived"-method of the Firebase cloud messaging handling to check if the notification is received).

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

# 🦺 Developers/testers
- Be aware that the "qa"-versions that you can install by scanning the QR-code in a github pull request don't 
  support notifications!

- When starting the talk app within Android Studio, make sure to select the "gplayDebug" build variant:
![gplay debug build variant](/docs/gplayDebugBuildVariant.png "gplay debug build variant")

- Especially after reinstalling the app, make sure to always check the android settings as they might be reset.