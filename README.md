<!--
 ~ SPDX-FileCopyrightText: 2017-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# [Nextcloud](https://nextcloud.com) Talk for Android :speech_balloon:

[![Build Status](https://drone.nextcloud.com/api/badges/nextcloud/talk-android/status.svg)](https://drone.nextcloud.com/nextcloud/talk-android) [![Codacy Badge](https://app.codacy.com/project/badge/Grade/b89a720efbd24754984a776804913bca)](https://www.codacy.com/gh/nextcloud/talk-android/dashboard) [![Releases](https://img.shields.io/github/release/nextcloud/talk-android.svg)](https://github.com/nextcloud/talk-android/releases/latest) [![REUSE status](https://api.reuse.software/badge/github.com/nextcloud/talk-android)](https://api.reuse.software/info/github.com/nextcloud/talk-android)

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" 
      alt="Download from Google Play" 
      height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.talk2)
[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/com.nextcloud.talk2/)
[<img src="https://github.com/user-attachments/assets/713d71c5-3dec-4ec4-a3f2-8d28d025a9c6"
      alt="Get it with Obtainium"
      height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.nextcloud.talk2%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fnextcloud%2Ftalk-android%22%2C%22author%22%3A%22nextcloud%22%2C%22name%22%3A%22Talk%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Atrue%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Enextcloud.*%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22Nextcloud%20Talk%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3Anull%7D)


Please note that Notifications won't work with the F-Droid version due to missing Google Play Services.

Signing certificate fingerprint to [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify)
the APK (same as for the [Nextcloud Android client app](https://github.com/nextcloud/android)):
- APK with "gplay" name, found [here](https://github.com/nextcloud/talk-android/releases) or distributed via Google Play Store
- APK with "nextcloud", found [here](https://github.com/nextcloud/talk-android/releases)
- not suitable for Fdroid downloads, as Fdroid is signing it on their own
```
SHA-256: fb009522f65e25802261b67b10a45fd70e610031976f40b28a649e152ded0373
SHA-1: 74aa1702e714941be481e1f7ce4a8f779c19dcea
```

|||||||
|---|---|---|---|---|---|
|![Conversation list](/fastlane/metadata/android/en-US/images/phoneScreenshots/conversationList_light.png "Conversation list")|![Participant search](/fastlane/metadata/android/en-US/images/phoneScreenshots/searchParticipant_light.png "Participant search")|![Voice call](/fastlane/metadata/android/en-US/images/phoneScreenshots/voiceCall.png "Voice call")|![Voice recording](/fastlane/metadata/android/en-US/images/phoneScreenshots/voiceRecord_light.png "Voice recording")|![Markdown view](/fastlane/metadata/android/en-US/images/phoneScreenshots/markdown_light.png "Markdown view")|![Settings](/fastlane/metadata/android/en-US/images/phoneScreenshots/settings_light.png "Settings")|

**Video & audio calls through Nextcloud on Android**

Nextcloud Talk is a fully on-premises audio/video and chat communication service. It features web and mobile apps and is designed to offer the highest degree of security while being easy to use.

Nextcloud Talk lowers the barrier for communication and lets your team connect any time, any where, on any device, with each other, customers or partners. 

## Why is this so awesome? :sparkles:

Because it is self hosted!!! Audio/video calls and text chat typically require a central server. Some projects go commendably far in trying to ensure they can't see the data, so nobody, not government, advertising company or somebody who broke in the servers, can follow conversations. But the servers still have to mediate every call and text message, allowing them to map out who talks to who and at what time. This 'metadata' [is as useful](https://www.wired.com/2015/03/data-and-goliath-nsa-metadata-spying-your-secrets/), if not more, to track people, than the full content, especially for mass surveillance purposes. Even if the data is not stored by the chat server, the hosting provider or a hacker could simply gather the data.

By hosting your own server, all meta data stays on your server and thus under your control!

If you have suggestions or problems, please [open an issue](https://github.com/nextcloud/talk-android/issues) or contribute directly :)

## How to contribute :rocket:

If you want to [contribute](https://nextcloud.com/contribute/) to Nextcloud, you are very welcome: 

- on [our public Talk team conversation](https://cloud.nextcloud.com/call/c7fz9qpr)
- our forum at https://help.nextcloud.com
- for translations of the app on [Transifex](https://app.transifex.com/nextcloud/nextcloud/android-talk/)
- opening issues and PRs (including a corresponding issue)

## Contribution Guidelines :scroll:

[GPLv3](https://github.com/nextcloud/talk-android/blob/master/LICENSE.txt). All contributions to this repository are considered to be licensed under the GNU GPLv3 or any later version.

Please read the [Code of Conduct](https://nextcloud.com/community/code-of-conduct/). This document offers some guidance to ensure Nextcloud participants can cooperate effectively in a positive and inspiring atmosphere, and to explain how together we can strengthen and support each other.

Please review the [guidelines for contributing](/CONTRIBUTING.md) to this repository.

More information how to contribute: [https://nextcloud.com/contribute/](https://nextcloud.com/contribute/)

## Start contributing :hammer_and_wrench:

Make sure you read [SETUP.md](/SETUP.md) and [CONTRIBUTING.md](/CONTRIBUTING.md) before you start working on this project.
But basically: fork this repository and contribute back using pull requests to the master branch.
Easy starting points are also reviewing [pull requests](https://github.com/nextcloud/talk-android/pulls) and working on [starter issues](https://github.com/nextcloud/talk-android/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22).

### Testing :test_tube:

So you would like to contribute by testing? Awesome, we appreciate that very much. 

To report a bug for the alpha or beta version, just create an issue on github like you would for the stable version and
 provide the version number. Please remember that Google Services are necessary to receive push notifications. 
 
#### Beta versions (Release Candidates) :package:

##### via Google Play

Sign up at [Google Play Beta channel](https://play.google.com/apps/testing/com.nextcloud.talk2) to get Release Candidates via Google Play.

##### via github

You can also get the Release Candidates at [github releases](https://github.com/nextcloud/talk-android/releases).

#### Alpha versions

##### via Google Play

To become an alpha tester you have to be signed up for the [Google Play Beta channel](https://play.google.com/apps/testing/com.nextcloud.talk2) 
and additionally you have to join the [Alpha testing Google Group](https://groups.google.com/g/nextcloud-android-talk-alpha-testing). 
After that you will receive the alpha versions via the Play Store (initially, this might take some minutes after
 signing up). However, in the Play Store the app will still be named "Nextcloud Talk (Beta)" even if you are an alpha tester, but you will receive the alpha versions.
If a beta was released that is newer than the alpha version, you will get the beta in the alpha channel.
 
##### via Download page

In addition to google play, the alpha and beta apps can also be obtained from the Nextcloud [Download page](https://download.nextcloud.com/android/talk-alpha/)
Please make sure to remember that these versions might contain bugs and you don't use them in production.

## Support :rescue_worker_helmet:

If you need assistance or want to ask a question about the Talk Android app, you are welcome to [ask for community help](https://help.nextcloud.com/c/support/talk/52) in our forums. If you have found a bug, feel free to [open a new issue on GitHub](https://github.com/nextcloud/talk-android/issues). Keep in mind, that this repository only manages the Nextcloud Talk for Android app. If you find bugs or have problems with the server/backend, you should ask the [Nextcloud server team](https://github.com/nextcloud/server) for help!

### Notifications

If you have problems to receive talk notifications on your android phone, please have a look at [this checklist](https://github.com/nextcloud/talk-android/blob/master/docs/notifications.md).

## Credits :scroll:

### Ringtones :bell:

- [Ringtones by Librem](https://developer.puri.sm/licenses/Librem5/Birch/sound-theme-librem5.html) 
  author: [feandesign](https://soundcloud.com/feandesign)
- [Telefon-Freiton in Deutschland nach DTAG 1 TR 110-1, Kap. 8.3](https://commons.wikimedia.org/wiki/File:1TR110-1_Kap8.3_Freiton1.ogg)
  author: arvedkrynil

[dcofile]: https://github.com/nextcloud/talk-android/blob/master/contribute/developer-certificate-of-origin
[applyalicense]: https://github.com/nextcloud/talk-android/blob/master/contribute/HowToApplyALicense.md

## Remarks :scroll:

Google Play and the Google Play logo are trademarks of Google Inc.
