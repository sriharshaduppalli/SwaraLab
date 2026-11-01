# SwaraLab

SwaraLab is an open-source Android studio for singers. Record a vocal, keep every take on the phone, and prepare a file for Reels, Shorts, or WhatsApp.

The app is Apache-2.0. It does not ship a catalog of commercial songs. Imported tracks stay in the session only after the singer confirms they can use them.

## Open

Open this folder in Android Studio. The app module is `com.swaralab.app`, minimum Android 8.0.

From a terminal, with `JAVA_HOME` pointed at JDK 17 or newer and the Android SDK installed:

```bash
./gradlew :core:domain:test :core:session:test :app:assembleDebug
```

On Windows that is `gradlew.bat` with the same tasks.

## Modules

| Module | Role |
| --- | --- |
| `core:domain` | Session document, commands, rights and listening rules |
| `core:session` | Folder store and crash-safe `session.json` |
| `engine:audio` | `AudioEngine` port. Oboe connects here |
| `engine:render` | `RenderEngine` port. Media3 connects here |
| `feature:library` | Sessions on this phone |
| `feature:studio` | Listening route and session |
| `feature:publish` | Frame, loudness, safe area |
| `app` | Composition root and navigation |
