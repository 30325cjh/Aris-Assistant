# Aris AI — Product & Technical Specification v1.0

Status: Design approved  
Target: Personal-use Android application  
Repository: 30325cjh/Aris-Assistant  
Design branch: docs/aris-ai-v1-design

## 1. Product vision

Aris AI is a floating, character-led personal assistant for Android. The user can summon the character above other apps, open a text chat with a double-tap, optionally switch to voice, and ask the assistant to answer questions or perform explicitly supported device actions.

The initial character presentation is a Live2D-style 2.5D upper-body avatar. The first implementation must prioritize a reliable Android shell and a replaceable character-rendering interface so model/runtime choices do not become coupled to chat or device actions.

## 2. Approved user experience

### 2.1 First launch
1. Show an introduction and explain why overlay permission is needed.
2. Send the user to Android's system overlay-permission screen.
3. Re-check permission when the app resumes; never assume it was granted.
4. Explain notification permission where required and start the user-requested foreground service only after the user enables the floating assistant.
5. Keep settings available from the launcher activity.

The app must not attempt to silently grant permissions or hide the persistent foreground-service notification where Android requires it.

### 2.2 Floating assistant
- A small draggable floating launcher is shown while the user-enabled assistant service is active.
- Tapping the launcher expands the character into an upper-body idle presentation; the chat remains hidden.
- Double-tapping the expanded character opens or closes the chat panel.
- The character can be repositioned. Gesture handling must distinguish taps, double-taps, and drags to avoid accidental activation.
- Provide an obvious close/stop action and a way back to the app's settings.
- Overlay windows must not block unrelated screen interaction except where the chat panel intentionally accepts touch and text input.

### 2.3 Chat
- Text is the default interaction mode.
- The chat panel contains conversation history, a text composer, send action, voice-mode toggle, and close action.
- Closing chat returns to the character-only floating state; stopping the service removes all overlays.
- Chat history is stored locally according to user-controlled retention settings.

### 2.4 Voice
- Voice is optional and activated by a user-controlled button.
- Speech recognition and text-to-speech are separate services behind interfaces.
- Show microphone/listening/speaking state clearly.
- Lip-sync should follow TTS playback timing or audio amplitude/viseme data where available; do not animate mouth movement merely because text is being streamed.
- Provide mute/stop controls.

### 2.5 Settings screens
1. AI provider and model configuration.
2. Character appearance, scale, and animation preferences.
3. Voice input/output settings.
4. Device-action permissions and explanations.
5. Conversation and memory management, including inspect, edit, and delete.
6. Android permissions, service status, privacy, and diagnostics.

Visual direction: Blue Archive / Millennium-inspired game UI, using a restrained navy, white, and cyan palette, geometric panels, subtle technical motifs, readable typography, and accessible contrast. Avoid copying game logos or UI assets as required dependencies; theme assets should be original or appropriately licensed.

## 3. Character specification

### 3.1 Runtime
Preferred direction: Live2D Cubism 2.5D, subject to SDK compatibility, licensing, and Android integration verification. Keep a renderer abstraction so a compatible alternative can be evaluated if the selected SDK cannot be used in the target app.

### 3.2 Initial scope
Upper-body avatar only. Full-body rigging, weapon props, and complex scene animation are out of the first release scope.

Required motion/parameters:
- Idle breathing/sway.
- Natural blinking.
- Eye gaze.
- Head yaw and pitch.
- Hair physics/sway.
- Mouth movement synchronized to voice playback.

Required expressions:
- Neutral, happy, sad, surprised, serious, confused, relaxed.
- Expression transitions should be blended rather than abruptly swapped.

### 3.3 Art pipeline
Reference images are visual references, not production-ready rig assets. Production requires a layered source illustration (for example PSD), separated facial features and hair, reconstructed occluded regions, rigging, physics, expression definitions, and export/runtime assets. Obtain appropriate rights or permissions for the character and source artwork before distributing the model or app.

Expected deliverables depend on the chosen Live2D SDK version and may include model, texture, motion, expression, physics, and configuration files. Do not hard-code filenames or assume a Unity AssetBundle is directly loadable by a native Android renderer.

## 4. AI and emotion contract

The AI layer returns user-facing text plus a validated presentation directive. Example:

```json
{
  "message": "찾아봤어요. 관련 정보를 정리해 드릴게요.",
  "emotion": "happy",
  "expression": "smile",
  "motion": "nod",
  "intensity": 0.7
}
```

The presentation directive is structured data, not user-visible chat text. Validate values against allowlists; unknown values fall back to neutral. The AI must not directly issue arbitrary Android intents or arbitrary code.

Keep provider integration behind an interface. Gemini is the first provider target, with model and endpoint configurable. API credentials must not be embedded as a reusable plaintext secret in the APK. Use a user-supplied key stored with Android-appropriate protection for a personal prototype, or a trusted backend for stronger key protection.

## 5. Device actions and safety

Implement an allowlisted tool/action layer:
- Launch an installed app using its launch intent when available.
- Open supported Android settings screens.
- Open a web search or URL using an appropriate intent.
- Perform local calculations and app-provided utilities.

