# Development Handoff

## Last Updated

Date: 2026-11-03
Agent: Claude Code (Claude Sonnet 5.5)

## Current State

Version **0.1.11** (`versionCode` 12) is built, pushed (`main` at `8a3bc42`) and
published as GitHub release `v0.1.11` with `slot-android-0.1.11.apk` attached. Everything the user asked for so far is
implemented, tested on the RG476H handheld and pushed. Nothing is half-finished. The working tree was clean before
these `.ai/` files and `AGENTS.md` / `CLAUDE.md` were added.

Working features: slot's shelf UI and motion; GBA/GB/GBC/NES/SNES/MD/SMS/GG/PCE through libretro cores with save
states, rewind, fast forward, turbo; shelves for PlayStation, PS2, N64, GameCube, PSP (UMD shape) and Dreamcast that
launch the installed emulators; Apps and Installed shelves from Android apps; Y app drawer with long-press options;
cover art with fuzzy matching and a Wikipedia fallback; Settings > Emulators (default per system, ARMSX2 as a second PS2
emulator); cart size and drawer options; touch scrolling in menus; backup of choices in the ROM folder; fixed signing key.

## Recently Completed (newest first, all in git history)

- Per-system on-screen button size and position (`Settings.touchTweaks`, six indices per system id, menu-driven not drag; `layoutFor` in Menus.kt) (0.1.11). Tested on the tablet. A drag-to-place editor was not built.

- Portrait game picture moved down by default; `Settings.portraitScreenStep` (0-5, 3% of height each, default 3), Settings > Screen (0.1.10). Tested on a 1000x1524 tablet.

- Dolphin MMJR2 opened its menu instead of the game: Dolphin launches now also pass the `AutoStartFile` extra (0.1.9).

- System pill bar scrolls and centres the selected system (square RG Rotate screen cut off the right side); pause menu now
  starts with Eject then Resume (0.1.8).


- README section "Saving and loading in a game" (`8a3bc42`).
- Shelf buttons: L1/R1 previous/next system, L2/R2 previous/next game (0.1.7). Earlier variants (0.1.6) were wrong
  after the user's clarification.
- App drawer long-press / Y menu: Open, App info, Uninstall (0.1.5).
- Fix: an app moved between Apps and Installed kept its old APP/GAME look (painter cache key lacked the system, 0.1.4).
- Settings > Emulators with per-system default cores and per-game pinning; `Settings.defaultCores`, `Game.corePinned` (0.1.3).
- ARMSX2 added as a second PS2 emulator (0.1.2).
- Backup copy of Apps & games choices and settings in the ROM folder, fixed signing key (0.1.1).
- DuckStation/AetherSX2 launched with document-URI grants so phones without all-files access work.
- Cover matching rewritten (regexes compiled once, listing parsed once); discs redrawn as plain CDs; UMD caddy shape.

## Files Changed in the latest work

- `ui/.../SlotState.kt` shelf button mapping, default core application, drawer menu logic.
- `ui/.../SlotMenus.kt` Emulators page, Button guide text; `ui/.../Drawer.kt` options sheet; `ui/.../CartArt.kt` painter cache key.
- `app/.../ExternalLauncher.kt` ARMSX2 and document-URI launching; `app/.../Persist.kt` `pinned` meta and `defaultCores`;
  `app/.../Backup.kt`; `app/.../Covers.kt`; `ui/.../CoverMatch.kt`; `app/build.gradle.kts` signing config and version.
- `README.md` user documentation (kept in sync with every feature). `AGENTS.md`, `CLAUDE.md`, `.ai/*` added now.

## Work in Progress

None.

## Known Issues

- Dolphin, Dolphin MMJR, PPSSPP, M64Plus FZ Pro and Redream are still launched with raw file paths. They were verified
  only on the handheld. On a phone without all-files access they will probably fail with a permission error like
  DuckStation did. The fix, if reported, is to pass a document URI plus grant (see `ExternalLauncher.kt`); each
  emulator's accepted form must be tested on a real phone first.
- The user's S25 Ultra has an older build (it was disconnected when later versions were released). It needs the 0.1.7
  APK from the GitHub release; the signing key is unchanged so it updates in place.
- The backup file is only written once the ROM folder has been picked with write access (old grants are read-only);
  the user has to pick the folder again once for the backup to start. Restore after a reinstall is only tested by
  removing the app files, not by a real uninstall (uninstalling the user's app is forbidden, see `AGENTS.md`).
- The Wikipedia cover fallback has been exercised by the code path but not seen producing a cover for a game that is
  missing from libretro, because all of the user's games were found there.
- A long GameCube title ("Legend of Zelda, The The Wind ...") is truncated in the title strip; "Minecraf t" wraps badly on
  a card without cover art. Both cosmetic and unreported by the user.
- Analog-only controller triggers do not work on the shelf (deliberate, see `PROJECT.md`).

## Verification

Run in the last session (results as observed):
- `./gradlew :app:lintDebug :app:assembleRelease` passed with no errors for every released version (0.1.1 to 0.1.7).
- Release APK installed with `adb install -r` on the RG476H and checked by screenshots and key events: shelf buttons,
  Settings > Emulators, drawer options (Uninstall confirmation was cancelled, nothing removed), Apps/Installed card labels,
  cover art on SNES/GBA/GBC/PS1/PS2/N64/GC/PSP/DC shelves, ARMSX2 launch of Gran Turismo 4, DuckStation and AetherSX2 launches.
- On the S25 Ultra (earlier build): DuckStation and AetherSX2 booted games through document URIs.
- No automated unit tests exist in this project; verification is by builds, headless previews and the device.
- The last release step created GitHub release `v0.1.7`; GitHub reported the asset as uploaded (9.8 MB).

## Next Steps

1. Wait for the user's next request; they mostly ask for small UI/behaviour changes, then "push". For each pushed app
   change follow the release procedure in `PROJECT.md` (version bump, build, push, new release with the APK).
2. If the user reports an emulator failing on their phone, test that emulator's document-URI form on the phone and
   switch `ExternalLauncher.kt` for it, keeping the raw path for devices where it works.
3. Possible polish if asked: truncation of long titles, the card text wrapping, a Settings option to move games from
   Installed back to Apps in bulk, and a "restore backup" action in Settings (today restore happens when the folder is picked).

## Additional Context

- The user communicates in short messages with typos; when a request is ambiguous (for example which buttons do what),
  choose the sensible reading, say so up front, and make it easy to flip. They confirm with "push".
- The user wants the latest APK attached to a GitHub release whenever updates are pushed.
- Their Apps & games selection (several installed games) and settings are real user data on the handheld. Do not touch them.
- Device connection details change; ask the user for the wireless-debugging port if `adb devices` does not show the handheld.
