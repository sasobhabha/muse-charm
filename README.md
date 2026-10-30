# Muse Charm

An open source Android companion and standalone wearable AI appliance for your own Muse. Put a spare phone on your
desk, slip it in your pocket, or turn an ultra-affordable Unisoc SC8541E Android smartwatch into a dedicated hardware **Muse Charm**: tap or hold your avatar to talk, share photos or short videos, and let a small animated companion present replies and notifications.

**Community preview, 0.7.1.** Android 8+ (API 26) on 32-bit (`armeabi-v7a`), 64-bit (`arm64-v8a`), or `x86_64` devices.
Physical-device evidence verified on the Unihertz Jelly Star (Android 13) and Unisoc SC8541E smartwatches (Android 8.1 Go). No root
is required for screen controls, pairing, camera or optional Home-screen use.

[Download community preview](https://github.com/sasobhabha/muse-charm/releases) ·
[Install and pair](docs/INSTALL.md) · [Compatibility](docs/COMPATIBILITY.md) ·
[Unisoc SC8541E Smartwatch Guide](docs/UNISOC_SC8541E.md) ·
[Privacy](docs/PRIVACY.md) · [Contribute](CONTRIBUTING.md)

## Turn a Unisoc SC8541E Smartwatch into a Standalone Muse Charm

Instead of purchasing proprietary AI hardware, you can turn an ultra-affordable ($20–$35) 4G Android smartwatch powered by the Unisoc (Spreadtrum) SC8541E processor (such as SGTC and similar Android 8.1 Oreo Go wearables) into a dedicated, autonomous **Muse Charm** wearable:

- **Hardware Specs:** 32-bit ARM Cortex-A53 (`armeabi-v7a`), 240x240 touchscreen, built-in microphone, speaker, front-facing camera, and standalone 4G LTE cellular + Wi-Fi.
- **Dedicated Kiosk Experience:** Completely replaces the native OEM launcher (`com.sgtc.launcher`) with Muse Charm as the persistent default Android Home screen.
- **Watchdog Taming:** Automatically disables Unisoc's aggressive power watchdog (`com.sprd.engineermode` / `EmBootNetService`), preventing the system from killing sideloaded AI background processes and ensuring immediate resurrection on boot.
- **Floating App Drawer (Taskbar):** Binds a non-intrusive floating assistant overlay to the Android System Server accessibility engine, giving you instantaneous access to the cellular phone dialer, settings, and shortcut utilities without disrupting the dedicated charm UI.
- **One-Click Automated Provisioning:** Run `./scripts/provision-unisoc-sc8541e.sh` to configure permissions, persistent USB debugging keep-alive, disable OEM bloat, and lock Muse Charm as home in seconds.

Check out the [Unisoc SC8541E Setup & Provisioning Guide](docs/UNISOC_SC8541E.md) for dialing secret unlock codes (`*#*#0325#*#*`) and full deployment instructions.


## What works

- Tap the avatar once to record and again to send, or hold it and release to send.
  Double tap opens the camera. A double tap does not start the microphone. Fast offline English captions
  show words while recording. Muse receives the original audio.
- Take a photo or hold the camera's video control to record. The attachment goes
  with your next voice question, or send the video with its own audio. No
  automatic description request is added.
- One persistent Muse conversation, reply bubbles you can swipe away, and
  optional Android read-aloud.
- Optional notification access: local source-labeled bubbles, an unread count,
  swipe dismissal and Clear all. An optional setting hides clearable Android
  tray alerts after mirroring; calls, alarms and ongoing alerts stay visible.
- Optional hardware-button calibration on phones that expose an extra key.
  The firmware-bound physical test gate is preserved.

## Phones, Wearables, and the Hardware Button

Muse Charm does not require a Jelly Star or a hardware button. It targets Android 8+ with
Android (arm64-v8a, armeabi-v7a or x86_64); other ABIs are not supported. Normal phones and smartwatches
use the avatar and camera controls. Camera and BLE advertising support vary.

On the Jelly Star, the extra red button can be physically calibrated in Setup.
Other extra keys may work if their press and release reach Muse Charm while it is open.
Short press wakes, double tap opens camera, hold records voice and release sends;
in camera, a short tap takes a photo and hold/release records/stops video. Volume,
power, Home and Back keys are excluded from calibration. No key is armed by
installation. Locked/background activation is a separate device-specific capability
and is not universally supported or certified. See [hardware buttons](docs/BUTTONS.md).

## Connect Muse

> [!IMPORTANT]
> **Your Muse SDK token MUST be your own.**  
> You must generate your own personal token from your own account at [gadgets.muse.ai/settings/sdk-tokens](https://gadgets.muse.ai/settings/sdk-tokens).  
> - **Never share, leak, or commit tokens.** No default or shared tokens ship in this repository.  
> - **Never use someone else's token.** Tokens are bound to personal accounts and subject to [Muse's token terms](https://gadgets.muse.ai/sdk-terms).  
> - Keep your token safe in your uncommitted `local.properties` (or enter it directly on device during setup).

Use **guided setup** on first launch. Each person supplies their own personal
SDK token and pairs from Muse on a **second phone**. No shared developer token,
backend account or credentials ship in the app. A correlated reply test verifies
connection before setup marks it successful. Same-phone Bluetooth pairing has
not been established. The second phone is needed for initial pairing only;
afterward this phone connects directly over the internet. Installing Muse on
the same phone does not currently replace that initial setup step.

Muse's source SDK is Apache-2.0, but service tokens have separate
[personal-use terms](https://gadgets.muse.ai/sdk-terms). Token-bearing public
products are restricted; we have not established permission for a public app-store
release with user-supplied tokens. This credential-free community preview lets each user build or install the app
and supply their own token. Public app-store/service distribution still needs
clarification from Meta; this is not an approved commercial integration. Muse Charm is independent
and is not made by or endorsed by Meta.

## Build

Install JDK 21, Python 3.11, Android SDK Platform 36 / Build Tools 36.0.0, and set
`sdk.dir` in your local `local.properties` (or set `ANDROID_HOME`).

```sh
python3.11 scripts/fetch-models.py
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`. A Mac helper is available:
`./scripts/gradle-mac.sh`. The optional large English caption model is fetched
with `--enhanced` and bundled only in debug builds. Release uses the fast model.

[Release process](docs/RELEASING.md) explains signing, source exports and checks.
CI builds and checks source without credentials; live Muse tests are opt-in.

## Limits

Locked/suspended hardware activation is device-specific and not certified.
Setting Muse Charm as Home does not turn an ordinary phone into an enforced kiosk.
No reset, rooting, device-owner enrollment or navigation restriction is needed
for the normal setup. Calls and message drafts open the user's phone apps;
those apps own the final Call/Send. Native Muse push delivery remains unverified;
notification presentation uses Android's optional local notification listener.
Video is SD and capped at 8 MB. Captions are English and read-aloud uses Android
voices, not a Muse voice API. Custom Muse avatars are animated locally with procedural breathing, bobbing,
tilting, speaking motion and a notification look-up pose. Original artwork is
kept intact; no paid image service or developer account is required. Generic
Muse profiles use the built-in animated companion. Profile export remains
service-dependent; a missing export keeps the existing/fallback companion.

Notifications place your avatar at the bottom-left, looking toward the bubbles
above it. Gesture instructions live in the **Info** pop-up, keeping Home minimal.
Standalone video sends its own audio through a locally extracted WAV alongside
the MP4 when present; video with a separate voice question uses that recording.
No describe-video instruction is inserted.

## Developer mode

Open **Settings → Developer mode** for hardware diagnostics, Android settings,
optional server configuration and appliance maintenance. Ordinary use needs none
of those controls. This is separate from Muse's own Developer mode, which you
still enable in Muse on the pairing phone. Tap gestures are the primary controls;
a verified hardware key remains optional.

## License

Muse Launcher's original code: Apache-2.0. Third-party notices are in `third_party/`.
Personal avatars and Muse's Jollybot artwork are not distributed. The source
export excludes private development files, recordings and account history.
