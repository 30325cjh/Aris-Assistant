# Aris AI

Personal-use Android floating assistant prototype, built with Kotlin.

## Current milestone: floating shell (M1 prototype)

- Requests Android's display-over-other-apps permission through the system settings screen.
- Starts a user-requested foreground service with a persistent notification and an explicit **종료** action.
- Shows a compact floating Aris launcher.
- Single-tap expands/collapses a Millennium-themed character placeholder; chat is not shown by default.
- Double-tap opens/closes the chat test panel.
- Drag moves the overlay; gesture detection separates taps from scrolling/dragging.
- Existing Unity AssetBundle selection is retained as an experimental file-import path only.

## Important limitations

The current floating character is a text-based UI placeholder, **not a Live2D or 3D model**. Selecting a .unity3d file only stores its document URI; it does not load or render the bundle. Gemini, production chat, voice, lip-sync, persistent memory, and device-action tools are not implemented yet.

The app must be granted overlay permission by the user. Android may stop the service, and the app must handle permission revocation. The foreground-service notification is intentionally visible and provides a stop action.

## Build

- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- JDK: 17
- compileSdk / targetSdk: 35
- minSdk: 26

Run gradle assembleDebug from the repository root, or run the Android APK GitHub Actions workflow. CI builds pushes on all branches and manual workflow dispatches. The debug APK is uploaded as a workflow artifact.
