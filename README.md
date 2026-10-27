<p align="center"><picture><source media="(prefers-color-scheme: dark)" srcset="app/src/main/res/drawable/kimp_logo_dark.png"><img src="app/src/main/res/drawable/kimp_logo.png" width="88" alt="KIMP logo"></picture></p>

<h1 align="center">KIMP for Android</h1>
<p align="center"><b>YouTube to MP3 and MP4 on your phone.</b> Paste a link, pick what to save, done.<br>
Developed by <b>Kim G. Decierdo</b> · <a href="https://kimp-qwtl.onrender.com">kimp-qwtl.onrender.com</a></p>

<p align="center"><a href="https://kimp-qwtl.onrender.com"><b>⬇ Download the APK</b></a></p>

## Features
- **Music:** one song or a whole playlist as 320 kbps MP3 with title, artist and cover art, saved to `Music/KIMP`.
- **Video:** *Find qualities* lists only the resolutions the video really has (144p up to 4K), then saves an MP4 to `Movies/KIMP`.
- Downloads keep going with the screen off, with a notification and a Stop button.
- Share a link from the YouTube app straight to KIMP.
- Same design as KIMP for Windows; light, dark or follow-system theme. No accounts, no tracking.

## Install
1. Download `KIMP-1.0-arm64-v8a.apk` (almost every phone) from the website. Use `armeabi-v7a` only for very old phones.
2. Open it and allow your browser or Files app to **install unknown apps** when Android asks.
3. Android 10 or newer is required.

KIMP isn't on Google Play: Play doesn't allow apps that download from YouTube.

## Responsible use
Only download what you own, what's free to download, or what you have permission to save. Downloading copyrighted songs or videos without permission can break copyright law and YouTube's Terms of Service. KIMP doesn't unlock paid or protected content and isn't affiliated with, endorsed or sponsored by YouTube or Google. **You are responsible for how you use KIMP.**

## Build from source
Requirements: Android Studio (or JDK 17 + Android SDK 36), NDK and CMake from the SDK Manager.

1. Build the JavaScript runtime yt-dlp needs for YouTube (QuickJS-ng, compiled with the NDK):
   ```
   powershell -ExecutionPolicy Bypass -File tools\build-quickjs.ps1
   ```
2. Build the APKs (one per processor type):
   ```
   gradlew assembleRelease
   ```
   Release signing reads `../kimp-android-keys/keystore.properties` (not in this repo). Without it you get unsigned release APKs; `gradlew assembleDebug` always works.

Debug builds include a self-test that logs to logcat (tag `KIMP`):
```
adb shell am start -n com.kimgdecierdo.kimp/.SelfTestActivity --es mode mp3
adb shell am start -n com.kimgdecierdo.kimp/.SelfTestActivity --es mode video:240
```

## License
KIMP for Android is free software under the [GNU General Public License v3](LICENSE).
It is built on [youtubedl-android](https://github.com/yausername/youtubedl-android) (GPL-3.0), which bundles
[yt-dlp](https://github.com/yt-dlp/yt-dlp), Python and [FFmpeg](https://ffmpeg.org/), and uses
[QuickJS-ng](https://github.com/quickjs-ng/quickjs) (MIT). Fonts: Inter, Archivo, Geist Mono, Source Serif 4 and
Carter One (SIL Open Font License 1.1). See [NOTICE](NOTICE).

© 2026 Kim G. Decierdo.
