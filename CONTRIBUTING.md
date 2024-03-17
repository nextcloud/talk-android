<!--
 ~ SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# [Nextcloud](https://nextcloud.com) Talk for Android app

# Index
1. [Guidelines](#guidelines)
    1. [Issue reporting](#issue-reporting)
    1. [Labels](#labels)
        1. [Pull request](#pull-request)
        1. [Issue](#issue)
        1. [Bug workflow](#bug-workflow)
1. [Contributing to Source Code](#contributing-to-source-code)
    1. [Developing process](#developing-process)
        1. [Branching model](#branching-model)
        1. [Android Studio formatter setup](#android-studio-formatter-setup)
        1. [Build variants](#build-variants)
    1. [Contribution process](#contribution-process)
        1. [Fork and download android repository](#fork-and-download-android-repository)
        1. [Create pull request](#create-pull-request)
        1. [Create another pull request](#create-another-pull-request)
        1. [Backport pull request](#backport-pull-request)
        1. [Adding new files](#adding-new-files)
        1. [Testing](#testing)
	1. [File naming](#file-naming)
        1. [Menu files](#menu-files)
    1. [Translations](#translations)
    1. [Engineering practices](#engineering-practices)
        1. [Approach to technical debt](#approach-to-technical-debt)
        1. [Dependency injection](#dependency-injection)
        1. [Testing](#testing)
1. [Releases](#releases)
    1. [Types](#types)
        1. [Stable](#stable)
        1. [Release Candidate](#release-candidate)
		1. [Alpha Release](#alpha-release)
        1. [QA Release](#qa-release)
    1. [Version Name and number](#version-name-and-number)
        1. [Stable / Release candidate](#stable--release-candidate)
    1. [Release cycle](#release-cycle)
    1. [Release Process](#release-process)
        1. [Stable Release](#stable-release)
        1. [Release Candidate Release](#release-candidate-release)
        1. [Alpha Release](#alpha-release)

# Guidelines

## Issue reporting

* [Report the issue](https://github.com/nextcloud/talk-android/issues/new/choose) and choose bug report or feature request. The template includes all the information we need to track down the issue.
* This repository is *only* for issues within the Nextcloud Talk Android app code. Issues in other components should be reported in their own repositories, e.g. [Nextcloud server](https://github.com/nextcloud/server/issues)
* Search the [existing issues](https://github.com/nextcloud/talk-android/issues) first, it's likely that your issue was already reported.

If your issue appears to be a bug, and hasn't been reported, open a new issue.

## Labels

### Pull request

* 2 developing
* 3 to review

### Issue

* nothing
* approved
* PR exists (and then the PR# should be shown in first post)

### Bug workflow

Every bug should be triaged in approved/needs info in a given time.
* approved: at least one other is able to reproduce it
* needs info: something unclear, or not able to reproduce
  * if no response within 1 months, bug will be closed
* pr exists: if bug is fixed, link to pr

# Contributing to Source Code

Thanks for wanting to contribute source code to Nextcloud. That's great!

New contributions are added under GPL version 3+.

## Developing process

We are all about quality while not sacrificing speed so we use a very pragmatic workflow.

* create an issue with feature request
    * discuss it with other developers
    * create mockup if necessary
    * must be approved --> label approved
    * after that no conceptual changes!
* develop code
* create [pull request](https://github.com/nextcloud/talk-android/pulls)
* to assure the quality of the app, any PR gets reviewed, approved and tested before it will be merged to master

### Branching model

![branching model](/docs/branching.png "Branching Model")
* All contributions (bug fix or feature PRs) target the ```master``` branch
* Feature releases will always be based on ```master```
* Bug fix releases will always be based on their respective feature-release-bug-fix-branches
* Bug fixes relevant for the most recent _and_ released feature (e.g. ```11.0.0```) or bugfix (e.g. ```11.2.1```) release will be backported to the respective bugfix branch (e.g. ```stable-11.0``` or ```stable-11.2```)
* Hot fixes not relevant for an upcoming feature release but the latest release can target the bug fix branch directly

### Android Studio formatter setup

Our formatter setup is rather simple:
* Standard Android Studio
* Line length 120 characters (```Settings``` → ```Editor``` → ```Code Style``` → ```Right margin(columns)```: 120)
* Auto optimize imports (```Settings``` → ```Editor``` → ```Auto Import``` → ```Optimize imports on the fly```)

### Build variants

There are three build variants
* generic: no Google Stuff, used for F-Droid
* gplay: with Google Stuff (Push notification), used for Google Play Store
* qa: based on pr and available as direct download within the pr for testing purposes

### Apply a license

Nextcloud doesn't require a CLA (Contributor License Agreement).
The copyright belongs to all the individual contributors.
Therefore we recommend that every contributor adds following line to the header of a file, if they changed it substantially:

```
Copyright (c) <year> <your name> <your email address>
```

See section [Adding new files](#adding-new-files) for templates which can be used in new files.

### Sign your work

We use the Developer Certificate of Origin (DCO) as a additional safeguard for the Nextcloud project.
This is a well established and widely used mechanism to assure contributors have confirmed their right to license their contribution under the project's license.
Please read [developer-certificate-of-origin][dcofile].
If you can certify it, then just add a line to every git commit message:

````
  Signed-off-by: Random J Developer <random@developer.example.org>
````

Use your real name (sorry, no pseudonyms or anonymous contributions).
If you set your `user.name` and `user.email` git configs, you can sign your commit automatically with `git commit -s`.
You can also use git [aliases](https://git-scm.com/book/tr/v2/Git-Basics-Git-Aliases) like `git config --global alias.ci 'commit -s'`.
Now you can commit with `git ci` and the commit will be signed.

### Git hooks

We provide git hooks to make development process easier for both the developer and the reviewers.
To install them, just run:

```bash
./gradlew installGitHooks
```

## Contribution process

Contribute your code targeting/based-on the branch ```master```.
It will give us a better chance to test your code before merging it with stable code.

### Fork and download android repository:

* Please follow [SETUP.md](/SETUP.md) to setup Nextcloud Talk Android app work environment.

### Create pull request:

* Commit your changes locally: ```git commit -a```
* Push your changes to your GitHub repo: ```git push```
* Browse to <https://github.com/YOURGITHUBNAME/talk-android/pulls> and issue pull request
* Enter description and send pull request.

### Create another pull request:

To make sure your new pull request does not contain commits which are already contained in previous PRs, create a new branch which is a clone of upstream/master.

* ```git fetch upstream```
* ```git checkout -b my_new_master_branch upstream/master```
* If you want to rename that branch later: ```git checkout -b my_new_master_branch_with_new_name```
* Push branch to server: ```git push -u origin name_of_local_master_branch```
* Use GitHub to issue PR

### Backport pull request:

Use backport-bot via "/backport to stable-version", e.g. "/backport to stable-11.2".
This will automatically add "backport-request" label to PR and bot will create a new PR to targeted branch once the base PR is merged.
If automatic backport fails, it will create a comment.

### Adding new files

If you create a new file it needs to contain a license header. We encourage you to use the same license (GPL3+) as we do.
Copyright of Nextcloud GmbH is optional.

Source code of app:
```java/kotlin
/*
 * Nextcloud Talk application
 *
 * @author Your Name
 * Copyright (C) 2021 Your Name
 * Copyright (C) 2021 Nextcloud GmbH
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
 ```
 
 XML (layout) file:
 ```xml
<!--
  Nextcloud Talk application

  @author Your Name
  Copyright (C) 2021 Your Name
  Copyright (C) 2021 Nextcloud GmbH
 
  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  at your option) any later version.
 
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
 
  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->
```

## File naming

The file naming patterns are inspired and based on [Ribot's Android Project And Code Guidelines](https://github.com/ribot/android-guidelines/blob/c1d8c9c904eb31bf01fe24aadb963b74281fe79a/project_and_code_guidelines.md).

### Menu files

Similar to layout files, menu files should match the name of the component. For example, if we are defining a menu file that is going to be used in the `UserProfileActivity`, then the name of the file should be `activity_user_profile.xml`. Same pattern applies for menus used in adapter view items, dialogs, etc.

| Component        | Class Name             | Menu Name                   |
| ---------------- | ---------------------- | ----------------------------- |
| Activity         | `UserProfileActivity`  | `activity_user_profile.xml`   |
| Fragment         | `SignUpFragment`       | `fragment_sign_up.xml`        |
| Dialog           | `ChangePasswordDialog` | `dialog_change_password.xml`  |
| AdapterView item | ---                    | `item_person.xml`             |
| Partial layout   | ---                    | `partial_stats_bar.xml`       | 

A good practice is to not include the word `menu` as part of the name because these files are already located in the `menu` directory. In case a component uses several menus in different places (via popup menus) then the resource name would be extended. For example, if the user profile activity has two popup menus for configuring the users settings and one for the handling group assignments then the file names for the menus would be: `activity_user_profile_user_settings.xml` and `activity_user_profile_group_assignments.xml`.

## Translations

We manage translations via [Transifex](https://app.transifex.com/nextcloud/nextcloud/talk-android/). So just request 
joining the translation team for Android on the site and start translating. All translations will then be automatically pushed to this repository, there is no need for any pull request for translations.

When submitting PRs with changed translations, please only submit changes to values/strings.xml and not changes to translated files. These will be overwritten by the next merge of transifex-but and would increase PR review efforts.

## Engineering practices

This section contains some general guidelines for new contributors, based on common issues flagged during code review.

### Approach to technical debt

TL;DR Non-Stop Litter Picking Party!

We recognize the importance of technical debt that can slow down development, make bug fixing difficult and
discourage future contributors.

We are mindful of the [Broken Windows Theory](https://en.wikipedia.org/wiki/Broken_windows_theory) and we'd like to
actively promote and encourage contributors to apply The Scout's Rule: *"Always leave the campground cleaner than 
you found it"*. Simple, little improvements will sum up and will be very appreciated by Nextcloud team.

We also promise to actively support and mentor contributors that help us to improve code quality, as we understand
that this process is challenging and requires deep understanding of the application codebase.

### Dependency injection

TL;DR Avoid calling constructors inside constructors.

In effort to modernize the codebase we are applying [Dependency Injection](https://en.wikipedia.org/wiki/Dependency_injection)
whenever possible. We use 2 approaches: automatic and manual.

We are using [Dagger 2](https://dagger.dev/) to inject dependencies into major Android components only:

 * `Activity`
 * `Fragment`
 * `Service`
 * `BroadcastReceiver`
 * `ContentProvider`

This process is fairly automatic, with `@Inject` annotation being sufficient to supply properly initialized
objects. Android lifecycle callbacks allow us to do most of the work without effort.

For other application sub-components we prefer to use constructor injection and manually provide required dependencies.

This combination allows us to benefit from automation when it provides most value, does not tie the rest of the code
to any specific framework and stimulates continuous code modernization through iterative refactoring of all minor
elements.

### Testing
 
TL;DR If we can't write a test for it, it's not good.
 
Test automation is challenging in mobile applications in general. We try to improve in this area
and thereof we'd ask contributors to be mindful of their code testability:

1. new code submitted to Nextcloud project should be provided with automatic tests
2. contributions to existing code that is currently not covered by automatic tests
   should at least not make future efforts more challenging
3. whenever possible, testability should be improved even if the code is not covered by tests

# Releases

At the moment we are releasing the app in two app stores:

* [Google Play Store](https://play.google.com/store/apps/details?id=com.nextcloud.talk2)
* [F-Droid](https://f-droid.org/en/packages/com.nextcloud.talk2/)

## Types

We do differentiate between three different kinds of releases:

### Stable

Play store and f-droid releases for the masses.
Pull Requests that have been tested and reviewed can go to master. After the last alpha release is out in the wild and no mayor errors get reported (by users or in the developer console) the master branch is ready for the stable release phase.
So when we decide to go for a new release we freeze the master feature wise and a stable branch will be created.

### Release Candidate

_stable beta_ releases done via the Beta program of the Google Play store.
Whenever a PR is reviewed/approved we put it on master.
Before releasing a new stable version there is at least one release candidate. It is based on the current stable-branch. After a beta testing phase a stable version will be released, which is identical to the latest release candidate.

### Alpha Release

_alpha_ releases done via the Alpha program of the Google Play store.
Whenever a PR is reviewed/approved we put it on master.
Alpha releases are based on latest master and and we aim to release a new alpha version on a weekly basis. 

### QA Release

Done as a standalone app that can be installed in parallel to the stable app.
Any PR gets a QA build so users and reporters are able to easily test the change (feature or bugfix).

## Version Name and number

### Stable / Release candidate

For _stable_ and _release candidate_ the version name follows the [semantic versioning schema](http://semver.org/) and the version number has several digits reserved to parts of the versioning schema inspired by the [jayway version numbering](https://www.jayway.com/2015/03/11/automatic-versioncode-generation-in-android-gradle/), where:

* 2 digits for beta/alpha code as in release candidates starting at '01' (1-50=Alpha / 51-89=RC / 90-99=stable)
* 2 digits for hot fix code
* 3 digits for minor version code
* n digits for mayor version code

![Version code schema](/docs/semantic_versioning_code.png "Semantic versioning code")

Examples for different versions:
version name|version code
---|---
1.0.0|```10000099```
8.12.2|```80120290```
9.8.4-Alpha18|```90080418```
11.2.0-rc1|```110020051```

Beware that beta releases for an upcoming version will always use the minor and hotfix version of the release they are targeting. So to make sure the version code of the upcoming stable release will always be higher stable releases set the 2 beta digits to '90'-'99' as seen above in the examples. For major versions, as we're not a library and thus 'incompatible API changes' is not something that happens, decisions are essentially marketing-based. If we deem a release to be very impactful, we might increase the major version number.

## Release cycle

* major releases are linked to the corresponding [server-releases](https://apps.nextcloud.com/apps/spreed/releases) with aligned release date and version number (server version = 11 = client version)
* feature releases are planned every ~2 months, with 6 weeks of developing and 2 weeks of stabilising
* after feature freeze a public release candidate on play store and f-droid is released
* ~2 weeks testing, bug fixing
* release final version on f-droid and play store
* bugfix releases (dot releases, e.g. 3.2.1) are released 4 weeks after stable version from the branch created with first stable release (stable-3.2).

Hotfixes as well as security fixes are released via bugfix releases (dot releases) but are released on demand in contrast to regular, scheduled bugfix releases.

To get an idea which PRs and issues will be part of the next release simply check our [milestone plan](https://github.com/nextcloud/talk-android/milestones)

## Release process

### Stable Release

Stable releases are based on the git [stable-*](https://github.com/nextcloud/talk-android/branches/all?query=stable-).

1. Bump the version name and version code in the [/app/build.gradle](https://github.com/nextcloud/talk-android/blob/master/app/build.gradle), see chapter 'Version Name and number'.
2. Create a [release/tag](https://github.com/nextcloud/talk-android/releases) in git. Tag name following the naming schema: ```stable-Mayor.Minor.Hotfix``` (e.g. stable-1.2.0) naming the version number following the [semantic versioning schema](http://semver.org/)

### Release Candidate Release

Release Candidate releases are based on the git [stable-*](https://github.com/nextcloud/talk-android/branches/all?query=stable-) and are before publishing stable releases.

1. Bump the version name and version code in the [/app/build.gradle](https://github.com/nextcloud/talk-android/blob/master/app/build.gradle), see below the version name and code concept.
2. Create a [release/tag](https://github.com/nextcloud/talk-android/releases) in git. Tag name following the naming schema: ```rc-Mayor.Minor.Hotfix-betaIncrement``` (e.g. rc-1.2.0-12) naming the version number following the [semantic versioning schema](http://semver.org/)

### Alpha Release

Release Candidate releases are based on the git [master](https://github.com/nextcloud/talk-android) and are done between stable releases.

1. Bump the version name and version code in the [/app/build.gradle](https://github.com/nextcloud/talk-android/blob/master/app/build.gradle), see below the version name and code concept.
2. Create a [release/tag](https://github.com/nextcloud/talk-android/releases) in git. Tag name following the naming schema: ```rc-Mayor.Minor.Hotfix-betaIncrement``` (e.g. rc-1.2.0-12) naming the version number following the [semantic versioning schema](http://semver.org/)
