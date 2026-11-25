# Aris AI

Personal-use Android floating assistant prototype, implemented with Kotlin.

## Approved experience

- First launch explains the display-over-other-apps permission before opening Android settings.
- The user explicitly starts and stops the floating foreground service. An explicit start records the user's opt-in; Android may restore the sticky foreground service after ordinary process death only while that opt-in and overlay permission remain valid. The stop action clears the opt-in. The app does not auto-start at boot or bypass Android force-stop behavior.
- The compact launcher is draggable.
- One tap on the compact launcher expands the character placeholder; a single tap on the expanded character does not collapse it.
- A double-tap on the expanded placeholder opens or closes the chat panel. A double-tap on the compact launcher only expands it.
- A double-tap is committed when the second touch ends; if that touch crosses the drag threshold, it remains a drag and does not toggle chat.
- Chat stays hidden until the expanded character is double-tapped.
- The persistent foreground-service notification includes a stop action and opens app settings when tapped.
- Overlay permission is checked again when the app resumes, when a sticky service restart is received, and before overlay interactions. Revocation clears the user-enabled flag and removes the overlay. On Android 11+, the launcher is clamped to the safe area formed by system-bar and display-cutout insets; Android 8-10 use the legacy display-metrics fallback.
- The in-app service diagnostics screen reports the current app-owned service state, overlay/notification permission state, the persisted user opt-in, and the latest allowlisted lifecycle/recovery result. It stores only fixed event codes and timestamps; it never records chat text, saved memories, API keys, or action payloads.
- Every accepted foreground-service start calls `startForeground()` before synchronous user-consent persistence or database/WindowManager work, so a slow or failed preference write cannot skip Android's foreground-start deadline. If consent persistence fails, the service stops without attaching the overlay. Explicit stop commits the opt-out before teardown.

## Current implementation: M0-M3 foundation

The floating view and character remain UI prototypes. `CharacterRenderer` separates the floating host from the character view, and `PlaceholderCharacterRenderer` remains the active fallback; the avatar is **not** a Live2D or 3D model.

The expanded chat sends text to the Gemini REST API after the user supplies a key in the app's Gemini settings screen. Local expression-test words still update the placeholder label without making a network request. Conversations and messages are stored in a private, device-local SQLite database. The user can create, select, rename, inspect, edit, and delete conversations or individual messages.

Saved memories are never extracted automatically. The user must add, edit, or delete each memory in the Conversation / Memory screen. Memory context is off by default; only when the user explicitly enables it are up to 10 saved memories (maximum 500 characters each) included in Gemini requests. Nothing is synced to a cloud service. Conversation titles, message text, and saved-memory content are AES-GCM encrypted before being written to SQLite using a non-exportable Android Keystore key. Row IDs, roles, timestamps, and message counts remain visible as metadata. Do not store passwords, recovery codes, or other secrets as memories.

Remaining approved features:
- A real Live2D `CharacterRenderer` implementation and rigged upper-body model (requires verified SDK/runtime compatibility, appropriate rights, and layered model assets).
- Streaming responses and optional user-managed speech input/output with audio-synchronized lip movement.
- Validated emotion directives connected to the renderer and allowlisted Android app/search/settings actions with confirmation for consequential actions.

The existing Unity AssetBundle picker is retained only as an experimental import path. Choosing a .unity3d file stores its document URI; it does not load or render the bundle.


## AI provider contract (M3 groundwork)

`AssistantProvider` remains the provider-neutral boundary. `GeminiProvider` now implements a non-streaming text request to Google's `generateContent` REST endpoint, using the documented `x-goog-api-key` header and the `gemini-3.8-flash` model. Requests are made on an IO dispatcher, use bounded input/history and response sizes, and map HTTP failures to user-safe messages. The prompt explicitly tells the model it cannot execute Android actions.

`GeminiApiKeyStore` encrypts the user-supplied key with an AES-GCM key held by Android Keystore. Only the IV and ciphertext are stored in private app preferences; the key is never bundled in source, BuildConfig, or the repository. The app has a settings screen to save or delete it. A mobile app cannot make a user-owned API key completely secret from a rooted or instrumented device, so the settings screen warns users to restrict the key at Google.

This is the first connected text-chat slice, not the complete assistant: responses are not streamed, and Android action execution is not present. Conversation history is persisted locally in SQLite and restored when the floating chat opens. Explicitly saved memories remain user-managed; they are added to Gemini requests only when the separate opt-in setting is enabled. Device actions remain outside the provider response contract and must later use a separate allowlisted executor.

## Live2D runtime evaluation

