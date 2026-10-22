# Project Knowledge

Lasting knowledge about this project. Current progress and next steps live in `HANDOFF.md`.

## Overview

"slot. for Android": an Android port of the Slot handheld frontend (https://slot-cfw.fyi, GPL-3.0, by Brandon
Kowalski) that keeps its look and exact motion (cartridges and discs slide into a slot, shelf springs, core-picker
"board" flip) and adds more systems, gamepad + touch control and launching of installed emulators.

- Repository: https://github.com/nikunjsingh93/slot-ui-android (branch `main`). Releases carry the APK.
- Package `fyi.slot.android`, version tracked in `app/build.gradle.kts` (see `HANDOFF.md` for the current one).
- Primary test device: the user's Anbernic RG476H handheld (Android 13, arm64, 1280x960, 4:3). Also tested on the
  user's Samsung S25 Ultra (Android 16). Handhelds are the real target; phones must work too.
- Not run on an emulator: there is no Android emulator for Windows ARM64, the development machine.

Systems slot emulates itself (libretro cores in-process): GBA (mGBA, gpSP), GB / GBC (Gambatte, mGBA), NES, SNES,
Mega Drive, Master System, Game Gear, PC Engine. Systems handed to the user's installed emulators: PlayStation
(DuckStation), PS2 (AetherSX2/NetherSX2, ARMSX2), N64 (M64Plus FZ Pro), GameCube (Dolphin, Dolphin MMJR), PSP
(PPSSPP), Dreamcast (Redream). Plus two shelves built from Android apps: "Apps" and "Installed" (games).

## Technology stack

- Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.10.2, JDK 17, Compose Multiplatform 1.7.1.
- `compileSdk`/`targetSdk` 35, `minSdk` 26, `abiFilters = arm64-v8a` only.
- Emulation: LibretroDroid 0.14.0 (JitPack) plus libretro cores as `lib<core>_libretro_android.so` in
  `app/src/main/jniLibs/arm64-v8a` (git-ignored; fetch with `scripts/fetch-cores.sh`). `useLegacyPackaging = true`.
- Persistence: small JSON files via `org.json` in the app's `filesDir`.
- Environment: `ANDROID_HOME=/c/Users/Nikunj/Android/Sdk` (set it if Gradle cannot find the SDK); `local.properties`
  holds `sdk.dir` (forward slashes) and is git-ignored.

## Project structure

- `ui/` Kotlin Multiplatform module (Android + desktop JVM). All screens and drawing:
  - `SlotRoot.kt` shelf browser, footer, touch buttons, toast; `Scene.kt` geometry (`Metrics`, poses, the slot);
    `SlotState.kt` all UI state, navigation, menus, drawer, insert/eject phases; `SlotModel.kt` data types
    (`Settings`, `SlotHost` callbacks, `Overlay`, `AppEntry`); `Model.kt` `GameSystem`, `CartShape`, `Game`,
    folder-name aliases, core lists; `SlotMenus.kt` all settings/pause menu pages; `Menus.kt` menu rendering, save
    state switcher, touch controls; `Drawer.kt` app drawer (grid/list, long-press options); `Board.kt` core-picker
    board; `CartSpecs.kt` / `CartArt.kt` / `Shells.kt` cart, disc and UMD shapes and painting; `CoverMatch.kt`
    fuzzy matching of ROM names to cover file names; `Hud.kt`, `Icons.kt`, `Theme.kt`, `CartGallery.kt`.
  - `ui/src/desktopMain/kotlin/Preview.kt`: headless screenshot renderer (see commands).
