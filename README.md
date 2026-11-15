# Bubble Translate

Android floating screen translator. The floating round bubble toggles live screen OCR and translation. Finglish conversion uses the bundled offline dictionary; other selected languages use ML Kit models.

## Download
After the GitHub Actions build succeeds, install the APK from [Latest APK release](https://github.com/mvarkati1390-lgtm/Bubble-Translate/releases/download/latest/app-debug.apk).

## Build
GitHub Actions builds a debug APK on every push to `main` and updates the `latest` release.

## Notes
- Android 9+ (minSdk 28).
- Screen capture requires the Android system confirmation each time.
- Grant “Display over other apps” permission before starting.
- ML Kit translation models need an initial internet download; Finglish mode is offline.
