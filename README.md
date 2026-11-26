# Something

Because the Nothing speaker sounded like nothing.

Something is a small Android app that makes **voice-mode audio louder on the speaker**: voice chats with AI apps,
internet calls, and phone calls that your carrier's app carries over Wi-Fi. No root, no bootloader unlock, no wipe.

It was written for the Nothing Phone (3), where speakerphone voice audio is very quiet even at full volume
([thread 1](https://nothing.community/d/38006-nothing-phone-3-in-call-speaker-volume-too-low),
[thread 2](https://nothing.community/d/52492-low-call-volume-weak-audio-on-nothing-phone-3-help)).
It is not made by, or affiliated with, Nothing.

## What it does and does not do

| Audio | Boosted? |
| --- | --- |
| Voice chats in apps (apps that play in Android's "voice communication" mode) | Yes |
| Internet calls (apps that use voice mode) | Yes |
| Phone calls your carrier app carries as app audio (seen with Google Fi on Wi-Fi) | Yes |
| Ordinary cellular calls | **No.** That sound goes from the modem to the speaker without passing through Android's app audio, so no app can reach it. |
| Music and video | No, on purpose. They are already loud. |

Tested on one Nothing Phone (3), Nothing OS 5.0 (Android 17). It uses standard Android APIs, so it may work on
other phones, but that is untested.

## Install

You need a computer with [adb](https://developer.android.com/tools/adb) once, for one permission.

1. Download the APK from [Releases](../../releases) and install it on the phone.
2. On the phone, turn on **Developer options > USB debugging** (or Wireless debugging) and connect to the computer.
3. Run this once:

   ```
   adb shell pm grant app.something.voiceboost android.permission.DUMP
   ```

4. Open **Something**. Leave **Boost voice chats** on and set **Extra loudness** (starts at +9 dB; above +15 dB may distort).
5. In **Settings > Apps > Something > App battery usage**, choose **Unrestricted** so the system does not stop it.

The permission survives app updates and reboots. If you uninstall and reinstall, run step 3 again.

## How it works

Android lets an app attach an audio effect to another app's audio session if it knows the session id. Something
reads the list of active players from `dumpsys audio` (this is what the `DUMP` permission is for), picks the ones
playing with `USAGE_VOICE_COMMUNICATION`, and attaches a `LoudnessEnhancer` to each with the gain you chose. It
removes the effect when the stream ends. Nothing leaves the phone: the app has no internet permission.

When an ordinary cellular call starts, it also sends the Qualcomm audio parameter `volume_boost=on`. Whether that
does anything depends on the phone maker's tuning, and it is unverified.

## Why the DUMP permission

`dumpsys audio` is the only way for a normal app to learn other apps' audio session ids. `DUMP` can only be granted
over adb, never from a prompt on the phone. The app reads only the audio report and only the session ids in it.

## Build

```
./gradlew assembleDebug
```

Needs the Android SDK (compile SDK 37) and JDK 17 or newer.

## Caution

Extra gain can distort, and loud playback for long periods is hard on a small speaker. Start low. Use at your own risk.

## License

MIT
