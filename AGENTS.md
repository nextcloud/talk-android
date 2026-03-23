<!--
  - SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
  - SPDX-License-Identifier: GPL-3.0-or-later
-->
# AGENTS.md

This file provides guidance to all AI agents (Claude, Codex, Gemini, etc.) working with code in this repository.

## Project Overview

Nextcloud Talk for Android — a self-hosted audio/video and chat communication app. Connects to a Nextcloud server backend. Written primarily in Kotlin (some legacy Java), targets API 26+ (minSdk 26, targetSdk 36).

## Build Commands

```bash
# Assemble a debug APK (F-Droid flavor, no Google services)
./gradlew assembleGenericDebug

# Assemble with Google Play services (push notifications)
./gradlew assembleGplayDebug

# Run all unit tests
./gradlew test

# Run a single unit test class
./gradlew testGenericDebugUnitTest --tests "com.nextcloud.talk.utils.SomeTest"

# Run instrumented (on-device) tests
./gradlew connectedAndroidTest

# Static analysis — all checks (spotbugs, lint, ktlint, detekt)
./gradlew check

# Individual checks
./gradlew ktlintCheck
./gradlew ktlintFormat   # auto-fix
./gradlew detekt
./gradlew lint

# Install git hooks (run once after cloning)
./gradlew installGitHooks

# Clean build
./gradlew clean assembleGenericDebug
```

Build output: `app/build/outputs/apk/`

## Build Flavors

| Flavor    | App ID                   | Purpose                              |
|-----------|--------------------------|--------------------------------------|
| `generic` | `com.nextcloud.talk2`    | F-Droid release (no Google services) |
| `gplay`   | `com.nextcloud.talk2`    | Google Play (Firebase push notifs)   |
| `qa`      | `com.nextcloud.talk2.qa` | Per-PR testing builds                |

`gplay`-only dependencies (Firebase, play-services-base) use `gplayImplementation`. Avoid introducing Play-only dependencies into `generic` code paths. F-Droid (`generic`) builds do not support Google push notifications.

## Architecture

MVVM with layered architecture:

- **API layer** — `api/NcApi.java` (Retrofit/RxJava2) and `api/NcApiCoroutines.kt` (Retrofit/coroutines).
- **Data layer** — `data/` contains Room DB entities/DAOs (`data/database/`), repository impls (`data/user/`, `repositories/`), and a network monitor. The Room DB is encrypted with SQLCipher.
- **Repository layer** — `repositories/` and `data/user/UsersRepository.kt` are the single source of truth.
- **ViewModel layer** — expose `StateFlow`/`LiveData` to UI. Located in per-feature `viewmodels/` subdirectories.
- **UI layer** — Activities/Fragments per feature. Mix of traditional View/XML and Jetpack Compose (composables live alongside XML layouts in feature packages).

### Dependency Injection

Dagger 2 (via AutoDagger2). App component: `application/NextcloudTalkApplication.kt` (`@AutoComponent`). Modules in `dagger/modules/`: `RestModule`, `DatabaseModule`, `DaosModule`, `RepositoryModule`, `ViewModelModule`, `ManagerModule`, `UtilsModule`.

Use `@Inject` for Activities/Fragments/Services/BroadcastReceivers. For all other components, prefer constructor injection.

### Feature Packages (under `com/nextcloud/talk/`)

- `conversationlist/` — main screen after login (actively being Compose-migrated, see below)
- `chat/` — `ChatActivity`, message input, voice recording, scheduled messages
- `call/` — WebRTC participant modeling, MCU/non-MCU strategies
- `webrtc/` — low-level WebRTC: `PeerConnectionWrapper`, `WebSocketInstance`, audio
- `signaling/` — `SignalingMessageReceiver`, `SignalingMessageSender`, typed notifiers
- `conversationinfo/` / `conversationinfoedit/` — room settings
- `account/` — login, account verification
- `settings/` — app settings
- `jobs/` — WorkManager background workers
- `services/` — `CallForegroundService`
- `ui/theme/` — Nextcloud theming applied to Material components

### Signaling Architecture

Two modes selected at runtime based on server capabilities:
- **No-MCU** (P2P mesh): `call/LocalStateBroadcasterNoMcu.kt`, `call/MessageSenderNoMcu.kt`
- **MCU** (media server): `call/LocalStateBroadcasterMcu.java`, `call/MessageSenderMcu.java`

`signaling/SignalingMessageReceiver.java` dispatches to typed notifiers (`CallParticipantMessageNotifier`, `WebRtcMessageNotifier`, etc.).

**When changing participant or call state handling, always verify both MCU and no-MCU paths — a change that works in one mode can silently break the other.**

## Active Work: Compose Migration of `conversationlist/`

