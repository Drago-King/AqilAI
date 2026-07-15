# AQIL AI

AQIL AI is a polished Android Accessibility-based assistant starter with a premium dark UI, navigation drawer-style sections, configurable AI providers, voice settings, a draggable floating assistant, and safe automation scaffolding.

## Sections

- Home
- Chat
- AI Agent
- Floating Assistant
- History
- Settings
- AI Providers
- Voice
- Appearance
- Permissions
- Accessibility
- Automation
- Advanced
- About

## AI Providers

AQIL does not hardcode OpenAI. Configure any OpenAI-compatible endpoint:

- OpenAI Compatible
- OpenRouter
- Groq
- Google Gemini-compatible gateways
- Local Ollama OpenAI-compatible endpoints
- Any custom OpenAI-compatible Base URL

Fields include Provider, Base URL, AI Model, API Key, Save Provider, Test Connection, and status output.

## Voice

The Voice page stores ElevenLabs configuration fields for API key, Voice ID, speed, stability, style, and TTS enable/disable.

## Floating Assistant

The AQIL floating bubble starts after overlay permission, restarts when returning to the app, survives service restarts with `START_STICKY`, can be dragged anywhere, remembers its position, expands into keyboard + mic controls, and long-presses back into the main app.

## Accessibility Automation

With user-granted Accessibility permission, AQIL can run basic actions including back, home, recents, quick settings, notifications, scrolling, focused text entry, app opening, camera/settings display, media/gallery, and web searches. Sensitive automations such as sending messages, orders, purchases, or sharing files should always request explicit final confirmation.

## Build on Android with GitHub Actions

1. Push this repository to GitHub.
2. Open the **Actions** tab.
3. Run **Build Android APK**.
4. Download the `aqil-ai-debug-apk` artifact and install the APK on your phone.

## Required phone permissions

- Accessibility: lets AQIL read screen content and perform gestures.
- Display over other apps: shows the floating circle.
- Microphone: voice commands.
- Photos/images: gallery document lookup starter.

## Safety note

Android does not allow any app to silently control the entire phone without explicit user permissions. AQIL AI uses official Android Accessibility APIs and always requires you to grant permissions manually.

## True Agent Upgrade

AQIL now has a tool registry so the model can decide whether to call Accessibility actions, Gallery Search, WhatsApp preparation, Camera, File Manager, or System actions. Provider responses may return tool JSON, and dangerous workflows require confirmation before AQIL continues.

## Gallery AI

Gallery search scans recent MediaStore images and uses Google ML Kit Text Recognition plus Image Labeling for OCR/object-label search. This enables queries such as “community certificate” to match filenames, recognized text, and visual labels before asking for confirmation to share.

## Voice Output

The Voice page can call the ElevenLabs text-to-speech streaming endpoint, cache the returned speech audio, play it with Android `MediaPlayer`, and stop playback when interrupted.
