# AQIL AI

AQIL AI is a secure Android Accessibility-based personal agent starter. It provides:

- A floating AQIL accessibility circle.
- Tap-to-expand microphone and keyboard command controls.
- Long-press-to-open the main app.
- Accessibility actions for back, home, recents, and scroll gestures.
- Voice/text commands that open YouTube, music searches, food/order searches, and gallery image browsing.

## Build on Android with GitHub Actions

1. Push this repository to GitHub.
2. Open the **Actions** tab.
3. Run **Build Android APK**.
4. Download the `aqil-ai-debug-apk` artifact and install the APK on your phone.

## Required phone permissions

After installing, open AQIL AI and enable:

- Accessibility: lets AQIL read screen content and perform gestures.
- Display over other apps: shows the floating circle.
- Microphone: voice commands.
- Photos/images: gallery document lookup starter.

## Safety note

Android does not allow any app to silently control the entire phone without explicit user permissions. AQIL AI uses official Android Accessibility APIs and always requires you to grant permissions manually.
