# Unisoc SC8541E Smartwatch Provisioning & Deployment Guide

This guide details how to deploy **Muse Launcher** (`dev.puck.launcher`) onto smartwatches powered by the Unisoc (Spreadtrum) SC8541E chipset (e.g., SGTC / Android 8.1 Oreo Go Edition), permanently disabling the native Chinese stock launcher and providing a persistent floating assistant (**Taskbar**) for app drawer access.

---

## Hardware & Architecture Specs

| Property | Value |
|---|---|
| **SoC** | Unisoc / Spreadtrum SC8541E (`sl8541e_1h10_go`) |
| **CPU Architecture** | 32-bit ARM Cortex-A53 (`armeabi-v7a`) |
| **Android Version** | Android 8.1.0 Oreo Go Edition (API 27) |
| **Display** | 240x240 circular or square capacitive touchscreen |
| **Default OEM Launcher** | `com.sgtc.launcher` (`/system/priv-app/SgtcLauncher3/SgtcLauncher3.apk`) |
| **OEM Watchdog** | `com.sprd.engineermode` (`EmBootNetService`) |

---

## 1. Initial Device Unlocking (Dialer Secret Codes)

Standard Android Settings are intentionally obscured by OEM watch skins. To access the native Android Settings:

1. Open the dialer keypad on the watch face.
2. Dial the secret code:
   ```text
   *#*#0325#*#*
   ```
   *(Alternative Spreadtrum EngineerMode codes if `0325` is customized: `*#*#83781#*#*` or `*#*#83782#*#*`).*
3. The native Android Settings or Factory Menu will launch immediately.

---

## 2. Enable Developer Options & Persistent USB Debugging

1. Inside native Android Settings, go to **System** > **About Phone**.
2. Tap **Build number** 7 times rapidly until prompted with *"You are now a developer!"*.
3. Go back to **System** > **Developer options**.
4. Enable **USB debugging**.
5. Connect the watch via USB. On the RSA Authorization dialog, check **Always allow from this computer** and tap **OK**.
6. **Critical:** On SC8541E watches, the power management daemon powers down the USB PHY transceiver when the display sleeps. Configure the system to keep USB and screen persistent:
   ```bash
   adb shell settings put global stay_on_while_plugged_in 7
   adb shell settings put system screen_off_timeout 600000
   adb shell settings put global development_settings_enabled 1
   adb shell settings put global adb_enabled 1
   ```

---

## 3. Allow Sideloading (Unknown Sources)

1. Navigate to **Apps & notifications** > **Special app access** > **Install unknown apps**.
2. Select your browser or file manager and toggle **Allow from this source** to **ON** (or toggle **Security** > **Unknown sources** depending on build).

---

## 4. Backup & Deactivate Native OEM Launcher

The OEM stock launcher (`com.sgtc.launcher`) resides in `/system/priv-app/`.

### 4.1 Back up Stock APK
```bash
adb pull /system/priv-app/SgtcLauncher3/SgtcLauncher3.apk ./com.sgtc.launcher.apk
```

### 4.2 Disable for User 0
```bash
adb shell pm disable-user --user 0 com.sgtc.launcher
```

*(To restore at any time: `adb shell pm enable com.sgtc.launcher`)*.

---

## 5. Disable Unisoc OEM Kill Watchdog (`com.sprd.engineermode`)

Unisoc Go Edition firmwares ship with `com.sprd.engineermode`. Its background service (`EmBootNetService`) calls `am force-stop` on non-system third-party packages whenever the screen locks or during boot. To ensure Muse Launcher and companion services survive:
```bash
adb shell pm disable-user --user 0 com.sprd.engineermode
```

---

## 6. Install Muse Launcher as Primary Default Home

1. Build or locate the 32-bit compatible APK:
   ```bash
   ./scripts/gradle-mac.sh assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Lock Muse Launcher as default HOME:
   ```bash
   adb shell cmd package set-home-activity --user 0 dev.puck.launcher/dev.puck.appliance.ApplianceActivity
   ```
3. Verify default home activity:
   ```bash
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```

---

## 7. Install Floating Assistant (Taskbar) for App Access

Because Muse Launcher is designed as a focused AI companion appliance without a traditional app drawer, install **Taskbar** (`com.farmerbb.taskbar`) to launch other utilities (e.g. phone dialer, settings, Alextern Shortcut Creator).

### 7.1 Install & Grant Privileges
```bash
adb install -r Taskbar.apk
adb shell appops set com.farmerbb.taskbar SYSTEM_ALERT_WINDOW allow
adb shell appops set com.farmerbb.taskbar GET_USAGE_STATS allow
adb shell dumpsys deviceidle whitelist +com.farmerbb.taskbar
```

### 7.2 Bind to System Server for Guaranteed Boot Resurrection
Binding Taskbar to Android's accessibility and notification frameworks ensures `system_server` auto-starts it on boot:
```bash
adb shell settings put secure enabled_accessibility_services "com.farmerbb.taskbar/com.farmerbb.taskbar.service.PowerMenuService"
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure enabled_notification_listeners "com.farmerbb.taskbar/com.farmerbb.taskbar.service.NotificationCountService:dev.puck.launcher/dev.puck.appliance.MuseNotifications"
```

### 7.3 Enable "Launch on Boot" in Taskbar Settings
1. Open Taskbar settings:
   ```bash
   adb shell am start -n com.farmerbb.taskbar/.MainActivity
   ```
2. Turn the main switch in the top toolbar to **ON**.
3. Tap **General settings**.
4. Scroll down 3 times to view the bottom of the 240x240 screen.
5. Check **Launch on boot**.

> **Unisoc Boot Note:** On SC8541E, radio and cellular daemons initialize first. `BOOT_COMPLETED` broadcast is dispatched approximately 30-45 seconds after the display lights up. Taskbar's floating handle will appear immediately after.

---

## 8. One-Click Provisioning Script

Run the automated provisioning script directly:
```bash
./scripts/provision-unisoc-sc8541e.sh
```
