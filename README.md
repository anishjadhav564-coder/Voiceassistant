# Voice Assistant (Android, runs in the background)

A beginner-friendly Android app. It runs as a **foreground service**, listens for a
wake word (default: "hey assistant"), sends your question to the **OpenAI API**, and
speaks the answer aloud.

Example: say *"hey assistant what is the capital of France"* and it replies by voice.

## What you need

1. **Android Studio** (free): https://developer.android.com/studio
2. An **OpenAI API key** from https://platform.openai.com/api-keys
   (API usage is billed separately from a ChatGPT subscription).
3. An Android phone (Android 8.0+) with USB debugging on, or an emulator with a microphone.

## Run it

1. Open Android Studio, choose **Open**, and select this `VoiceAssistant` folder.
2. Wait for Gradle sync to finish.
3. Press the green **Run** button.
4. In the app, paste your API key, tap **Start assistant**, and allow the microphone
   and notification permissions. Tap **Start assistant** once more.
5. Say the wake word followed by your question. You can now leave the app; it keeps
   listening, and a notification shows it is active.

## How it works

| File | Job |
| --- | --- |
| `MainActivity.kt` | Screen for the API key, wake word, start/stop, permissions |
| `VoiceService.kt` | Background service: listen, detect wake word, ask, speak |
| `OpenAiClient.kt` | Calls OpenAI Chat Completions and keeps short chat history |

The model is set in `OpenAiClient.kt` (`MODEL = "gpt-4o-mini"`). Change it to any chat model
your account can use.

## Limits you should know about

- **Battery:** continuous listening drains the battery. Stop the assistant when not needed.
- **Wake word accuracy:** this uses Android's built-in speech recognizer, not a dedicated
  wake-word engine. It can miss the phrase, and some phones play a beep or need internet
  for recognition. For a more reliable always-on wake word, look at Picovoice Porcupine.
- **Background restrictions:** some phone brands (Xiaomi, Oppo, Vivo, Samsung) kill background
  apps. Turn off battery optimization for this app in Settings.
- **Microphone while screen is off:** Android 14+ allows it only for a foreground service
  started while the app is visible, which is what this app does.
- **API key security:** the key is stored on the phone in plain app storage. That is fine for
  personal use. Before sharing the app with others, put the key on your own server and have
  the app call that server instead, otherwise anyone could extract your key.
- **Cost:** every question uses API credit. Set a monthly limit in the OpenAI dashboard.

## Ideas for next steps

- Add a Compose or chat-history screen showing past questions and answers.
- Start automatically after the phone reboots (`BOOT_COMPLETED` receiver).
- Swap in OpenAI's speech-to-text and text-to-speech APIs for better quality.
