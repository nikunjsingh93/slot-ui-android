# slot. for Android

An Android port of [slot](https://slot-cfw.fyi), Brandon Kowalski's Game Boy-style frontend for the Anbernic RG SP,
extended to more systems and to touch screens.

You get the same cart shelf, the same slot you push carts into, a board you flip over to choose an emulator core,
polaroid-style save states, and the same motion: a critically damped shelf spring, the "catch" when a cart meets
the contacts, neighbours sliding aside, the screen dimming into the game.

| | |
|---|---|
| Systems | GBA, GBC, GB, NES, SNES, Mega Drive/Genesis, Master System, Game Gear, PC Engine, PlayStation, N64 and PSP run inside slot (Dreamcast experimental). PS2, GameCube, Dreamcast, Xbox, PS3 and Xbox 360 open your installed emulator, and RetroArch can be used for any built-in system. Android apps and games can be added too. |
| Cores | mGBA, gpSP, Gambatte, FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast, PCSX ReARMed, Mupen64Plus-Next, PPSSPP, Flycast (libretro, via [LibretroDroid](https://github.com/Swordfish90/LibretroDroid)) |
| Input | Gamepad first (handhelds), touch controls and swipe when no controller is connected |
| Target | Android 8+, arm64 |

Tested on an Anbernic RG476H (Android 13, 1280x960), an RG Rotate (720x720), an Android tablet and a Samsung S25 Ultra.

## Screenshots

Captured from slot's own renderer at 1280 × 960 (the RG476H's resolution), using made-up game names so no real titles
or box art appear. With your own games the labels show title screens and box art downloaded for each game.

| Game Boy Advance | Super Nintendo | PlayStation |
|:---:|:---:|:---:|
| ![Game Boy Advance shelf](docs/screenshots/gba.png) | ![Super Nintendo shelf](docs/screenshots/snes.png) | ![PlayStation shelf with round discs](docs/screenshots/psx.png) |

| Nintendo 64 | PSP | Dreamcast |
|:---:|:---:|:---:|
| ![Nintendo 64 shelf](docs/screenshots/n64.png) | ![PSP shelf with UMD discs](docs/screenshots/psp.png) | ![Dreamcast shelf](docs/screenshots/dc.png) |

| Choose an emulator (START) | Settings | Portrait (touch) |
|:---:|:---:|:---:|
| ![The board flipped over to choose an emulator](docs/screenshots/picker.png) | ![Settings menu](docs/screenshots/settings.png) | <img src="docs/screenshots/portrait.png" alt="Portrait layout" width="180"> |


## Using it

1. Install the APK. On a handheld you can also pick **slot.** as your home app.
2. Press **A** on the empty shelf (or tap it) and choose your ROM folder. ROMs may be loose files or `.zip`s.
   Alternatively drop ROMs into `Android/data/fyi.slot.android/files/roms` and no permission is needed.
3. Cart art is fetched once and cached: first from the libretro thumbnail server (file names are matched loosely, so
   `Gran Turismo 4.iso` still finds `Gran Turismo 4 (USA)`), then from the game's Wikipedia page. Put your own
   `<rom name>.png` into `Android/data/fyi.slot.android/files/labels` to override it (turn the download off in
   Settings > Library if you prefer).

| Button | Shelf | In game |
|---|---|---|
| Left / Right | browse (hold to speed up) | |
| Up / Down | jump by letter | |
| L1 / R1 | previous / next system | |
| L2 / R2 | previous / next game (same as Left / Right) | |
| Y | app drawer | |
| A | play (tap resumes, hold for a new game) | |
| START | flip the cart: choose the core | |
| MENU | settings | pause menu; press twice for save states |
| L2 / R2 | | rewind / fast-forward (double-tap R2 to lock) |
| X / Y | | turbo A / B on GB, GBA, NES, SMS/GG, PCE |

On a touchscreen the footer has a **gear** (settings) beside the battery and an **apps** button beside the clock.

If your handheld reports A/B the other way round, flip **Settings > Controls > Swap A/B and X/Y**.

## Saving and loading in a game

This applies to the systems slot emulates itself (GBA, GB, GBC, NES, SNES, Mega Drive, Master System, Game Gear, PC Engine).
PlayStation, PS2, N64, GameCube, PSP and Dreamcast run in their own emulators, which have their own save menus.

- **In-game saves (battery saves)** are written for you: every few seconds while you play and again when you eject.
  You never have to do anything for the game's own "Save" to stick.
- **Jump by letter:** while you swipe the shelf a row of letters appears under the system bar and fades out about 2.5 seconds after you stop. Tap N to jump to the first game starting with N.
- **Shelf view:** Settings > Interface > Shelf view: **Carts** (slot) or **List + cart**, a scrolling list of games on the left, between the system bar and the bottom bar, with the carts stacked top to bottom and the slot on the right. The white highlight stays fixed in the middle and the names roll through it with the carts, like a slot machine; the list wraps round from the last game to the first, and small letter headings mark where each letter starts. Tap a game to select it, tap it again to play; Up / Down move one game.
- **Edit button layout:** Settings > Controls > Edit button layout (also in the pause menu under Controls). Pick a system and its on-screen buttons appear over the game: drag the d-pad or the A/B buttons to move them, pinch over a group to resize it, **Reset** puts the defaults back and **Done** keeps the layout. Each system keeps its own layout.
- **Moving saves to another device:** Settings > Saves. **Export all saves** writes one zip of every emulated game's save (the game's own save and the save states) to a place you choose, and **Import saves** reads such a zip back on another device. Saves go to the game with the same system and name, so the same ROMs must be in the library on both devices. For one game, select it on the shelf, then **Download save** (writes a zip) or **Upload save** (reads a zip into the selected game, even if its file is named differently). An existing save is kept as `.srm.bak` before it is replaced. Save states only load in the same version of the same emulator core.
- **Portrait screen position:** with on-screen controls in portrait the game picture sits a little below the top edge. Change it in the pause menu or Settings > Screen > Screen position (portrait): Top, Down 1 to Down 5 (default Down 3).
- **Pause menu:** press **MENU** during a game (on a touchscreen tap the **MENU** pill at the top). It has **Eject** (first, so Back then A quits), **Resume**,
  **Save states**, **Quick save**, **Quick load**, **Fast forward**, **Screen**, **Gameplay**, **Controls**,
  and **Reset game**.
- **Quick save / Quick load** use slot 1. One button press each, no questions asked.
- **Save states:** press **MENU twice quickly** (or pick **Save states** in the pause menu) to open the state
  switcher, a row of cards with a screenshot of each slot. There are nine slots: slot 0 is the automatic one, slot 1
  is the quick-save slot and slots 2 to 8 are yours.

  | Button | In the state switcher |
  |---|---|
  | Left / Right | pick a slot |
  | **A** | load the slot (on an empty slot it saves there instead) |
  | **Y** | save to the slot, replacing what is there |
  | **X** | delete the slot |
  | **SELECT** | undo the last load, in case you loaded by mistake |
  | **B** or **MENU** | close and go back to the game |

  On a touchscreen tap a card to select it, and tap the selected card to load it.
- **Auto-resume:** **Eject** (pause menu) saves an automatic state in slot 0 (turn this off in **Settings > Gameplay >
  Auto-save on eject**). Next time you pick the game, a quick press of **A** resumes exactly where you stopped, and
  holding **A** starts the game fresh.
- **Rewind** (hold **L2**) and **Fast forward** (hold **R2**, or double-tap **R2** to lock it on and double-tap again to
  turn it off) are also in **Settings > Gameplay**; **Reset game** restarts the game (anything unsaved is lost).

States and battery saves live inside slot's own app storage (`files/states` and `files/saves`), so they are removed if
the app is uninstalled; updates keep them.

**gpSP** needs `gba_bios.bin` in `Android/data/fyi.slot.android/files/system`. Without it slot quietly uses mGBA.

## Systems that open your own emulators

- **Built-in emulators:** PlayStation (PCSX ReARMed), N64 (Mupen64Plus-Next), PSP (PPSSPP) and Dreamcast (Flycast, experimental) run inside slot. They read games where they are, so slot asks once for "All files access". The installed emulator apps remain a second choice (START, or Settings > Emulators). N64 games in a zip are unpacked automatically. Dreamcast defaults to Redream because Flycast shows only a black screen on the devices tested; to try it anyway, put `dc_boot.bin` and `dc_flash.bin` in your Dreamcast folder. PlayStation 2 and GameCube still open ARMSX2/AetherSX2 and Dolphin.
Discs and cartridges slot doesn't emulate itself are launched in the emulator app you already have. Put games in a
folder named after the system (`PSX`, `PS2`, `PSP`, `N64`, `GC`/`GameCube`, `DC`/`Dreamcast`, in almost any spelling) inside the ROM folder you picked.

| System | Emulator it opens | Notes |
|---|---|---|
| PlayStation | DuckStation | `.chd`, `.cue` (the `.bin` next to it is hidden), `.pbp`, `.iso` |
| PlayStation 2 | ARMSX2 or AetherSX2 | `.iso`, `.chd`, `.cso`, `.cue` |
| Nintendo 64 | M64Plus FZ (Pro) | `.z64`, `.n64`, `.v64`, and `.zip` straight from the folder |
| GameCube | Dolphin, or Dolphin MMJR | `.iso`, `.ciso`, `.gcm`, `.rvz`, `.gcz`, `.wbfs` |
| PSP | PPSSPP | `.iso`, `.cso`, `.pbp`, `.chd` |
| Dreamcast | Redream | `.chd`, `.gdi`, `.cdi`, `.cue` |
| Xbox | hakuX | `.iso`, `.xiso` (folder `Xbox`) |
| PlayStation 3 | ARMSX3 | `.iso` (folder `PS3`) |
| Xbox 360 | Xendroid+ | `.iso`, `.xex`, `.zar` (folder `X360` / `Xbox 360`) |

PSP games are UMDs (a rounded caddy with the disc inside). Press START on a game to flip the disc and pick the emulator when there is more than one. When you quit the emulator
and come back to slot, the disc pops back out of the slot. Cover art for discs is cropped from the libretro box art.
Every system slot emulates itself also offers **RetroArch** as a choice (START on a game, or Settings > Emulators). Slot opens RetroArch with the same libretro core it bundles (mGBA, Gambatte, FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast, PCSX ReARMed, Mupen64Plus-Next, PPSSPP, Flycast), so download that core once in RetroArch's Online Updater.

PS2 games open in ARMSX2 by default (it now runs better on Mali chips too); if ARMSX2 is not installed, AetherSX2 / NetherSX2 is used. To change that for every game, use **Settings > Emulators**, which lists each system that has more than one emulator or core (GBA, GB, GBC, PlayStation 2, GameCube) and lets you pick the default. Press START on a single disc or cart to pick one just for that game.

Folder names are matched loosely, so `DC`, `dc`, `Dreamcast`, `Sega Dreamcast (Games)`, `PS1`, `Sony PlayStation`, `Game Cube`,
`NGC`, `Nintendo 64`, `Mega Drive`, `Super Nintendo` and so on all work, anywhere in the folder tree you picked.

## Android apps and games

Nothing is shown by default. Open **Settings > Apps & games** to see every app on the device (Android's own "game"
tag is shown next to the ones it thinks are games) and set each one to **Hidden**, **App** or **Game**. Apps get an
**Apps** shelf and games an **Installed** shelf, as square Switch-style cards with the app icon on the face.
They slide into the slot like any other cart, and coming back to slot ejects them.

### App drawer

Press **Y** (or tap the grid button beside the clock) to open a launcher with every app on the device, as a grid or a
list. D-pad moves, **A** opens, **Y** (or a long press on touch) shows **Open / App info / Uninstall** for the app, **X** flips grid/list, **L1/R1** change the icon size. The layout and size options, plus
**Cart size** (XS, Small, Default, Large, XL), are under **Settings > Interface**.



## Building

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35). On a new machine, create `local.properties` containing `sdk.dir=/path/to/Android/Sdk`
(Android Studio does this for you when you open the project).

