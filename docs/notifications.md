This list is intended to help users that have problems to receive talk notifications on their android phone. It may
 not be complete. Please contribute to this list as you gain new knowledge. Just create an issue with the
  "notification" label or create a pull request for this document. 

# for users
- please install the app from google playstore. The f-droid version doesn't support notifications.
- only talk notifications will be delivered by the talk app, for all other notifications install the nextcloud files app from google playstore.

## check android settings
Please take into account that the android settings might be different for each manufacturer. It might be worth it to check what other messaging apps recommend to get their apps running on a certain smartphone and adapt this to the talk app.

- check that your phone is not in "do not disturb" mode and has internet access
- check the android settings like "energy saving" and "notifications" regulary as they might be reset by android!
- energy saving options
	- example for xiaomi redmi:
		- go to "settings" - "Battery & performance" - "App battery saver" - tap on the Talk app - set "No restrictions"
- notification options
	- example for xiaomi redmi:
		- go to "settings" - "Notifications" - tap on the Talk app - enable "Show notifications" and if you like enable "Lock screen notifications"

## check talk app settings
- in the settings, check if ringtones are set for calls and notifications and if vibration is activated if you would like so.
- in the conversation settings (in the upper right corner of a conversation), check that notifications are set to "Always notify" or "Notify when mentioned"
	- be aware that is is a per conversation setting. set it to every conversation differently depending on your needs.

If your problem still occurs after checking all these hints, create an issue at https://github.com/nextcloud/talk-android/issues

# for developers/testers
- be aware that the "qa"-versions that you can install by scanning the QR-code in a github pull request don't support notifications!

- When starting the talk app within Android Studio, make sure to select the "gplayDebug" build variant:
![gplay debug build variant](/docs/gplayDebugBuildVariant.png "gplay debug build variant")

- especially after reinstalling the app, make sure to always check the android settings as they might be reset.