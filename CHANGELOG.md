<!--
 ~ SPDX-FileCopyrightText: 2021-2024 Nextcloud GmbH and Nextcloud contributors
 ~ SPDX-License-Identifier: GPL-3.0-or-later
-->
# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Types of changes can be: Added/Changed/Deprecated/Removed/Fixed/Security

## [21.1.0] - 2025-06-05

### Added
- Allow adding participants to one-to-one chats creating a new conversation
- Handling of event conversations
- Show info about participant (organization, role, timezone, ...) in 1:1 conversation info screen
- Added gallery option in chat attachment menu (access photos and videos with one click without giving permissions)
- Add self-test button for push notifications in diagnosis screen
- Edit checkbox in chat messages
- Team mentions in chat
- Add option to mark a conversation as sensitive
- Allow bluetooth headset to be discovered and used during a call (@gavine99)

### Changed
- Design of participants grid in call
- Improve subline in conversations screen when last message is attachment
- Improve message search
- In search window, show messages at last
- Switch video capture for calls between 4:3 and 16:9 ratio depending on portrait/ landscape mode

### Fixed
- Crashes
- Videos in videocall lost after app comes back to foreground
- Open conversations not being shown in search
- Minor bugs (@MmAaXx500)

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/nextcloud/talk-android/milestone/94?closed=1

## [21.0.1] - 2025-04-15

### Fixed
- No sound transmitted from android device in initial call after granting microphone permission
- Duplicated chat messages are shown (e.g. after taking a picture)
- Crashes

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/nextcloud/talk-android/milestone/95?closed=1

## [21.0.0] - 2025-02-24

### Added
- Sending status for text messages
- "Retry" button for messages where sending failed
- Playback speed control for voice messages (@arkascha)
- Auto play consecutive voice messages (@Ruggero1912)
- Show info in offline mode when no chat messages are available for a conversation
- Archive/unarchive conversation from context menu
- Search in main screen shows open conversations and contacts

### Changed
- Edit & delete buttons for an offline written message (queued state) are moved to the context menu
- Move archive conversation button in conversation-info screen to "Danger zone"

### Fixed
- Duplicated chat messages are shown after screen rotation
- Chat loading animation overlays with chat messages
- Wrong reaction backgrounds
- Mentioned teams are not rendered in chat
- Crashes
- Minor bugs

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/nextcloud/talk-android/milestone/92?closed=1

## [20.1.1] - 2025-01-16

### Added
- Display conversation avatars for open conversations search

### Changed
- play ".webm" videos in internal player

### Fixed
- Video is not shown in android to android calls
- App crashes
- Send and voice-message icons overlay
- minor bugs

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/nextcloud/talk-android/milestone/93?closed=1

## [20.1.0] - 2024-12-03

### Added
- Archive conversations
- Show regular warning if notification settings are not set up correctly (can be disabled)
- Identify guests invited via email address
- Long press options for end call button (leave or end call for all)
- Textsearch in "Join open conversations" screen

### Changed
- Call started indicator is now at bottom of chat

Minimum: NC 17 Server, Android 8.0 Oreo

For a full list, please see https://github.com/nextcloud/talk-android/milestone/86?closed=1

## [20.0.6] - 2024-11-28

### Added
- Permanent warning if notification settings are not set up correctly (can be disabled)

Minimum: NC 17 Server, Android 7.0 Nougat

## [20.0.5] - 2024-11-18

### Fixed
- Fix a crash when opening chat

Minimum: NC 17 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/91?closed=1

## [20.0.4] - 2024-11-14

### Fixed
- Emojis to react in call are missing (now with horizontal scrolling)
- Fix crashes

Minimum: NC 17 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/90?closed=1

## [20.0.3] - 2024-11-11

### Added
- Improvements to offline support ("offline first")
- Queuing and editing of offline written messages
- New "unread messages marker" behavior
- Align grouping of chat messages with server behavior
- Set full screen width for messages in 'Note To Self' (@arkascha)

### Fixed
- Sometimes offline messages need to be re-downloaded
- Unread messages marker is shown multiple times
- Deck cards are shown as {object}
- Minor bugs

Minimum: NC 17 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/89?closed=1

## [20.0.2] - 2024-09-26

### Added
- Improvements for conversation creation

### Fixed
- Conversation list + chat does not load for very old server versions
- TextInput field is shown when user has no permission to write
- Call buttons sometimes disappear after 30 sec for federated rooms
- Avoid crashes that could happen when entering chat

Minimum: NC 17 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/88?closed=1

## [20.0.1] - 2024-09-17

### Fixed
- Chat does not load for older server versions
- Status icons are not shown
- Account is missing in account switcher dialog
- Error for federated invitation is shown although server does not support them

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/87?closed=1

## [20.0.0] - 2024-09-14

### Added
- Offline support for conversations list and chat
- Federated calls
- Allow banning users and guests
- Open internal links for files app from any screen
- Show conversation description in chat and when listing open conversations

### Changed
- New workflow for conversation creation

### Fixed
- Connection fails with wired internet
- Minor bugs

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/85?closed=1

## [19.0.1] - 2024-05-23

### Fixed
- Wrong availability of "leave conversation" and "delete conversation"
- Chats jump to last message instead to first unread message
- Sent text from "share to" feature is set repeatedly for text input
- Shared files from Nextcloud fail to open Nextcloud files app
- Minor bugs

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/83?closed=1

## [19.0.0] - 2024-04-23

### Added
- Federated conversations
- Message editing