```bash
scripts/fetch-cores.sh          # downloads the arm64 libretro cores into app/src/main/jniLibs
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

Every build (debug or release, on any computer) is signed with the key in `app/slot-signing.keystore`, so a new APK
always updates the installed app in place and keeps its data. It is a plain debug key and is public on purpose; never
swap it for another one, or Android will insist on an uninstall, which wipes the app's settings and saves.

Your Apps & games choices and settings are also copied to a hidden `.slot-backup.json` in the ROM folder you picked
(once the folder has been picked with write access). If you ever do have to reinstall, pick the same folder again and
they come back.

The UI lives in `ui/`, a Kotlin Multiplatform Compose module, so it can also be rendered headlessly on a desktop:

```bash
./gradlew :ui:renderPreviews -Pscene=anim     # PNG filmstrips into ui/build/previews
```

The game runs in its own process (`GameActivity`, `:game`) so a core that crashes can't take the launcher with it.

## Notes

* All motion is driven by the frame clock instead of Compose's animation APIs. Many handhelds set the system
  "animator duration scale" to 0, which would otherwise freeze every animation.
* Not ported yet: link-cable play, Cart Studio labels,
  the N64/PSX/etc. systems.

## Credits and licences

* **slot** by Brandon Kowalski, GPL-3.0: the design, the cart/board/socket artwork, colour tables and motion constants
  are taken from <https://github.com/BrandonKowalski/slot>. This port is therefore also GPL-3.0 (see `LICENSE`).
* Open Sans (OFL) for the lettering, from slot's assets.
* [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) (GPL-3.0) and the libretro cores, each under its own licence
  (mGBA MPL-2.0, gpSP GPL-2.0, Gambatte GPL-2.0, FCEUmm GPL-2.0, Beetle PCE Fast GPL-2.0, PCSX ReARMed GPL-2.0,
  Mupen64Plus-Next GPL-2.0, PPSSPP GPL-2.0, Flycast GPL-2.0;
  Snes9x and Genesis Plus GX are free for non-commercial use only, so slot must not be sold).
* No games, BIOS or firmware are included; you supply your own.
* Thumbnails come from the libretro thumbnail server.