`ConversationsListActivity` is being incrementally migrated to Jetpack Compose. The plan is in `docs/compose-migration-conversations-list.md`. Steps 1–7 are complete (ViewModel state consolidation, status banners, empty states, FAB, notification warning card, federation invitation card, shimmer loading, conversation item composable). Steps 8–10 (LazyColumn list, toolbar/search bar, full Activity handover) are pending.

**Convention:** During the migration each component is replaced one at a time so the app remains fully functional after every step. New composables go in `conversationlist/ui/`. The existing `FlexibleAdapter`/`RecyclerView` is kept until Step 8.

## Code Style

- Line length: **120 characters**
- Standard Android Studio formatter with EditorConfig.
- Kotlin preferred for new code; legacy Java still present.
- Do not use decorative section-divider comments of any kind (e.g. `// ── Title ───`, `// ------`, `// ======`).
- Every new file must end with exactly one empty trailing line (no more, no less).
- All new files require an SPDX license header:

  Kotlin/Java:
  ```kotlin
  /*
   * Nextcloud Talk - Android Client
   *
   * SPDX-FileCopyrightText: <year> Nextcloud GmbH and Nextcloud contributors
   * SPDX-License-Identifier: GPL-3.0-or-later
   */
  ```

  XML:
  ```xml
  <!--
    ~ Nextcloud Talk - Android Client
    ~
    ~ SPDX-FileCopyrightText: <year> Nextcloud GmbH and Nextcloud contributors
    ~ SPDX-License-Identifier: GPL-3.0-or-later
  -->
  ```

- Translations via Transifex — only modify `values/strings.xml`, never translated `values-*/strings.xml` files.

## File Naming

Layout/menu files follow the component they belong to:

| Component        | Class Name             | File Name                       |
|------------------|------------------------|---------------------------------|
| Activity         | `UserProfileActivity`  | `activity_user_profile.xml`     |
| Fragment         | `SignUpFragment`       | `fragment_sign_up.xml`          |
| Dialog           | `ChangePasswordDialog` | `dialog_change_password.xml`    |
| AdapterView item | —                      | `item_person.xml`               |
| Partial layout   | —                      | `partial_stats_bar.xml`         |

## Design

- Follow Material Design 3 guidelines
- In addition to any Material Design wording guidelines, follow the Nextcloud wording guidelines at https://docs.nextcloud.com/server/latest/developer_manual/design/foundations.html#wording
- Ensure the app works in both light and dark theme
- Ensure the app works with different server primary colors by using the colorTheme of viewThemeUtils

## After Making Changes

After finishing code changes, run `./gradlew detekt ktlintCheck` and fix any new errors or warnings before considering the task done.

## Static Analysis

- **detekt**: config in `detekt.yml` (maxIssues: 80)
- **ktlint**: via `org.jlleitschuh.gradle.ktlint` plugin
- **SpotBugs**: filter in `spotbugs-filter.xml`; FindSecBugs and fb-contrib active
- **lint**: HTML report at `app/build/reports/lint/lint.html`

## Testing

- **Unit tests**: `app/src/test/` — JUnit 4/5, Mockito, Robolectric, MockWebServer. Uses `useJUnitPlatform()`.
- **Instrumented tests**: `app/src/androidTest/` — Espresso. Integration tests need real server credentials in `gradle.properties` (`NC_TEST_SERVER_BASEURL`, etc.).
- **Room migrations**: if you change the schema, add or update migration tests under `androidTest/data/`. See `data/source/local/TalkDatabase.kt` for migration declarations.
- **App startup workers**: `NextcloudTalkApplication.kt` schedules periodic workers (`CapabilitiesWorker`, signaling/WebSocket workers) at startup. Worker scheduling changes can cause subtle startup regressions.

## Commits

- All commits must be signed off (`git commit -s`) per the Developer Certificate of Origin (DCO). All PRs target `master`. Backports use `/backport to stable-X.Y` in a PR comment.

- Commit messages must follow the [Conventional Commits v1.0.0 specification](https://www.conventionalcommits.org/en/v1.0.0/#specification) — e.g. `feat(chat): add voice message playback`, `fix(call): handle MCU disconnect gracefully`.

- Every commit made with AI assistance must include an `AI-assistant` trailer identifying the coding agent, its version, and the model(s) used:

  ```
  AI-assistant: Claude Code 2.1.80 (Claude Sonnet 4.6)
  AI-assistant: Copilot 1.0.6 (Claude Sonnet 4.6)
  ```

  General pattern: `AI-assistant: <coding-agent> <agent-version> (<model-name> <model-version>)`

  If multiple models are used for different roles, extend the trailer with named roles:

  ```
  AI-assistant: OpenCode v1.0.203 (plan: Claude Opus 4.5, edit: Claude Sonnet 4.5)
  ```

  Pattern with roles: `AI-assistant: <coding-agent> <agent-version> (<role>: <model-name> <model-version>, <role>: <model-name> <model-version>)`