Potentially sensitive actions (sending messages, placing calls, deleting data, purchases, changing security settings, or actions with external side effects) require explicit confirmation and must not be inferred from ambiguous chat. AccessibilityService is not required for basic app launching or web search. If later added for user-authorized UI automation, it must be opt-in, narrowly scoped, clearly explained, and respect Android and Play policy requirements. Do not promise arbitrary control of every app.

## 6. Architecture

- Presentation: Kotlin; Jetpack Compose for in-app screens where practical.
- Floating runtime: foreground service and WindowManager overlays, with explicit start/stop lifecycle.
- Character: CharacterRenderer interface; Live2D implementation behind it.
- AI: AssistantProvider interface; Gemini implementation first.
- Conversation orchestration: typed messages, tool-call validation, cancellation, and streaming.
- Emotion: validated emotion/expression/motion mapping.
- Voice: SpeechInput and SpeechOutput interfaces.
- Actions: allowlisted AndroidActionExecutor.
- Persistence: Room for conversations/memory records; DataStore for preferences.
- Async: Kotlin Coroutines and lifecycle-aware scopes.

Suggested modules/packages:
- app/ui
- app/overlay
- app/character
- app/ai
- app/voice
- app/actions
- app/data
- app/settings

Avoid introducing every module as a separate Gradle module in the first iteration; package boundaries are sufficient until the project grows.

## 7. Data and privacy

- Store conversations locally by default.
- Provide controls to inspect and delete conversations and saved memories.
- Separate short-term conversation context from explicitly saved long-term memories.
- Do not silently promote sensitive or inferred information into long-term memory.
- Clearly disclose when prompts are sent to the configured AI provider.
- Provide a clear-data action and sensible retention defaults.
- Do not log API keys, full private conversations, or sensitive action payloads.

## 8. Android constraints

- Overlay permission is granted by the user in system settings and can be revoked at any time.
- Foreground-service start and notification behavior must follow the Android version and target SDK requirements.
- Re-check permissions and handle service termination, process recreation, screen rotation, and overlay removal.
- Do not claim the assistant can survive a user force-stop; Android intentionally prevents normal background restart after force-stop.
- Test on the user's target Android versions and Samsung One UI devices. Tablet and phone layouts should both be usable.

## 9. Milestones and acceptance criteria

### M0 — Repository baseline and build
- Confirm project structure, manifest, Gradle configuration, and current service behavior.
- Build a debug APK in CI.
- Document known gaps and remove misleading placeholder labels.

### M1 — Reliable floating shell
- First-run permission explanation and permission re-check.
- User-controlled foreground service start/stop.
- Draggable launcher and expanded character placeholder.
- Single/double tap and drag gestures do not conflict.
- Chat remains hidden until double-tap.
- Overlay can be dismissed and does not leave orphan windows.

### M2 — Character renderer
- Renderer abstraction and a verified SDK/runtime choice.
- Import/render a properly rigged upper-body model.
- Idle, blink, gaze, head, hair, and expression controls.
- Stable frame rate and memory behavior on target devices.

### M3 — Text AI chat
- Provider configuration and secure key handling appropriate to a personal build.
- Streaming text chat, errors, cancellation, and conversation persistence.
- User-editable/deletable memory controls.

### M4 — Emotion and voice
- Validated emotion directives.
- Smooth expression transitions.
- Optional speech input/output and synchronized mouth movement.

### M5 — Device actions
- Allowlisted app launching, web search, and settings actions.
- Explicit confirmation for consequential actions.
- Clear permission explanations and failure reporting.

### M6 — Release hardening
- Test on phone and tablet, Android lifecycle, permission revocation, network loss, API errors, and long-running sessions.
- Accessibility, battery, memory, and privacy review.
- Produce and verify a signed/reproducible personal-use APK workflow.

## 10. Current repository baseline (observed during design)

The existing repository already contains:
- A Kotlin MainActivity with Unity AssetBundle file selection.
- Overlay permission navigation.
- A FloatingAssistantService using WindowManager and a foreground-service notification.
- A basic floating text placeholder and a simple expression-command test chat panel.
- A GitHub Actions workflow that builds a debug APK.

Not yet implemented:
- Actual character rendering (the selected Unity bundle is stored but not rendered).
- Production chat/AI provider integration.
- Voice input/output and lip-sync.
- Persistent conversation/memory management.
- Real Android action tools.
- Final Blue Archive-inspired UI.

Important discrepancy to resolve during M0: README currently describes tapping the floating button as reopening the app, while the current service source contains a double-tap chat-panel gesture. Update documentation after verifying the intended behavior on-device.

## 11. Out of scope for v1
- Full-body 3D/VRM avatar.
- Unrestricted touch automation across arbitrary third-party apps.
- Always-listening microphone.
- Cloud-synced memory by default.
- Public distribution or commercial character licensing workflow.

---
This document records the approved product direction. Implementation details may be refined when SDK compatibility, Android behavior, and the available character source assets are verified; such refinements must preserve the approved user experience.
