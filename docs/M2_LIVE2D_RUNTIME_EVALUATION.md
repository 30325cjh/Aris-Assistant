# M2 Live2D Runtime Evaluation

**Evaluation date:** 2026-11-16  
**Project target:** personal-use Android application, minSdk 26, compileSdk/targetSdk 35  
**Decision:** Keep `PlaceholderCharacterRenderer` active. Cubism Native with OpenGL is the leading candidate, but it is **not integrated or build-verified in this repository**.

## Findings from official sources

1. Live2D's current platform support matrix lists **Cubism Native / OpenGL on Android**. This confirms platform support, not compatibility with this app's exact Gradle/NDK configuration.
   - https://docs.live2d.com/en/cubism-sdk-manual/platform/

2. The official `Live2D/CubismNativeSamples` repository contains native samples and an OpenGL sample. Its README says the Cubism Native Framework is a submodule and **Cubism Core is downloaded separately**; the Core binary is not included in that repository.
   - https://github.com/Live2D/CubismNativeSamples

3. The official sample repository currently documents this Android development toolchain:
   - Android NDK 29.0.14206865
   - Android SDK 36.1.0
   - CMake 4.1.2
   - Its documented operation table includes Android 16 devices.
   
   The Aris app currently uses AGP 8.7.3, Gradle 8.9, compileSdk/targetSdk 35, minSdk 26, and has no NDK/CMake or `externalNativeBuild` configuration. Therefore the sample's current environment cannot be treated as a drop-in dependency. A controlled toolchain alignment and a separate sample build are required before integration.

4. The SDK download page requires acceptance of the Live2D Proprietary Software License Agreement and Live2D Open Software License Agreement. The download page lists Cubism 5 SDK for Native R5 in its update history.
   - https://www.live2d.com/en/sdk/download/native/

5. Live2D states that SDK verification/development can start without a publication license, while publication rules depend on the usage plan. Individuals and small-scale businesses are generally exempt from the publication license/payment for non-expandable applications, but **Expandable Applications are an exception** and require review and a special agreement. The AI/chatbot usage page directs users to the applicable plan flow.
   - https://www.live2d.com/en/sdk/license/
   - https://www.live2d.com/en/sdk/license/expandable/

6. The official Cubism Editor system requirements list Windows 10/11 desktop or macOS, with PSD/PNG input. Android tablets are not listed as a supported Editor platform.
   - https://www.live2d.com/en/cubism/download/spec/5_3_00_b1/
   - https://docs.live2d.com/en/cubism-editor-manual/cubism-editor-activation/

## Project-specific compatibility assessment

| Area | Current state | Assessment |
|---|---|---|
| Android runtime | Native OpenGL is listed as supported | Candidate is viable in principle |
| Gradle/AGP | AGP 8.7.3, Gradle 8.9 | Not proven compatible with current Native sample toolchain |
| NDK/CMake | Not configured in Aris | Required work before Native integration |
| Cubism Core | Not present | Must be obtained under the SDK terms |
| Native Framework | Not present | Must be obtained from the official sample/framework source and reviewed |
| Character model | No layered PSD or rigged Cubism model in this repo | Blocks a real model render test |
| Asset authoring | Cubism Editor supports Windows/macOS, not Android listed | Tablet-only authoring is a practical blocker |
| Licensing | SDK agreements must be accepted; publication plan depends on app distribution and expandability | Do not assume personal-use status waives every requirement |

## Recommendation

1. Do not add the SDK binaries, Core library, or sample project to Aris yet.
2. Keep the renderer seam and placeholder intact.
3. Obtain an original or appropriately authorized layered character source. Reference images and a Unity AssetBundle URI are not a rigged Cubism model.
4. Arrange access to a Windows/macOS Cubism Editor environment for mesh/parameter/physics rigging, or identify a tablet-capable authoring pipeline that can export a runtime-compatible model. Verify its export rights and runtime license separately.
5. Once the SDK archive and a real model are available, build the official Android OpenGL sample in isolation with its documented NDK/SDK/CMake versions. Then align Aris's Android toolchain deliberately and implement a Native/OpenGL-backed `CharacterRenderer`.
6. Before any public release, classify the actual AI-avatar app under Live2D's AI/chatbot and Expandable Application licensing guidance. Personal use and public distribution are different cases.

## Explicit non-claims

- No Live2D SDK or Cubism Core is integrated.
- No model, PSD, rig, texture, motion, or physics asset has been generated.
- No Android tablet authoring capability is claimed.
- No compatibility build of the official Native sample has been run in this repository.
