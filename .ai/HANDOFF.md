# Development Handoff

## Last Updated

Date: 2026-11-03
Agent: Claude Code (Claude Sonnet 5.5)

## Current State

Version **0.1.24** (`versionCode` 25) is built, pushed (`main` at `8a3bc42`) and
published as GitHub release `v0.1.24` with `slot-android-0.1.24.apk` attached. Everything the user asked for so far is
implemented, tested on the RG476H handheld and pushed. Nothing is half-finished. The working tree was clean before
these `.ai/` files and `AGENTS.md` / `CLAUDE.md` were added.

Working features: slot's shelf UI and motion; GBA/GB/GBC/NES/SNES/MD/SMS/GG/PCE through libretro cores with save
states, rewind, fast forward, turbo; shelves for PlayStation, PS2, N64, GameCube, PSP (UMD shape) and Dreamcast that
launch the installed emulators; Apps and Installed shelves from Android apps; Y app drawer with long-press options;
cover art with fuzzy matching and a Wikipedia fallback; Settings > Emulators (default per system, ARMSX2 as a second PS2
emulator); cart size and drawer options; touch scrolling in menus; backup of choices in the ROM folder; fixed signing key.

## Recently Completed (newest first, all in git history)

- 0.1.25: menu choices get tappable < > arrows (`stepMenuRow`), Settings > Interface > Interface scale (`UiScale`, multiplies `Metrics.u`). Tested on the tablet.
- 0.1.26: Menu text size removed (Interface scale covers it). `Metrics.u` is now `min(w,h)/480` in both orientations (same size when rotated) and capped at `density*1.15` px on screens 600dp+ across (tablets drawn at a handheld's physical size); `maxU` comes from SlotRoot, and `autoScaleBigScreens` is false in the desktop previews. Checked on the tablet in portrait and landscape.
- 0.1.27: Shelf view "Grid" (`Settings.shelfGrid`, `ShelfGrid.kt`, LazyVerticalGrid of cart painters drawn over the carts while `screen == Browse`; hidden on insert so the drop animation plays). `SlotState.gridView`, `gridCols` (Up/Down step a row, clamped in `ringStep`), `coverRange` (MainActivity loads covers for the visible tiles plus a screenful either side). Preview scene `grid`. Tested on the RG476H (D-pad, tap) and restored to Carts.
- 0.1.28: `Scanner.SKIP_DIRS` still listed "xbox" and "ps3" from before those systems were supported, so those folders were never scanned; removed. "Game.xiso.iso" stems drop the ".xiso". Checked on the RG476H (Games/xbox/Forza ... .xiso.iso shows on the Xbox shelf).
- 0.1.29: GB/GBC/GBA labels from slot's art server (`Covers.fromSlotArt`): `https://art.slot-cfw.fyi/index.json` (~1.2 MB, CRC32 hex -> `support-texture` real label scan, `wheel` logo, `look.ground` colour; cached in covers/index with ETag, weekly), images tried as `.webp` then `.png`; logo-only entries become a ground-colour label with the logo. Cached as `<id>.label.png` / `.label.miss`, checked before the old libretro/Wikipedia cache. Server has only GB/GBC/GBA. Verified on the tablet: GBA carts show the real label scans.
- 0.1.30 (removed in 0.1.31): a self-hosted ScreenScraper art server. Dropped because ScreenScraper's API refuses requests without developer credentials (checked: 403 "identifiants développeur" even with a user login).
- 0.1.31: real disc scans from the LaunchBox Games Database for psx/ps2/psp/dc/gc/xbox/x360/ps3. `tools/launchbox-index/build.js` streams LaunchBox's free `Metadata.zip` (Metadata.xml, ~500 MB) into `art/launchbox/<system>.json` ("Name (Region)" incl. alternate names -> image file); the app downloads that list from raw.githubusercontent (30 days), matches with `CoverMatch`, fetches `https://images.launchbox-app.com/<file>`, caches `<id>.disc.png/.miss` (`Covers.real`). Preview scene `launchbox` checks real ROM names (32/32 matched). Cart photos in LaunchBox include the shell, so carts are not taken from it. Rebuild the lists now and then.
- 0.1.32: disc scans are told apart from box art by their transparent corners (`CartPainter.clearCorners`): drawn whole and centred (fixed the PS1/DC double ring from the spine crop). N64/SNES/NES: label cut from the LaunchBox cart photo (`LabelCut`, shared + desktop scene `labelcut`; cached `<id>.cartlabel.png` with rounded clear corners) and drawn in its own shape in `REAL_LABEL_AREA`. N64 cart redrawn like the real one (arched top, side panels and seams, bottom step, tall label, no lettering). PSP UMD redrawn white with sealed disc, "PSP" and controller mark; a LaunchBox UMD scan (whole caddy) is drawn as the cart. PS1 fallback: PSX1G1B1C1D disc scans (`art/psx1g1b1c1d/disc.json`, `tools/launchbox-index/psx1g1b1c1d.js`). Preview scene `shapes`.

- README rewritten to match 0.1.24 (systems table with built-in/external defaults, browsing, playing, saves, settings overview). Keep it in sync when adding features.

- 0.1.24: Settings > Interface > List text size (`Settings.listSize`, `ListSize` scale 0.85-1.4 applied to row height, heading height and fonts in `GameListPanel`).

- 0.1.23: list wraps seamlessly (three copies, middle one shown) instead of jumping from last to first; letter headings above each letter group again (variable row step handled when mapping `scroll` to the list); dark copy of the list clipped to the fixed highlight so names stay readable while passing; scanner skips BIOS files (`Scanner.isBios`: scph*, *bios*, dc_boot/flash/nvmem, mcpx).

- 0.1.22: list view redesigned: `GameListPanel` overlays the left part between top bar and footer; `Metrics(split=true)` moves cx to 0.72w (carts + slot on the right), fixed highlight with rows glued to `state.scroll` (scrollToItem per frame, equal row heights, letter in a side column), list drag drives the shelf. README screenshots in docs/screenshots (fictional games, `readmeShots` scene). The desktop renderer crashes (native skia access violation) on the list view, so there is no list screenshot.

- 0.1.21: letters only while swiping (+2.5 s, fade), no background pill; list view: small bold centred system name, selected game pinned to the middle (scrollToItem with half-height content padding, letter-heading offset), list slides under it.

- 0.1.20: letter jump bar (`LetterBar` in ShelfExtras.kt, `jumpToLetter`, `selectIndex`); "List + cart" shelf view (`Settings.shelfList`, `ListShelf`: game list + vertical cart stack via `verticalPose`, vertical drag/fling, Up/Down step games). README credits now list the bundled PS1/N64/PSP/DC cores and note Snes9x / Genesis Plus GX are non-commercial.

- 0.1.19: fixed wrong cover art while the shelf moves (carts are now placed and chosen from the moving `scroll`, `ringGame(centre + slot)`, not from `target`/`selected` which can disagree mid-fling) and blank carts (cover loads are independent jobs tracked in `coverJobs`, nearest 17 queued, never cancelled by a selection change; 9 carts drawn instead of 7).

- 0.1.18: touch momentum. Shelf: `SlotState.fling` + `flingVel` coast with friction (FLING_FRICTION 2.4/s), selection follows the middle cart while dragging/coasting, then the spring settles. Menus: MenuPanel rows are a LazyColumn (native fling like the app drawer); the D-pad cursor is scrolled into view.

- 0.1.17: RetroArch offered as an external choice on every system with a built-in core (`RETROARCH` appended in `GameSystem.cores`; launch via RetroActivityFuture with ROM/LIBRETRO/CONFIGFILE extras, com.retroarch.aarch64 or com.retroarch). Not tested: RetroArch not installed on the test devices.

- 0.1.16: ARMSX2 is the default PS2 emulator on every device (user: better Mali support now); AetherSX2/NetherSX2 second and used automatically when ARMSX2 is missing.

- 0.1.15: Xbox (hakuX, com.rfandango.haku_x LauncherActivity VIEW), PS3 (ARMSX3, com.armsx3 / com.armsx3.play, com.armsx2.BootSplashActivity VIEW), Xbox 360 (Xendroid+, xendroid.compose.fork, EmulatorHostActivity with game_uri extra) shelves; launch intents read from each project's source, NOT tested (none installed). Flycast: options use the reicast_ prefix; threaded rendering now disabled but still black on Mali-G57 too, emulation runs (GLThread ~27% CPU), so the picture is lost when handed to LibretroDroid (suspected framebuffer format/blit); Redream stays default.

- Edit button layout editor (drag to move, pinch to resize, Reset, Done): `LayoutEditor.kt`, `Overlay.EditLayout`, `Settings.touchTweaks` = six floats per system (d-pad scale,x,y, buttons scale,x,y), stored under `touchLayout` (0.1.12). Replaced the short-lived menu-based sliders of 0.1.11. Pinch was not tested (adb cannot send two fingers); drag and Reset were.

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

- Update: PSX video now fills portrait width (0.54 height for disc/N64 systems); N64 zips are unpacked in RomFiles (most of the user's N64 games are .zip); Flycast needs dc_boot.bin (HLE BIOS draws nothing) so Slot copies it from the DC folder or falls back to Redream. Built-in cores (UNCOMMITTED, NOT released): pcsx_rearmed (PSX), mupen64plus_next_gles3 (N64), ppsspp (PSP), flycast (DC) bundled via scripts/fetch-cores.sh; `CoreChoice.external` flag, `Game.external`; first core of each system is now the built-in one; MANAGE_EXTERNAL_STORAGE + largeHeap added; `RomFiles.materialize` uses the real path for discs/N64; touch layouts for PSX/PS2/DC/PSP/N64 (analog stick, C buttons via `onStick`); L2/R2 passed to games on those systems and rewind disabled there. Tested on the tablet: PSX (Colin McRae Rally 2) and PSP boot and show video, N64 (1080) plays, DC boots on HLE BIOS but stays black (needs dc_boot.bin in files/system/dc). Not done: PS2/GC in-app emulators (not possible as libretro cores on these devices), BIOS handling, audio checks, per-system tuning.

- Save export/import, per-game download/upload (0.1.13): `SaveTransfer.kt`, Settings > Saves. Zip layout `saves/<system>/<game>.srm` and `states/<system>/<game>/slotN.*`, matched by system + game name because ids differ per device. Verified on the tablet: per-game export produced the expected zip and per-game import wrote the save plus a .bak. Export-all and import-all (name matching across devices) were not run.

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
