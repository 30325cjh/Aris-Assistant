# Aris AI

Personal-use Android floating assistant prototype, implemented with Kotlin.

## Approved experience

- First launch explains the display-over-other-apps permission before opening Android settings.
- The user explicitly starts and stops the floating foreground service.
- The compact launcher is draggable.
- One tap expands or collapses the character placeholder.
- A double-tap on the expanded placeholder opens or closes the chat panel. A double-tap on the compact launcher only expands it.
- Chat stays hidden until the expanded character is double-tapped.
- The persistent foreground-service notification includes a stop action and opens app settings when tapped.
- Overlay permission is checked again when the app resumes and before overlay interactions.

## Current milestone: M1 floating-shell prototype

The floating view and chat panel are interaction/layout prototypes. The avatar is **not** a Live2D or 3D model. The chat panel only recognizes a small set of local expression-test words; it is not an AI chat implementation.

The following approved features are still future milestones:
- Live2D renderer and rigged upper-body model (requires verified SDK/runtime compatibility, appropriate rights, and layered model assets).
- Gemini provider, streaming chat, cancellation, local conversation storage, and user-managed memory.
- Optional speech input/output, speaking-state UI, and audio-synchronized lip movement.
- Validated emotion directives and allowlisted Android app/search/settings actions with confirmation for consequential actions.

The existing Unity AssetBundle picker is retained only as an experimental import path. Choosing a .unity3d file stores its document URI; it does not load or render the bundle.

## Android permissions and privacy

- SYSTEM_ALERT_WINDOW is granted by the user in Android settings. It is never silently granted.
- On Android 13+, the app explains and requests notification permission before starting the service. If denied, the user must explicitly confirm continuing; Android may show the service only in its system task manager rather than the notification drawer.
- The foreground-service notification is intentionally visible and includes an explicit stop action.
- The app does not request microphone permission or collect audio in this milestone.
- No API key, model file, or private conversation is bundled in this prototype.

## Build

Toolchain pinned by the project/CI:
- Android Gradle Plugin: 8.7.3
- Kotlin Android plugin: 2.0.21
- Gradle: 8.9
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26

The repository does not currently include the Gradle Wrapper. CI installs the exact Gradle 8.9 distribution with gradle/actions/setup-gradle@v5 and runs the local overlay-policy unit tests before packaging:

    gradle --no-daemon testDebugUnitTest assembleDebug

For a local build, install Gradle 8.9 and Android SDK platform/build tools for API 35, then run the same command. The CI workflow uploads Aris-Assistant-debug-apk when the build succeeds.

## Automated tests

JVM unit tests cover the overlay tap/double-tap policy and display-bound position clamping. They run in the Android Actions workflow before the debug APK is assembled. These tests do not replace emulator or physical-device interaction tests.

## Device validation still required

No emulator or physical-device test is claimed here. Verify on the target Samsung One UI phone/tablet:
- Overlay permission grant and revocation.
- Single tap, double tap, and drag separation.
- Chat keyboard focus and close behavior.
- Foreground notification stop action.
- Rotation, process termination, and long-running overlay behavior.
