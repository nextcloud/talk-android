# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Types of changes can be: Added/Changed/Deprecated/Removed/Fixed/Security

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
