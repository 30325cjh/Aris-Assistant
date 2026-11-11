# Aris Assistant

Android MVP for a floating assistant launcher and user-selected Unity AssetBundle import.

## Current scope
- Import and persist access to a `.unity3d` file using Android's Storage Access Framework.
- Request Android's display-over-other-apps permission.
- Run a foreground service that displays a movable floating assistant button.
- Tap the floating button to reopen the app.

**Important:** `.unity3d` is a Unity AssetBundle, not a universal model format. This first build stores the selected bundle but does not yet load or render its contents. Rendering requires a compatible Unity runtime and compatible bundle version/assets. AI chat and device actions are not included yet.

## Build
GitHub Actions builds a debug APK on pushes to `main`. Download it from the workflow run's **Artifacts** section.