- `app/` Android shell (`app/src/main/java/fyi/slot/android/`):
  - `MainActivity.kt` launcher host (`SlotHost`): scanning, covers, external launches, drawer host calls, key dispatch.
  - `GameActivity.kt` runs one libretro game in the separate process `:game` (rendering, audio, save states, rewind,
    fast forward, touch overlay). `SlotApp.kt` application setup (also initialises compose resources in `:game`).
  - `ExternalLauncher.kt` intents for the external emulators; `Apps.kt` Android apps list/icons/launch;
    `Scanner.kt` ROM discovery through the storage access framework; `Covers.kt` cover art download and cache;
    `Persist.kt` JSON persistence (settings, library, per-game metadata, roots); `StateStore.kt` save states and
    battery saves; `Backup.kt` copy of choices in the ROM folder; `KeyMap.kt` key mapping and hat/stick navigation;
    `CoreOptions.kt` core variables; `RomFiles.kt`; `Sfx.kt` synthesised UI sounds (class `Sounds`).
- `scripts/fetch-cores.sh` downloads the libretro cores. `README.md` is the user-facing documentation.
- `app/slot-signing.keystore` is the signing key for every build (see Decisions).

## Architecture

- **Motion is driven by the frame clock, not Compose animation APIs.** The handheld has all system animation scales
  set to 0, so `animate*` never moves. `runFrames` (SlotRoot.kt) and a Choreographer loop (GameActivity) drive all
  motion. Constants come from the original slot source: shelf spring omega 16, side scale 0.78, side alpha 0.55,
  insert 0.73 s, seated at 0.45, catch 0.42/0.62/0.03.
- `SlotState` is pure UI logic and talks to the platform only through `SlotHost`. Insert/eject are `Screen`
  phases (`Browse`, `CorePicker`, `Inserting`, `Playing`, `Ejecting`). Returning to slot from an external emulator
  (`onResume` while `Playing`, more than 1.2 s later) plays the eject animation.
- The launcher and the game run in different processes (`:game`), so a core crash cannot kill the launcher. Both read
  and write the same JSON files, always through atomic writes (`Persist.writeAtomic`).
- Persisted files in `filesDir`: `settings.json`, `library.json`, `meta.json` (per game: `core`, `pinned`,
  `played`), `roots.json` (SAF tree URIs), `apps.json` (package -> App/Game), plus `covers/`, `saves/` (battery
  saves `<id>.srm`), `states/<id>/slotN.state|png`.
- **Emulator choice:** `Settings.defaultCores` (system id -> core id) is the default per system
  (Settings > Emulators, systems with more than one core). A game keeps its own core only when it was picked with
  START (`Game.corePinned`, `meta.json` `pinned: true`). Old `meta.json` entries without `pinned` count as pinned
  only if the core is not the system's first. `SlotState.withDefaultCore` applies the default.
