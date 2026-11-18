# Aris AI

Personal-use Android floating assistant prototype, implemented with Kotlin.

## Approved experience

- First launch explains the display-over-other-apps permission before opening Android settings.
- The user explicitly starts and stops the floating foreground service.
- The compact launcher is draggable.
- One tap on the compact launcher expands the character placeholder; a single tap on the expanded character does not collapse it.
- A double-tap on the expanded placeholder opens or closes the chat panel. A double-tap on the compact launcher only expands it.
- Chat stays hidden until the expanded character is double-tapped.
- The persistent foreground-service notification includes a stop action and opens app settings when tapped.
- Overlay permission is checked again when the app resumes and before overlay interactions.

## Current milestones: M1 floating shell and M2 renderer seam

The floating view and character remain UI prototypes. `CharacterRenderer` separates the floating host from the character view, and `PlaceholderCharacterRenderer` remains the active fallback; the avatar is **not** a Live2D or 3D model.

The expanded chat now sends text to the Gemini REST API after the user supplies a key in the app's Gemini settings screen. Local expression-test words still update the placeholder label without making a network request. Conversation context is held only in the running floating service and can be cleared from the chat panel; it is not persisted across service restarts.

Remaining approved features:
- A real Live2D `CharacterRenderer` implementation and rigged upper-body model (requires verified SDK/runtime compatibility, appropriate rights, and layered model assets).
- Streaming responses, durable conversation storage, and user-managed long-term memory.
- Optional speech input/output, speaking-state UI, and audio-synchronized lip movement.
- Validated emotion directives connected to the renderer and allowlisted Android app/search/settings actions with confirmation for consequential actions.

The existing Unity AssetBundle picker is retained only as an experimental import path. Choosing a .unity3d file stores its document URI; it does not load or render the bundle.


## AI provider contract (M3 groundwork)

`AssistantProvider` remains the provider-neutral boundary. `GeminiProvider` now implements a non-streaming text request to Google's `generateContent` REST endpoint, using the documented `x-goog-api-key` header and the `gemini-3.8-flash` model. Requests are made on an IO dispatcher, use bounded input/history and response sizes, and map HTTP failures to user-safe messages. The prompt explicitly tells the model it cannot execute Android actions.

`GeminiApiKeyStore` encrypts the user-supplied key with an AES-GCM key held by Android Keystore. Only the IV and ciphertext are stored in private app preferences; the key is never bundled in source, BuildConfig, or the repository. The app has a settings screen to save or delete it. A mobile app cannot make a user-owned API key completely secret from a rooted or instrumented device, so the settings screen warns users to restrict the key at Google.

This is the first connected text-chat slice, not the complete assistant: responses are not streamed, chat context exists only in memory for the lifetime of the floating service, and no long-term memory or Android action executor is present. Device actions remain outside the provider response contract and must later use a separate allowlisted executor.

## Live2D runtime evaluation

See [docs/M2_LIVE2D_RUNTIME_EVALUATION.md](docs/M2_LIVE2D_RUNTIME_EVALUATION.md) for the official Android/OpenGL support findings, sample toolchain gap, license considerations, and model-authoring blocker. The current placeholder remains active; no Live2D SDK or model is bundled.

## Android permissions and privacy

- SYSTEM_ALERT_WINDOW is granted by the user in Android settings. It is never silently granted.
- On Android 13+, the app explains and requests notification permission before starting the service. If denied, the user must explicitly confirm continuing; Android may show the service only in its system task manager rather than the notification drawer.
- The foreground-service notification is intentionally visible and includes an explicit stop action.
- The app requests INTERNET only for the user-initiated Gemini text request; cleartext traffic remains disabled.
- The Gemini API key is entered by the user and encrypted at rest with Android Keystore AES-GCM. It is not bundled in the APK or repository. Rooted/instrumented devices can still expose a key while it is in use; restrict it with the provider's key controls.
- The app does not request microphone permission or collect audio in this milestone.
- Conversation context is transient in service memory and has a chat-panel clear control; it is not yet durable or synced.

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

JVM unit tests cover the overlay tap/double-tap policy, display-bound position clamping, character presentation mapping, provider directive allowlists, and Gemini request-size/history bounds. They run in the Android Actions workflow before the debug APK is assembled. Android Keystore encryption, live Gemini requests, and overlay interactions still require device-level validation.

## Device validation still required

No emulator or physical-device test is claimed here. Verify on the target Samsung One UI phone/tablet:
- Overlay permission grant and revocation.
- Single tap, double tap, and drag separation.
- Chat keyboard focus and close behavior.
- Foreground notification stop action.
- Rotation, process termination, and long-running overlay behavior.
