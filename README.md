# android-tolk

Tolk is a personal Android translator that calls LLMs through your own OpenRouter key.

- **Text:** auto-detects the source language, takes an optional situation ("at a pharmacy") and a register (natural, formal, casual), streams the result, and can explain word choices.
- **Photo:** snap or pick an image; the model reads it and returns each piece of text with its translation and a short note (what a dish is, what a sign means).
- **Floating translator:** a button over other apps (an accessibility service). Tap it to translate the page on screen; screens without readable text are sent as a screenshot. Drag it onto the cross at the bottom to close it, and bring it back from the Quick Settings tile.
- **From other apps:** select text and pick *Translate with Tolk*, or share text or an image to Tolk.

The key is entered in the app, encrypted with an Android Keystore key, and never leaves the device except in requests to OpenRouter. With "Don't let providers store my data" on, requests carry `provider.data_collection = "deny"`.

The default model is `google/gemini-3.8-flash` with `reasoning.effort = "minimal"` for text, which cut time to the first word from about 7.6 s to 2.1 s in testing. Any OpenRouter model can be picked in Settings.

## Build

```sh
./gradlew :app:assembleRelease      # minified, about 10 MB idle on device
adb install -r app/build/outputs/apk/release/app-release.apk
./gradlew :app:testDebugUnitTest
```

Min SDK 33 (Android 13). The release build is signed with the debug key, so it installs over a debug build.

The Atkinson Hyperlegible Next font is under the SIL Open Font License; see `OFL-AtkinsonHyperlegibleNext.txt`.
