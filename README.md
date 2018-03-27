# Nextcloud Talk for Android

**Video & audio calls through Nextcloud on Android**

Nextcloud Talk is a fully on-premises audio/video and chat communication service. It features web and mobile apps and is designed to offer the highest degree of security while being easy to use.

Nextcloud Talk lowers the barrier for communication and lets your team connect any time, any where, on any device, with each other, customers or partners.

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/21a6fb22279e401baba31fb296b6f20e)](https://www.codacy.com/app/Nextcloud/talk-android?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=nextcloud/talk-android&amp;utm_campaign=Badge_Grade) [![irc](https://img.shields.io/badge/IRC-%23nextcloud--mobile%20on%20freenode-blue.svg)](https://webchat.freenode.net/?channels=nextcloud-mobile)

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" 
      alt="Download from Google Play" 
      height="80">](https://play.google.com/store/apps/details?id=com.nextcloud.talk2)
[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/com.nextcloud.talk2/)

## Why is this so awesome?
Because it is self hosted!!! Audio/video calls and text chat typically require a central server. Some projects go commendably far in trying to ensure they can't see the data, so nobody, not government, advertising company or somebody who broke in the servers, can follow conversations. But the servers still have to mediate every call and text message, allowing them to map out who talks to who and at what time. This 'metadata' [is as useful](https://www.wired.com/2015/03/data-and-goliath-nsa-metadata-spying-your-secrets/), if not more, to track people, than the full content, especially for mass surveillance purposes. Even if the data is not stored by the chat server, the hosting provider or a hacker could simply gather the data.

By hosting your own server, all meta data stays on your server and thus under your control!

If you have suggestions or problems, please [open an issue](https://github.com/nextcloud/talk-android/issues) or contribute directly :)

## Contribution Guidelines

Please read the [Code of Conduct](https://nextcloud.com/community/code-of-conduct/). This document offers some guidance to ensure Nextcloud participants can cooperate effectively in a positive and inspiring atmosphere, and to explain how together we can strengthen and support each other.

For more information please review the [guidelines for contributing](https://github.com/nextcloud/talk-android/blob/master/CONTRIBUTING.md) to this repository.

### Testing

So you would like to contribute by testing? Awesome, we appreciate that very much. Right now our testing
is conducted through the Google Play Beta channel, so if you'd like to receive the newest dough, sign
up over at the [Google Play Beta channel](https://play.google.com/apps/testing/com.nextcloud.talk2).

### Apply a license

All contributions to this repository are considered to be licensed under
the GNU GPLv3 or any later version.

Contributors to the Nextcloud Talk app retain their copyright. Therefore we recommend
to add following line to the header of a file, if you changed it substantially:

```
@copyright Copyright (c) <year> <your name> (<your email address>)
```

For further information on how to add or update the license header correctly please have a look at [our licensing HowTo][applyalicense].

### Sign your work

We use the Developer Certificate of Origin (DCO) as a additional safeguard
for the Nextcloud project. This is a well established and widely used
mechanism to assure contributors have confirmed their right to license
their contribution under the project's license.
Please read [developer-certificate-of-origin][dcofile].
If you can certify it, then just add a line to every git commit message:

````
  Signed-off-by: Random J Developer <random@developer.example.org>
````

Use your real name (sorry, no pseudonyms or anonymous contributions).
If you set your `user.name` and `user.email` git configs, you can sign your
commit automatically with `git commit -s`. You can also use git [aliases](https://git-scm.com/book/tr/v2/Git-Basics-Git-Aliases)
like `git config --global alias.ci 'commit -s'`. Now you can commit with
`git ci` and the commit will be signed.

[dcofile]: https://github.com/nextcloud/talk-android/blob/master/contribute/developer-certificate-of-origin
[applyalicense]: https://github.com/nextcloud/talk-android/blob/master/contribute/HowToApplyALicense.md
