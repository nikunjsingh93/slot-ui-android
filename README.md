# slot. for Android

An Android port of [slot](https://slot-cfw.fyi), Brandon Kowalski's Game Boy-style frontend for the Anbernic RG SP,
extended to more systems and to touch screens.

You get the same cart shelf, the same slot you push carts into, a board you flip over to choose an emulator core,
polaroid-style save states, and the same motion: a critically damped shelf spring, the "catch" when a cart meets
the contacts, neighbours sliding aside, the screen dimming into the game.

| | |
|---|---|
| Systems | GBA, GBC, GB, NES, SNES, Mega Drive/Genesis, Master System, Game Gear, PC Engine |
| Cores | mGBA, gpSP, Gambatte, FCEUmm, Snes9x, Genesis Plus GX, Beetle PCE Fast (libretro, via [LibretroDroid](https://github.com/Swordfish90/LibretroDroid)) |
| Input | Gamepad first (handhelds), touch controls and swipe when no controller is connected |
| Target | Android 8+, arm64 |

Tested on an Anbernic RG476H (Android 13, 1280x960).

## Using it

1. Install the APK. On a handheld you can also pick **slot.** as your home app.
2. Press **A** on the empty shelf (or tap it) and choose your ROM folder. ROMs may be loose files or `.zip`s.
   Alternatively drop ROMs into `Android/data/fyi.slot.android/files/roms` and no permission is needed.
3. Cart art is a title screen fetched from the libretro thumbnail server and cached. Put your own
   `<rom name>.png` into `Android/data/fyi.slot.android/files/labels` to override it (turn the download off in
   Settings > Library if you prefer).

| Button | Shelf | In game |
|---|---|---|
| Left / Right | browse (hold to speed up) | |
| Up / Down | jump by letter | |
| L1 / R1 | switch system | |
| A | play (tap resumes, hold for a new game) | |
| START | flip the cart: choose the core | |
| MENU | settings | pause menu; press twice for save states |
| L2 / R2 | | rewind / fast-forward (double-tap R2 to lock) |
| X / Y | | turbo A / B on GB, GBA, NES, SMS/GG, PCE |

If your handheld reports A/B the other way round, flip **Settings > Controls > Swap A/B and X/Y**.

**gpSP** needs `gba_bios.bin` in `Android/data/fyi.slot.android/files/system`. Without it slot quietly uses mGBA.

## Building

Requirements: JDK 17 and the Android SDK (platform 35, build-tools 35). On a new machine, create `local.properties` containing `sdk.dir=/path/to/Android/Sdk`
(Android Studio does this for you when you open the project).

```bash
scripts/fetch-cores.sh          # downloads the arm64 libretro cores into app/src/main/jniLibs
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

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
  (mGBA MPL-2.0, gpSP GPL-2.0, Gambatte GPL-2.0, FCEUmm GPL-2.0, Beetle PCE Fast GPL-2.0,
  Snes9x and Genesis Plus GX are non-commercial).
* Thumbnails come from the libretro thumbnail server.
