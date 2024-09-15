<!--
 ~ SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# [Nextcloud](https://nextcloud.com) Talk for Android Setup Guide

# Index
1. [Setup Guide](#setup-guide)
    1. [Tooling setup](#tooling-setup)
    1. [Fork and checkout the repository](#fork-and-checkout-the-repository)
	1. [Work and build environments](#work-and-build-environments)
	    1. [Working with Android Studio](#working-with-android-studio)
        1. [Working in a terminal with gradle](#working-in-a-terminal-with-gradle)
		1. [App flavours](#app-flavours)
	1. [Troubleshooting](#troubleshooting)
		1. [Compilation fails with "java.lang.OutOfMemoryError: Java heap space" error](#compilation-fails-with-javalangoutofmemoryerror-java-heap-space-error)

# Setup Guide

These instructions will help you to set up your development environment, get the source code of the Nextcloud Talk for Android app and build it by yourself.
If you want to help developing the app take a look to the [contribution guidelines][0].

Sections 1) and 2) are common for any environment.
The rest of the sections describe how to set up a project in different tool environments.
Nowadays we recommend to use Android Studio (section 2), but you can also build the app from the command line (section 3).

If you have any problem, remove the 'talk-android' folder, start again from 1) and work your way down.
If something still does not work as described here, please open a new issue describing exactly what you did, what happened, and what should have happened.

## Tooling setup

There are some tools needed, no matter what is your specific IDE or build tool of preference.

[git][1] is used to access to the different versions of the Nextcloud's source code.
Download and install the version appropriate for your operating system from [here][2].
Add the full path to the 'bin/' directory from your git installation into the PATH variable of your environment so that it can be used from any location.

[Android Studio][5] is currently the official Android IDE. Due to this, we recommend it as the IDE to use in your development environment.
Follow the installation instructions [here][6].

We recommend to use the last version available in the stable channel of Android Studio updates.

The Android SDK is necessary to build the app. Install it via Android Studio itself: 

```Settings``` → ```Languages & Frameworks```→ ```Android SDK```

After installing it, add the full path to the directories 'tools/' and 'platform-tools/' from your Android SDK installation into the PATH variable of your environment.

Open the Android SDK Manager under Android Studio's settings

```Settings``` → ```Languages & Frameworks```→ ```Android SDK```

To build the Nextcloud for Android app you will need to install at least the next SDK packages:

* Android SDK Tools and Android SDK Platform-Tools (already installed); upgrade to their last versions is usually a good idea.
* Android SDK Build-Tools 30.0.3.
* Android 11 (API 29), SDK Platform; needed to build the nextcloud app.

Install any other package you consider interesting, such as emulators.

After installing it, add the full path to the directories 'tools/' and 'platform-tools/' from your Android SDK installation into the PATH variable of your environment.

## Fork and checkout the repository

Fork and download the nextcloud/talk-android repository.

You will need [git][1] to access to the different versions of the Nextcloud's source code.
The source code is hosted on GitHub and may be read by anybody, without a GitHub account.
You will need one if you want to contribute to the development of the app with your own code.

The next steps will assume you have a GitHub account and that you will get the code from your own fork.

1. In a web browser, go to https://github.com/nextcloud/talk-android, and click the 'Fork' button near the top right corner.
2. Open a terminal and go on with the next steps in it.
3. Clone your forked repository: ```git clone --recursive https://github.com/YOURGITHUBNAME/talk-android.git```.
4. Move to the project folder with ```cd talk-android```.
5. Pull any changes from your remote branch 'master': ```git pull origin master```
6. Make official Nextcloud repo known as upstream: ```git remote add upstream https://github.com/nextcloud/talk-android.git```
7. Make sure to get the latest changes from official talk-android/master branch: ```git pull upstream master```

At this point you can continue using different tools to build the project.
Sections [Working with Android Studio](#working-with-android-studio) and [Working in a terminal with gradle](#working-in-a-terminal-with-gradle) describe the existing alternatives.

## Work and build environments

### Working with Android Studio

To set up the project in Android Studio follow the next steps:

1. Open Android Studio and select 'Open an Existing Project'. Browse through your file system to the folder 'talk-android' where the project is located. The file chooser will show an Android face as the folder icon, which you can select to reopen the project.
2. Android Studio will try to build the project directly after importing it. To build it manually, just click the 'Play' button in the toolbar to build and run it in a mobile device or an emulator. The resulting APK file will be saved in the 'app/build/outputs/apk/' subdirectory in the project folder and being installed/launched in a configured emulator or if connected your physical device.
3. Check Android Studio editor configuration for the project: ```Settings``` → ```Editor``` → ```Code Style``` → ```Scheme: Project``` and ```Enable EditorConfig support``` (should be enabled by default)

### Working in a terminal with gradle

[Gradle][7] is the build system used by Android Studio to manage the building operations on Android apps.
You do not need to install gradle in your system, and Google recommends not to do it, but instead trusting on the [Gradle wrapper][8] included in the project.

1. Open a terminal and go to the 'talk-android' directory that contains the repository.
2. Run the 'clean' and 'build' tasks using the gradle wrapper provided
    - Windows: ```gradlew.bat clean assembleGplay``` or ```gradlew.bat clean assembleGeneric```
    - Mac OS/Linux: ```./gradlew clean assembleGplay``` or ```./gradlew clean assembleGeneric```

The first time the gradle wrapper is called, the correct gradle version will be downloaded automatically. 
This requires a working Internet connection.

The generated APK file is saved in ```app/build/outputs/apk``` as ```app-generic-debug.apk```.

### App flavours

The app is currently equipped to be built with three flavours:
* **Generic** - the regular build, released as Nextcloud Android app on F-Droid
* **Gplay** - with Google stuff (push notification), used for Google Play store
* **Qa** - build per pr for testing

## Troubleshooting

### Compilation fails with "java.lang.OutOfMemoryError: Java heap space" error

The default settings for gradle is to limit the compilation to 1GB of heap.
You can increase that value by:
1. adding `org.gradle.jvmargs=-Xmx4G` to `gradle.properties` and
2. running gradlew(.bat) with this command line : `GRADLE_OPTS="-Xmx4G" ./gradlew clean build"`

[0]: https://github.com/nextcloud/talk-android/blob/master/CONTRIBUTING.md
[1]: https://git-scm.com/
[2]: https://git-scm.com/downloads
[5]: https://developer.android.com/studio
[6]: https://developer.android.com/studio/install
[7]: https://gradle.org/
[8]: https://docs.gradle.org/current/userguide/gradle_wrapper.html
[9]: https://github.com/pinterest/ktlint/releases/latest
