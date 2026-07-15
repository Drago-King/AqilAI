# AQIL AI

AQIL AI is a polished Android Accessibility-based phone agent starter inspired by modern assistant apps. It includes a dark classic UI, an AQIL logo, voice and keyboard command consoles, a floating accessibility circle, and an optional OpenAI API-key field for smarter reasoning.

## What it can do now

- Show a premium dark setup screen with AQIL branding.
- Save an OpenAI API key locally in private app storage.
- Accept typed commands in the app.
- Accept voice commands in the app.
- Start a floating AQIL circle after overlay permission is granted.
- Expand the floating circle into keyboard + microphone controls.
- Run Accessibility actions for back, home, recents, and scroll gestures.
- Open YouTube/music searches, food/order searches, web searches, and gallery image browsing.
- Use local fallback command handling when no API key is configured.
- Use OpenAI Responses API when a key is configured.

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

## Using the AI key

Open AQIL AI, paste your OpenAI API key in **Add AI brain**, and tap **Save API Key**. The key is stored only in this app's private Android preferences.

## Safety note

Android does not allow any app to silently control the entire phone without explicit user permissions. AQIL AI uses official Android Accessibility APIs and always requires you to grant permissions manually.
