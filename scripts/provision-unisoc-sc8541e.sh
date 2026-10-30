#!/usr/bin/env bash
# ==============================================================================
# Unisoc SC8541E Smartwatch Automated Provisioning Script
# Targets: Unisoc SC8541E (e.g., SGTC / Android 8.1 Oreo Go)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=== Unisoc SC8541E Provisioning Tool ==="

# 1. Pre-flight ADB check
if ! command -v adb &>/dev/null; then
    echo "[-] Error: 'adb' command not found. Please install Android Platform Tools and add to PATH."
    exit 1
fi

DEVICE_ID="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [[ -z "${DEVICE_ID}" ]]; then
    echo "[-] Error: No authorized ADB device found."
    echo "    1. Dial *#*#0325#*#* on the watch to open Android Settings."
    echo "    2. Enable Developer Options (tap Build 7x) and toggle USB Debugging ON."
    echo "    3. Connect USB cable and accept the RSA key prompt on the watch face."
    exit 1
fi

echo "[+] Connected to device: ${DEVICE_ID}"
DEVICE_MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
DEVICE_BUILD="$(adb shell getprop ro.build.display.id | tr -d '\r')"
echo "[+] Model: ${DEVICE_MODEL} | Build: ${DEVICE_BUILD}"

# 2. Configure USB keep-alive and screen timeout
echo "[+] Setting persistent display and USB keep-alive..."
adb shell settings put global stay_on_while_plugged_in 7
adb shell settings put system screen_off_timeout 600000
adb shell settings put global development_settings_enabled 1
adb shell settings put global adb_enabled 1

# 3. Disable Unisoc OEM kill watchdog
echo "[+] Disabling OEM watchdog (com.sprd.engineermode)..."
adb shell pm disable-user --user 0 com.sprd.engineermode 2>/dev/null || true

# 4. Backup & disable native stock launcher
echo "[+] Checking for native launcher (com.sgtc.launcher)..."
SGTC_PATH="$(adb shell pm path com.sgtc.launcher 2>/dev/null | sed -e 's/package://' | tr -d '\r' || true)"
if [[ -n "${SGTC_PATH}" ]]; then
    BACKUP_NAME="com.sgtc.launcher.backup.apk"
    if [[ ! -f "${BACKUP_NAME}" ]]; then
        echo "[+] Pulling backup of stock launcher to ${BACKUP_NAME}..."
        adb pull "${SGTC_PATH}" "${BACKUP_NAME}" || true
    fi
    echo "[+] Disabling native launcher for user 0..."
    adb shell pm disable-user --user 0 com.sgtc.launcher || true
else
    echo "[i] Native launcher already disabled or not present."
fi

# 5. Install / Update Muse Launcher
MUSE_APK="${1:-${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk}"
if [[ -f "${MUSE_APK}" ]]; then
    echo "[+] Installing Muse Launcher from: ${MUSE_APK}..."
    adb install -r -g "${MUSE_APK}"
    echo "[+] Setting Muse Launcher as default HOME activity..."
    adb shell cmd package set-home-activity --user 0 dev.puck.launcher/dev.puck.appliance.ApplianceActivity
    adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
else
    echo "[!] Muse Launcher APK not found at ${MUSE_APK}."
    echo "    Run './scripts/gradle-mac.sh assembleDebug' to build it first."
fi

# 6. Configure Taskbar floating assistant (if installed or APK available)
TASKBAR_INSTALLED="$(adb shell pm list packages com.farmerbb.taskbar | tr -d '\r')"
if [[ -z "${TASKBAR_INSTALLED}" ]]; then
    TASKBAR_CANDIDATE="${HOME}/Taskbar.apk"
    if [[ -f "${TASKBAR_CANDIDATE}" ]]; then
        echo "[+] Sideloading Taskbar from ${TASKBAR_CANDIDATE}..."
        adb install -r -g "${TASKBAR_CANDIDATE}"
        TASKBAR_INSTALLED="package:com.farmerbb.taskbar"
    fi
fi

if [[ -n "${TASKBAR_INSTALLED}" ]]; then
    echo "[+] Configuring Taskbar overlay permissions and battery optimization..."
    adb shell appops set com.farmerbb.taskbar SYSTEM_ALERT_WINDOW allow
    adb shell appops set com.farmerbb.taskbar GET_USAGE_STATS allow
    adb shell dumpsys deviceidle whitelist +com.farmerbb.taskbar || true

    echo "[+] Binding Taskbar to system server accessibility & notification listeners..."
    adb shell settings put secure enabled_accessibility_services "com.farmerbb.taskbar/com.farmerbb.taskbar.service.PowerMenuService"
    adb shell settings put secure accessibility_enabled 1
    adb shell settings put secure enabled_notification_listeners "com.farmerbb.taskbar/com.farmerbb.taskbar.service.NotificationCountService:dev.puck.launcher/dev.puck.appliance.MuseNotifications"
    
    echo "[+] Launching Taskbar service..."
    adb shell am startservice com.farmerbb.taskbar/.service.NotificationService 2>/dev/null || true
    adb shell am startservice com.farmerbb.taskbar/.service.StartLibraryService 2>/dev/null || true
fi

echo "=========================================================="
echo "[✓] Provisioning complete!"
echo "    - Watchdog disabled: com.sprd.engineermode"
echo "    - Stock launcher disabled: com.sgtc.launcher"
echo "    - Default Home: dev.puck.launcher"
echo "    - Display & USB persistent: Stay on while plugged in"
echo "=========================================================="
