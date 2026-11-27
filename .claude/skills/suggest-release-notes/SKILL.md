---
name: suggest-release-notes
description: Fill the release changelog file that fastlane incrementVersion created, named after the version code, for a release candidate or final release. Use when asked to write release notes or a changelog for a release.
---
<!--
 ~ SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# Suggest release notes

`bundle exec fastlane incrementVersion` creates an empty changelog file named
after the version code, usually
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, and prints its
path. Its content is used as release notes on Google Play and as description
of the GitHub release. `releasePhase2` refuses to release if the file is empty
or longer than 500 characters.

These instructions describe how to fill that file. They are written so that an
AI agent can follow them, but apply to humans as well. They are shared by
several Nextcloud Android apps, which is why they name no app or repository.

## 1. Find the release

Read `versionCode` and `versionName` from `app/build.gradle.kts`, and the
GitHub repository (`<repo>`, e.g. `nextcloud/talk-android`) from `REPO` in
`fastlane/Fastfile`.

- The last two digits of the version code are the build number: 51–89 is a
  release candidate, 90–99 is a final release. Below 51 is an alpha, which
  gets no changelog.
- The version without suffix (e.g. `25.1.0` for `25.1.0 RC2`) is the title of
  the GitHub milestone.
- The changelog file is `<versionCode>.txt` in the folder set by
  `CHANGELOG_DIR` in the fastlane files, relative to `fastlane/` (usually
  `metadata/android/en-US/changelogs`). Do not overwrite content that is
  already in it without asking.

## 2. Collect the changes

The changelog of a release candidate or final release lists all changes of the
version, not only those since the previous release candidate. Collect:

1. **Previous notes of the same version.** If a release candidate of this
   version was already published, start from its notes and add to them:
   `gh release list -R <repo>` and `gh release view <tag> -R <repo>`.
   Tags look like `rc-25.1.0-02` (release candidate) and `25.1.0` (final).
2. **Merged pull requests in the milestone.** This is the main source:

   ```bash
   gh api 'repos/<repo>/milestones?state=all&per_page=100' \
     -q '.[] | select(.title == "<version>") | .number'
   gh pr list -R <repo> --search 'milestone:"<version>" is:merged' -L 500 \
     --json number,title,body,author,labels
   ```

3. **Commits since the previous tag**, to find changes whose pull request is
   missing in the milestone: `git log --oneline <previous tag>..HEAD`.

Pull requests titled `[stable-…] …` from `backportbot` are backports. Their
body starts with `Backport of #<number>`. Treat a backport and its original as
one change, and use the author of the original pull request.

## 3. Select what to mention

Only mention what a user of the app notices. Leave out:

- translation and synced file updates (`fix(l10n)`, label `sync`)
- dependency updates, CI, tests, build and release automation
- refactorings and code cleanup without visible effect

Read the pull request body when the title is not clear enough to describe the
change for users.

## 4. Credit contributors

Credit authors who are neither members of the Nextcloud GitHub organization nor
bots, as `(@login)` after the item. Several minor fixes by such contributors
can be combined into one item, e.g. `- Various bugs (@login1, @login2)`.

- Organization membership: `gh api orgs/nextcloud/members/<login> --silent`
  succeeds for members (HTTP 204) and fails for non-members (HTTP 404).
- Bots: logins ending in `[bot]` or starting with `app/`, and accounts like
  `backportbot`, `nextcloud-bot`, `renovate` and `dependabot`.

## 5. Write the file

Format (sections without items are left out):

<!-- markdownlint-disable MD013 -->
```markdown
## Added
- Refreshing of conversation list
- Improved bluetooth handling (@login)

## Fixed
- Notifications sometimes do not show up when using Android 11 or lower
- Various bugs (@login1, @login2)

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/<repo>/milestone/<number>?closed=1
```
<!-- markdownlint-enable MD013 -->

- Items are short, user facing English, without issue or pull request numbers
  and without commit prefixes like `fix:`.
- Take the `Minimum` line from the previous release notes. Check the Android
  version against `minSdk` in `app/build.gradle.kts` and update it if it
  changed.
- Use the milestone number found in step 2 for the link.

## 6. Keep it within 500 characters

Google Play does not accept release notes longer than 500 characters. The limit
applies to the whole file, including the `Minimum` line and the link. Check
with `wc -m < <file>`.

If it is too long, keep the most important additions and fixes, and combine
the remaining minor fixes into `- Various bugs` (keeping the credits) or leave
them out. The milestone link lists everything.

Do not commit the file. Show it to the person doing the release, who reviews it
and commits it together with the version change by running
`bundle exec fastlane commitVersion`.