See [docs/M2_LIVE2D_RUNTIME_EVALUATION.md](docs/M2_LIVE2D_RUNTIME_EVALUATION.md) for the official Android/OpenGL support findings, sample toolchain gap, license considerations, and model-authoring blocker. The current placeholder remains active; no Live2D SDK or model is bundled.

## Android permissions and privacy

- SYSTEM_ALERT_WINDOW is granted by the user in Android settings. It is never silently granted.
- On Android 13+, the app explains and requests notification permission before starting the service. If denied, the user must explicitly confirm continuing; Android may show the service only in its system task manager rather than the notification drawer.
- The foreground-service notification is intentionally visible and includes an explicit stop action.
- The app requests INTERNET only for the user-initiated Gemini text request; cleartext traffic remains disabled.
- The Gemini API key is entered by the user and encrypted at rest with Android Keystore AES-GCM. It is not bundled in the APK or repository. Rooted/instrumented devices can still expose a key while it is in use; restrict it with the provider's key controls.
- The app does not request microphone permission or collect audio in this milestone.
- Conversation titles, message text, and saved-memory content are AES-GCM encrypted before being written to the app-private SQLite database. The AES key is non-exportable and held by Android Keystore; per-row associated data binds each ciphertext to its table, column, and record ID. Row IDs, roles, timestamps, and message counts remain visible as database metadata. No cloud sync is implemented.
- Database version 1 plaintext content is migrated to version 2 encryption inside SQLiteOpenHelper's upgrade transaction. A failed migration rolls back rather than dropping the user's history. SQLite secure-delete is enabled whenever the database is opened, including during migration, to reduce plaintext remnants in freed pages.
- If the Android Keystore key is lost or invalidated, encrypted conversation content cannot be recovered; the app fails closed instead of silently generating a replacement key. The app disables Android backup, so encrypted database files are not intended to be restored independently of their Keystore key.
- This protects data at rest in the app's database; it does not protect text while the app is unlocked, visible on screen, or being sent to Gemini after the user enables memory context. Do not store passwords, recovery codes, API keys, or other secrets in conversations or memories.
- Saved memories are manually added by the user and are not sent to Gemini by default. An explicit opt-in switch sends up to 10 saved memories (500 characters each) with Gemini requests.
- The Conversation / Memory screen supports selecting, renaming, inspecting, editing, and deleting conversations/messages, plus creating, editing, and deleting saved memories.

## Build

Toolchain pinned by the project/CI:
- Android Gradle Plugin: 8.7.3
- Kotlin Android plugin: 2.0.21
- Gradle: 8.9
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26

The repository includes a checksum-verified Gradle 8.9 Wrapper bootstrap for Linux/macOS and Windows. The official Gradle 8.9 wrapper JAR is stored as base64 text and materialized locally by the launcher; its SHA-256 is checked before Java executes it. The downloaded Gradle distribution is independently verified by the Wrapper using the official distribution checksum.

Linux/macOS:

    chmod +x gradlew
    ./gradlew --no-daemon testDebugUnitTest assembleDebug

Windows:

    .\\gradlew.bat --no-daemon testDebugUnitTest assembleDebug

The first invocation requires network access to download the pinned Gradle 8.9 binary distribution. Install JDK 17 and Android SDK platform/build tools for API 35. CI runs the same wrapper command and uses gradle/actions/setup-gradle@v5 for caching and wrapper validation. The generated binary wrapper JAR is ignored by Git; the checksum-verified base64 source remains tracked. The app uses kotlinx-coroutines-android 1.9.0 for lifecycle-scoped asynchronous network work. The CI workflow uploads Aris-Assistant-debug-apk when the build succeeds.

## Automated tests

JVM unit tests cover the overlay tap/double-tap policy, display-bound and system-inset position clamping, explicit foreground-service start/stop and sticky-restart policy, character presentation mapping, provider directive allowlists, Gemini request-size/history bounds, conversation CRUD, and migration of a version 1 plaintext database to encrypted version 2 content. They also cover foreground-promotion-before-consent ordering, service-consent persistence, and privacy-safe diagnostic event/recovery snapshots. The migration test verifies that titles, message text, and memories are not stored as readable plaintext and remain readable after reopening with the same test key. The Android Keystore implementation, live Gemini requests, and overlay interactions still require device-level validation.

## Device validation still required

No emulator or physical-device test is claimed here. Verify on the target Samsung One UI phone/tablet:
- Overlay permission grant and revocation.
- Single tap, double tap, and drag separation.
- Chat keyboard focus and close behavior.
- Foreground notification stop action and clearing the persisted user-enabled flag.
- Ordinary process-death restoration while permission remains granted, plus confirmation that force-stop is not bypassed.
- The in-app service diagnostics screen agrees with Android's visible service state, and its last-event/recovery values contain no private content.
- Rotation, process termination, and long-running overlay behavior.
