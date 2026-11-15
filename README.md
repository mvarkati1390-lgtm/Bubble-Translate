# Bubble Translate

Android floating screen translator with simultaneous screen recording. After the Android screen-capture confirmation, live translation and video recording start together.

## Audio controls
- **Phone audio**: captures supported game/app playback audio.
- **Outside audio**: captures microphone/environment audio (requires Android microphone permission).
- The two controls are independent and can both be enabled.

Videos are saved to `Movies/BubbleTranslate`. The floating bubble toggles live translation; holding it opens recording stop and exit controls.

## Build and download
GitHub Actions builds the debug APK on pushes to `main` and publishes it to the `latest` release.

## Notes
- Android 10+ (minSdk 29).
- Screen capture requires Android's system confirmation each time the app is started.
- “Display over other apps” permission is required for the floating translator.
- Microphone permission is separate and is requested only when outside audio is enabled.
- ML Kit translation models need an initial internet download; Finglish conversion is offline.
