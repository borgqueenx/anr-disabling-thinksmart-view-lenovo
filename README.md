# ThinkSmart System UI ANR Silencer (experimental source)

Target: Lenovo ThinkSmart View / `starfire`, LineageOS 15.1 unofficial build
`15.1-20240602-UNOFFICIAL-starfire`, Android 8.1 (API 27), 32-bit ARM userspace,
Magisk + Zygisk + LSPosed.

## What it does

A legacy Xposed module that hooks `com.android.server.am.AppErrors.handleShowAnrUi(Message)`
in `system_server`. If it can positively identify the target process as
`com.android.systemui`, it skips that dialog handler and attempts to clear the
Android 8.x `ProcessRecord.notResponding` and `notRespondingReport` fields.
It does not kill System UI. For other processes, the original method runs.

This is an **experimental source project**, not a tested APK. It has not been
compiled or tested against the user's exact ODEX ROM. The code is deliberately
fail-open: if the message shape or process cannot be identified, the original
ANR handler runs.

## Important limitations

- Suppressing the dialog does not prove the underlying ANR is harmless.
- This depends on the ROM retaining the Android 8.x class/method/field names.
- If the process payload differs on this ROM, the hook logs `suppressed` zero
  times and will not suppress the popup.
- LSPosed module scope must include the Android framework / `android` package
  so the hook loads in `system_server`.
- Do not assume the module works until verified in LSPosed logs.
- If the tablet becomes unstable, disable this module in LSPosed and reboot.
  The original framework files are not modified.

## Build

Build with Android Studio or Gradle using JDK 11 and Android SDK Platform 33.
The project uses Android Gradle Plugin 7.4.2 and Xposed API 82 from
`https://api.xposed.info/`.

From the project root:

    gradle assembleDebug

APK output:

    app/build/outputs/apk/debug/app-debug.apk

Install the APK, open LSPosed, enable the module, and set its scope to the
Android framework (`android` / system framework). Reboot to load the hook.

## Verification

After reboot, inspect LSPosed logs for:

    ThinkSmartAnrSilencer: loaded in system_server; hooks=1

When the targeted dialog would otherwise occur, logs should show:

    ThinkSmartAnrSilencer: suppressed System UI ANR dialog

If `hooks=0`, the method signature differs on this ROM and the module is not
hooking anything. If the dialog still appears and there is no suppression log,
disable the module rather than trying random framework changes.

## Disable / rollback

Disable the module in LSPosed and reboot. No `/system` files are changed.


## Beginner-friendly cloud build (no Android Studio required)

This ZIP includes a GitHub Actions workflow at `.github/workflows/build-apk.yml`.
To build in the cloud:
1. Create a new empty repository on GitHub.
2. Upload the contents of this project (including the hidden `.github` folder) to the repository's `main` branch. GitHub's web uploader may hide dot-folders; if so, use the GitHub mobile/desktop website with the full project files, or upload through GitHub Desktop.
3. Open the repository's **Actions** tab and select **Build ThinkSmart ANR Silencer APK**.
4. Click **Run workflow** if it has not started automatically.
5. When the run finishes successfully, open the run, scroll to **Artifacts**, and download `ThinkSmartAnrSilencer-debug-apk`.
6. Extract the artifact ZIP; it contains `app-debug.apk`.

A successful build only means the source compiled. It does not prove the hook matches the tablet's framework or that it suppresses the popup. Enable the module only after checking the source/build result and keep LSPosed available so it can be disabled if necessary.