- **Covers** (`Covers.kt`, `CoverMatch.kt`): custom label file > cache > libretro thumbnail server (exact name first,
  then fuzzy match against that set's cached directory listing) > Wikipedia article cover > generated label.
  The listing is parsed once into a `CoverMatch.Index`; each set has its own lock.
- **Scanning** (`Scanner.kt`): folders are matched to systems by normalised name aliases (`GameSystem.byFolder`,
  e.g. `dc`, `Dreamcast`, `Sega Dreamcast (Games)`), folder assignment is inherited by sub folders, `.bin` next to a
  `.cue` is hidden, dot-files and macOS `._` files are skipped, unsupported folders are skipped without opening zips.
- **External emulators** (`ExternalLauncher.kt`), verified behaviour:
  - DuckStation (`com.github.stenzek.duckstation/.EmulationActivity`) and AetherSX2/NetherSX2
    (`xyz.aethersx2.android/.EmulationActivity`): extra `bootPath` = the document URI string, with the same URI as
    intent data, `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION`. Raw file paths fail with `errno 13` on phones, where
    these apps have no all-files access and read games only through storage-access-framework grants.
  - ARMSX2 (`com.armsx2`, v2.8.2): `ACTION_VIEW` to `com.armsx2.BootSplashActivity` with the document URI and grant.
    A percent-encoded `file://` URI fails ("does not exist"); a plain path and the content URI work.
  - M64Plus FZ Pro (`ACTION_VIEW`, `Uri.fromFile`, `.SplashActivity`), Dolphin / Dolphin MMJR
    (`ACTION_VIEW`, raw path string as data, `ui.main.MainActivity`), PPSSPP (`.PpssppActivity`, raw path),
    Redream (`.MainActivity`, raw path as plain string, not `file://`): verified on the handheld only, with raw paths.
  - Candidate emulators are tried in order (the game's choice first); the first installed one is launched.
  - `<queries>` in the manifest must list every emulator package (Android 11+ package visibility).
- **Apps / Installed shelves** (`Apps.kt`): nothing shown by default; Settings > Apps & games sets each launcher app
  to Hidden / App / Game. Ids are `app_<package>`, so the card painter cache must include the system (fixed bug).
- **App drawer** (Y or the grid button): grid/list, three sizes, long-press or Y opens Open / App info / Uninstall
  (`ACTION_APPLICATION_DETAILS_SETTINGS`, `ACTION_DELETE`; needs `REQUEST_DELETE_PACKAGES`). System apps cannot be
  uninstalled so the option is hidden.
- **Shelf input:** Left/Right browse (own hold-repeat timing 400 ms then 110/85/65/50 ms), Up/Down jump by letter,
  L1/R1 previous/next system, L2/R2 previous/next game, A tap resumes / hold starts new, START core picker, Y app
  drawer, MENU/SELECT settings. Analog triggers are not mapped in the launcher on purpose (devices that send both a
  key and an axis would fire twice). Hat/stick presses come through `AxisNav` and must report releases or the shelf
  keeps repeating.
- **Save states** (in game): MENU pause menu, MENU twice for the state switcher, slot 0 = auto state on eject,
  slot 1 = quick save, slots 2-8 manual; battery saves written periodically and on eject. Documented in README.
- **Backup** (`Backup.kt`): `.slot-backup.json` in the first ROM folder (needs a write-capable folder grant) holds
  `apps.json` and `settings.json`; restored when the folder is picked again on a fresh install.

## Development commands

Run from the project root in Git Bash on Windows. Set `MSYS_NO_PATHCONV=1` for adb calls with `/sdcard/...` paths.

- Build and lint: `./gradlew :app:lintDebug :app:assembleRelease :app:assembleDebug`
  (APKs in `app/build/outputs/apk/{debug,release}/`).
- Cores (first time on a machine): `scripts/fetch-cores.sh`.
- Headless UI previews: `./gradlew :ui:renderPreviews -Pout=$PWD/build/previews -Pscene=<name>`; scenes:
  `all gallery home wide phone menus extra device anim consoles apps folders drawer covermatch`. Use an absolute
  `-Pout` (the task runs from `ui/`). `covermatch` needs libretro directory listings saved as `<out>/{dc,n64,ps2,psp}.html`.
- Install on the handheld: `adb -s <device> install -r <apk>`. Debug builds allow `adb shell run-as fyi.slot.android`;
  release builds do not.
- Device serials: the handheld connects as `<ip>:<port>` (port changes whenever wireless debugging restarts; the user
  gives the new one) or by its mDNS name `adb-RG476H03948318-Ig5BOO._adb-tls-connect._tcp`; the S25 appears as
  `adb-R5CXC298GBB-90hSvk._adb-tls-connect._tcp`. `adb devices` lists what is connected.
- ROMs on the handheld: `/storage/685D-C9F8/Games/{SNES,N64,PSX,PS2,PSP,GC,DC,...}`; on the phone `/sdcard/Games/{psx,Ps2}`.
- Screenshots: `adb shell screencap -p /sdcard/a.png` then `adb pull`. Wake the screen first (`input keyevent 224`);
  it sleeps and locks. Key codes: L1 102, R1 103, L2 104, R2 105, Y 100, A 96, START 108, DPAD 19-22, BACK 4.
- Release procedure (no `gh` CLI on this machine): bump `versionCode`/`versionName`, build release, copy
  `app-release.apk` to `slot-android-<version>.apk` (git-ignored), commit and push, then create the release through
  the GitHub REST API with a token from `printf 'protocol=https\nhost=github.com\n\n' | git credential fill`
  (never print it). POST `/repos/nikunjsingh93/slot-ui-android/releases` with `tag_name`, `name`, `body`, then POST the
  APK to `https://uploads.github.com/repos/.../releases/<id>/assets?name=slot-android-<version>.apk` with
  `Content-Type: application/vnd.android.package-archive`. Keep the JSON body in a file inside the project (curl on
  Windows cannot read `/tmp`), delete it afterwards, and omit `make_latest` (including it made creation fail once).

## Important decisions

- **Fixed signing key** `app/slot-signing.keystore` (a standard debug key, committed on purpose) for debug and
  release. Android refuses to update an app signed with another key, which forces an uninstall and wipes the user's
  data; a machine-local debug key would have made builds from another laptop incompatible. Do not replace it.
- **Document URIs for emulators that accept them** (DuckStation, AetherSX2, ARMSX2) so phones without all-files
  access work; raw paths are kept for the others because that is what was verified (see Architecture).
- **Default emulator per system** plus per-game pin, instead of remembering the effective core on every launch.
- **No analog trigger mapping in the launcher** (double-fire risk).
- **Sounds are synthesised.** The original site's `slot.mp3` is a robotic voice saying "slot", which the user hated.
- **Backup in the ROM folder** because that is the one place that survives an uninstall and is re-picked anyway.

## Known issues and pitfalls

- Never `adb uninstall` the app on the user's devices and never delete their JSON files (see `AGENTS.md`). Stray
  `adb input` taps and key events while a menu is open can change the user's settings; the "Small" cart and drawer sizes
  appear to be the user's own choice, so do not reset them.
- A device-side check of anything touching input is flaky if the screen is asleep or a notification shade is open:
  wake the device and screenshot before tapping. `adb input` key chains need small sleeps between keys.
- Parentheses in file names break `adb shell am start ... -d '...'` unless wrapped in an outer double quote with the
  value in single quotes.
- Regexes must be compiled once: compiling them per name made matching an 8,500-entry PS2 list take about a minute on the
  handheld, which looked like "no covers".
- The libretro thumbnail names follow No-Intro/Redump (`Gran Turismo 4 (USA) (v1.01)`), user ROM names often do not;
  that is why matching is fuzzy. Wikipedia's `pageimages` API returns nothing for non-free covers; the infobox
  `image =` field plus `imageinfo` is used instead.
- Kotlin/Compose: KDoc containing `/*` breaks parsing; a property used from an `init` block must be declared before
  it; `lifecycleScope` has no `MonotonicFrameClock` (use Choreographer); the original lettering PNGs are opaque
  grayscale and need a luma-to-alpha `ColorMatrix`; the Windows ARM64 skiko desktop runtime must be added manually.
- `sed` replacement text containing `\n` inside a `"` string literal in Kotlin turns into a real newline; fix the
  string afterwards. Python is not installed on the dev machine.
- Scanning must rethrow `CancellationException`; macOS `._` files must be ignored; folders of N64 zips are slow if opened.
- Emulator packages differ per device (NetherSX2 keeps the package `xyz.aethersx2.android`); an emulator that is not
  installed is skipped silently, and the UI says so only if none of the candidates is installed.

## Conventions

- Match the surrounding code's style, comment density and naming. Comments explain why, not what.
- Everything user-visible is in `ui/` and testable headlessly; keep platform code in `app/`.
- UI sizes use design units (`Float.udp()`, `Metrics.u`), never raw pixels.
- New settings: add to `Settings`, `Persist.loadSettings`/`saveSettings`, a menu row in `SlotMenus.kt`, and the README.
- After a user-visible change update the README and the in-app Button guide if buttons changed.
