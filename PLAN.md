# Plan

## Next: voice conversation mode

A two-person conversation on one phone, the way Google Translate's conversation mode works, but translated by the LLM.

- Split screen: each half belongs to one person and one language, with the far half drawn upside down so the other person can read it across a table.
- Each person taps their half to talk. The phone listens, translates, shows the translation on the other half and reads it aloud.
- Speech to text: Android's `SpeechRecognizer` first, since it's free and often works on the device. If it's weak for a language, fall back to sending the recording to an audio-capable model through OpenRouter (Gemini Flash accepts audio).
- Text to speech: Android's `TextToSpeech`. Languages without a voice show the text only.
- Needs the microphone permission.
- Keep the conversation history on screen so either person can scroll back, and let a tap on a line replay it.

## Backlog

- **In-place overlay:**
  - When a translation is longer than its original, the box grows down and can cover the next line. Keep it inside the original area, or fade the overflow.
  - Boxes over crowded icon rows overlap (for example, the "Download PDF" button on Wikipedia).
  - The floating button is drawn under the overlay while it's open; bring it back on top.
  - Cache translated lines so translating again after a small scroll only sends the new ones.
- **In-place for screenshots** (games, images): ask the model for each text's position and draw over it. Positions guessed from an image are less exact, so the list view stays the default there.
- **Memory:** run the floating translator in its own process so the main screen's graphics caches don't stay loaded (about 30 MB instead of up to 70 MB after the app has been opened). Settings storage would need to become safe across processes first.
- **History** of past translations and photos.

## Known device quirks

- **ColorOS Safe Center** turns off an accessibility service that draws a transparent full-screen overlay that catches touches. Keep any full-screen overlay non-touchable.
