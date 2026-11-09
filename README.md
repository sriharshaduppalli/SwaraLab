# SwaraLab

SwaraLab is an open-source Android studio for singers. Record a dry vocal on this phone, keep every take, then Mix & Save a file you can send through the system share sheet.

The app is Apache-2.0. It does not ship a catalog of commercial songs. Imported tracks stay in the session only after the singer confirms they can use them. There is no feed and no cloud locker.

## Open

Open this folder in Android Studio. The application id is `com.swaralab.app`, minimum Android 8.0 (API 26).

From a terminal, with `JAVA_HOME` pointed at JDK 17 or newer and the Android SDK installed:

```bash
./gradlew :core:domain:test :core:session:test :engine:render:testDebugUnitTest :app:assembleDebug
```

On Windows that is `gradlew.bat` with the same tasks.

Mix and edit fixtures live in `testdata/mix-fixtures/`: a clean 440 Hz A4, the same tone with white noise, a phrase pair with gaps for the noise gate, and eight more clean/noisy recipes under `testdata/mix-fixtures/batch/` (white, hiss, rumble, hum, knock, air, low SNR, fifth). Import those WAVs on the phone, or regenerate with:

```bash
python tools/generate_mix_test_signals.py
```

A signed Play bundle:

```bash
./gradlew :app:bundleRelease
```

The AAB is copied to `releases/` (that folder is not committed). Version code and name increment in the ignored `version.properties` file on each `bundleRelease`.

## How a singer moves

1. **Today** — continue the last song, search, listen, or import.
2. **Start** — open the stage, import a song from this phone, video, or vocal warm-up.
3. **Practice** — slow the track, loop a line, watch pitch. Warm-up opens this view.
4. **Record lead** — Sing on the selected layer. Dry 48 kHz WAV; headphone FX stay in the cans.
5. **Punch / stack** — re-record a window, or Rec on Double / Harmony / Ad-lib.
6. **Mix** — layer volume, mute, solo, chain. Undo / Redo on the stage.
7. **Mix & Save** — hear the mix, export stems, share through the system sheet.

An unfinished take can come back as a draft. Storage lives under Singer → On this phone. Edits write `session.json` in the song folder; the last playhead comes back with Continue editing.

## Modules

| Module | Role |
| --- | --- |
| `core:domain` | Session document, commands, rights, listening rules, use cases |
| `core:session` | Folder store, take index, invite zip, local analytics, settings |
| `core:ui` | Theme, five-language copy, empty/error panes, share helper |
| `engine:audio` | `AudioEngine`: Oboe capture, dry 48 kHz WAV, wet monitor |
| `engine:render` | `RenderEngine`: mix hear, export, compact AAC, video mux |
| `feature:library` | Songs on this phone |
| `feature:studio` | Stage, lyrics editor, karaoke, draft recovery |
| `feature:publish` | Mix & Save and share sheet |
| `feature:account` | Singer, optional account, storage |
| `app` | `SwaraLabGraph`, navigation, Home / Start / Import / Listen |

`SwaraLabGraph` is the composition root. Features do not construct engines.

## More

See [ARCHITECTURE.md](ARCHITECTURE.md) for folders, ports, threads, and what the app refuses. See [PRIVACY.md](PRIVACY.md) for what stays on the phone.