### Changed
- Updated file icons

### Fixed
- Participants in conversation info screen are missing
- Flickering appbar when scrolling conversation list
- Call notification screen is sometimes incomplete/unresponsive
- Polls won't open
- Note to self icon is not shown for languages other than english
- Minor bugs

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/81?closed=1

## [18.1.0] - 2024-03-12

### Added
- Diagnosis screen (in advanced settings. incl. share option to create new issue)
- Show warnings if notification settings are not set correctly
- Grouping for upload notifications (@parneet-guraya)
- Stop media playback when switching output device (@parneet-guraya)
- Avoid multiple media playbacks (@parneet-guraya)
- Add "Add to notes" in message options
- Cursor position is saved in message drafts
- Share message text to other apps
- Support Android 14
- Janus 1.x support

### Fixed
- App permanently sends speaking data channel message
- Back button closes app when forwarding a message

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/79?closed=1

## [18.0.1] - 2023-12-22

### Fixed
- Voice messages sometimes fail to playback

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/75?closed=1

## [18.0.0] - 2023-12-11

### Added
- File captions
- Note To Self
- Recording consent
- Share files by long press context menu (@Smarshal21)
- Save files to storage (@FaribaKhandani)
- Show active call in chat with accept call buttons

### Fixed
- Not possible to delete voice, video, image, contact and location messages (@Smarshal21)
- Hide "unread mention" bubble in search mode (@sowjanyakch)
- Call notification screen remains open
- Minor bug fixes (@parneet-guraya et al.)

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/75?closed=1

## [17.1.3] - 2023-11-17

### Fixed
- Login via Active Directory fails when using Umlauts in username
- Crash when guest without name joins a call
- Chat messages disappear on initial configuration change (e.g. screen rotation)

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/78?closed=1

## [17.1.2] - 2023-10-19

### Fixed
- Fix to play voice messages
- Fix to send voice message after recording was stopped to re-listen
- Fix emoji size in markdown headers
- minor bug fixes

### Changed
- message reminder: TimePicker format matches locale of device
- removed Android Auto support for now

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/76?closed=1

## [17.1.0] - 2023-09-15

### Added
- Markdown support
- Group system messages
- Set reminders for messages
- List open conversations
- Call duration visible while in a call
- Filter for unread / mentioned conversations
- Continuous recording of voice messages
- Android Auto support
- Keep message drafts
- Show status icon in chatview
- Send indicator that user is speaking when being in a call

### Fixed
- Media playback does not retain state (@parneet-guraya)
- minor bug fixes

### Changed
- Adjust app icon size for notifications (@Smarshal21)

Minimum: Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/71?closed=1

## [17.0.2] - 2023-07-24

### Fixed
- Fix establishing of call connection to High Performance Backend when rejoining call

Minimum: NC 14 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/73?closed=1

## [17.0.1] - 2023-07-07

### Fixed
- Avoid crash when opening conversations (happened when OpenAI translations were enabled)
- Avoid loading conversations screen multiple times after login when multiple accounts are used
- Fix phone book integration
- Minor fixes

### Changed
- new UI for Settings screen

Minimum: NC 14 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/72?closed=1

## [17.0.0] - 2023-06-12

### Added
- Typing indicator (requires NC27 and high performance backend)
- Conversation avatars (requires NC27)
- Reactions in calls (requires NC27)
- Translate chat messages (requires NC27 and translation provider)
- Group mentions in a conversation
- Set conversation description

### Fixed
- Avatars gone in conversation list (e.g. after screen rotation)

Minimum: NC 14 Server, Android 7.0 Nougat

For a full list, please see https://github.com/nextcloud/talk-android/milestone/70?closed=1

## [16.0.1] - 2023-04-21

### Fixed
- Fix to scroll to first unread message
- Rare crashes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/69?closed=1

## [16.0.0] - 2023-03-20

### Added
- Call recording support (requires NC26, HPB + recording server)
- Breakout rooms support (requires NC26 + HPB)
- Raise hand
- Scroll to bottom button in chat (@rapterjet2004)
- Scroll to quoted messages on tap (@rapterjet2004)

### Fixed
- Display duplicated chatmessages
- Chatmessages not being displayed
- Broken "mention" design when groupname contains emoji
- Avatars/Images not being displayed in some cases
- Fix theming of set status dialog buttons
- Fix call buttons size for landscape mode
- Rare crashes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/65?closed=1

## [15.1.2] - 2023-02-17

### Added
- Show raised hands of remote participants

### Changed
- Better voice message recording quality

### Fixed
- Missing author in group conversations
- Missing file thumbnails in "share from Nextcloud"
- Repair multiple actions when switching account via notification
- Missing "back" button when opening chat by notification
- Rare crashes

For a full list, please see https://github.com/nextcloud/talk-android/milestone/67?closed=1

## [15.1.1] - 2023-01-18

### Fixed

- Missing file icons in chat
- "Random" crashes
- Call connections

For a full list, please see https://github.com/nextcloud/talk-android/milestone/66?closed=1

## [15.1.0] - 2023-01-12

### Added
- Support latest emojis
- Localize time formatting

### Changed
- Android 6 required
- Improvements to calls

### Fixed
- Crash on startup because of Unknown color
- The video of the first remote participant is eventually disabled
- Show notifications for missed calls and improve duration for ringing
- Too hard to react with already given reactions
- Crash when joining call when ringtone is set to silent

For a full list, please see https://github.com/nextcloud/talk-android/milestone/61?closed=1

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
