# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Types of changes can be: Added/Changed/Deprecated/Removed/Fixed/Security

## [15.0.3] - 2022-12-15

### Fixed
- App crash on startup when having multiple accounts
- Accounts seem to disappear
- Wrong conversation title / conversation title sometimes switches

For a full list, please see https://github.com/nextcloud/talk-android/milestone/64?closed=1

## [15.0.2] - 2022-12-01

### Fixed
- Fail to show user status in conversation list in some scenarios
- Fail to upload files on some devices
- Fail to pick avatar from local gallery
- Minor call issues

For a full list, please see https://github.com/nextcloud/talk-android/milestone/63?closed=1

## [15.0.1] - 2022-10-19

### Fixed
- Defect translations for regions like de_AT

For a full list, please see https://github.com/nextcloud/talk-android/milestone/62?closed=1

## [15.0.0] - 2022-10-17

### Added
- Simple polls (Talk 15 required on server)
- Direct video upload
- Show upload notification with progress bar for files >1MB
- Server theming
- Handle expiring messages (Talk 15 required on server)
- Respect permissions set by moderators
- Account chooser for "share to Nextcloud"
- Link previews (Talk 15 required on server)

### Changed
- Update design to Material 3
- Move allow guests preferences to conversation info

### Fixed
- Load higher resolution avatars in conversation list
- Upload large files

For a full list, please see https://github.com/nextcloud/talk-android/milestone/59?closed=1

## [14.2.0] - 2022-08-31
### Added

- Tabs for deck cards, locations and other objects in shared items view
- "Mark as read" via notification
- Create new profile avatar image with camera

### Changed

- Load higher resolution avatars in conversation list

### Fixed

- Fail to open newly created conversation
- Starting a call from chat screen crashes
- Rare crashes during swipe left for reply to a message

For a full list, please see https://github.com/nextcloud/talk-android/milestone/57?closed=1

## [14.1.1] - 2022-08-15
### Fixed
- Swipe left for reply to a message
- Multiple minor issues

For a full list, please see https://github.com/nextcloud/talk-android/milestone/58?closed=1

## [14.1.0] - 2022-07-18
### Added
- Search within messages
- Quick reply via notification (@starypatyk)
### Changed
- Scroll to oldest unread message when opening a conversation
### Fixed
- No conversations loaded when user status app is limited to groups (server setting)
- Minor bugfixes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/52?closed=1

## [14.0.2] - 2022-05-13
### Added
- Handling for "event.participants.update.all" from HPB
### Fixed
- Multiple NPE
- Reactions option for deleted messages and commands are shown
- Always show reaction count (not only > 1)
- Reactions option shown in read-only conversations

For a full list, please see https: https://github.com/nextcloud/talk-android/milestone/55?closed=1

## [14.0.1] - 2022-05-03
- fix app crashes when UI isn't available anymore
- fix to load conversations when servers status app is disabled

For a full list, please see https://github.com/nextcloud/talk-android/milestone/54?closed=1

## [14.0.0] - 2022-05-02
### Added
- emoji message reactions
- set own user status / show user status of others
- show shared items of a conversation
- search for open conversations
- mark conversation as read
- select audio output for calls
- choose notification sounds by android settings (starypatyk)
- share contact from attachment dialog

### Fixed
- call connection from android to web sometimes fail on HPB 
- top bar partially hidden when typing message
- can't open chat view from notification (starypatyk)
- minor fixes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/50?closed=1

## [13.0.0] - 2021-11-29
### Added
- set own user status / show user status of others
- search for open conversations
- mark conversation as read (@AndyScherzinger)
- select audio output for calls
- choose notification sounds by android settings (@starypatyk)
- share contact from attachment dialog

### Fixed
- top bar remains fully visible when typing message
- minor fixes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/50?closed=1

## [12.2.1] - 2021-09-02
- clear chat history (as moderator)
- forward text messages
- RTL support

For a full list, please see https://github.com/nextcloud/talk-android/milestone/45?closed=1

## [12.1.2] - 2021-07-16
- fix to share link from chrome
- make links clickable in conversation description
- minor fixes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/47?closed=1

## [12.1.1] - 2021-07-09
### Fixed
- fix crash on startup (happened for some older Nextcloud server versions)
- fix to receive notifications when using Nextcloud server 22
- fix background of send button (when server version is <22)
- minor fixes

## [12.1.0] - 2021-07-06
### Added
- "share to Nextcloud Talk" from other apps
- location sharing (requires Talk 12 on server)
- voice messages (requires Talk 12 on server)
- open files inside app (jpg, .png, .gif, .mp3, .mp4, .mov, .wav, .txt, .md)
    - other data types are opened with external apps if they are able to handle it
- edit profile information and privacy settings
- add grid view for calls, make own video movable
- improve vcard support

### Changed
- improve conversation list design and dark/light theming (@AndyScherzinger)
- introduce new dark/light toolbar/searchbar design (@AndyScherzinger)
- improve login screen design (@AndyScherzinger)
- improve content/toolbar alignments (@AndyScherzinger)
- various design improvements (@AndyScherzinger)

### Fixed
- @ in username is allowed for phonebook sync
- avoid sync when phonebook is empty
- avoid creation of multiple "chat via"-links in phonebook
- delete "chat via"-link from phonebook if phone number was deleted on server
- remove all "chat via"-links from phonebook when sync is disabled
- fix to show avatars for incoming pictures in group chats (@starypatyk)
- do not allow selecting files in files browser that are not allowed to be reshared
- fix to show all file previews
- don't keep screen enabled in chat view
- fix logfile flooding (Too much storage was used when the app was offline and a high performance backend is used)

## [11.1.0] - 2021-03-12
### Added
- add ability to enter own phone number when address book sync is enabled

### Fixed
- show links for deck-cards

## [11.0.0] - 2021-02-23
### Added
- upload files from local storage
- delete messages (requires Talk 11.1 on server)
- UI-improvements for call screens
- new ringtone for outgoing calls
