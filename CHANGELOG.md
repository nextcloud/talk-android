# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
Types of changes can be: Added/Changed/Deprecated/Removed/Fixed/Security

## [UNRELEASED]
### Changed
- improve conversation list design wand dark/light theming

### Fixed
- @ in username is allowed for phonebook sync
- avoid sync when phonebook is empty
- avoid creation of multiple "chat via"-links in phonebook
- delete "chat via"-link from phonebook if phone number was deleted on server
- remove all "chat via"-links from phonebook when sync is disabled
- fix to show avatars for incoming pictures in group chats (@starypatyk)

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

