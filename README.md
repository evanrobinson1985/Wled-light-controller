# WLED Controller for Android

A native Android app (Kotlin + Jetpack Compose, Material You) for controlling
[WLED](https://kno.wled.ge) LED controllers. Built for large phones like the
Samsung Galaxy S23 Ultra, it covers everything the WLED app does and adds
multi-device control, scenes, and nicer controls.

## Features

**Everything you get in WLED**
- Power, master brightness, colour wheel, three colour slots (labelled per effect)
- RGBW white channel and CCT (white temperature) when the strip supports it
- All effects with their real slider/option labels (speed, intensity, custom 1–3, options) read from the device
- All palettes, segments (select, on/off, brightness, start/stop, grouping, spacing, offset, reverse, mirror, freeze, add/delete/rename)
- Presets and playlists: apply, save, overwrite, delete
- Sleep timer (WLED nightlight: instant, fade, colour fade, sunrise)
- UDP sync send/receive toggles, realtime-mode override, reboot
- Full WLED web interface built in for settings (Wi-Fi, LED hardware, usermods…)

**Extras**
- Auto discovery: mDNS plus a fast subnet scan, "Add all"
- Live two-way sync over WebSocket, with HTTP polling fallback
- Instant, optimistic controls: slider drags are throttled and merged so the ESP isn't flooded
- Groups: control several devices together (power, brightness, colour, effect by name, speed/intensity)
- Scenes: snapshot the look of any set of devices and restore them with one tap
- Holiday themes (Christmas, Halloween, Hanukkah, Diwali, July 4th, Valentine's and more): in-season ones listed first, one-tap apply with a matching effect, and a Remix button that generates fresh variations
- LED setup in the app: chip type (WS2811 / WS2812B / SK6812 …), LED count, start, data pin, colour order (with a red/green/blue test), controller position (left or right end), skip, multiple outputs and power limit — saved to the controller with a reboot
- Light types (LED strip, bullet / pixel string, matrix) per device or per segment: suggested effects, slower defaults and colour bands for bullets, 2D effects for matrices
- Props & groups: mark each run as a line, arch, tree, window, candy cane, star, matrix or bush (sets its natural direction) and group them ("All arches")
- Shows (xLights-style sequencer): tracks for the whole house, groups or single props; place, drag, resize and layer effects (wipe, chase, colour wave, rainbow, comet, bars, pulse, twinkle, sparkle, shimmer, strobe, fireworks, snow) with colours, direction, speed, size, brightness and fades; pinch-zoom timeline, live preview on the house photo and live streaming to the lights while you edit or play
- Music: give a show a song (it plays in sync and drives the timeline), see its waveform, detect beats automatically or tap them in, snap effects to beats, and fill a track with flashes/pulses/sparkles/chases on every beat or bar
- Show schedule: playlists of shows (ordered, looping, gaps between songs) and time windows by day of week (past midnight OK, optional lights-off at the end); runs in the background with the screen off, restarts after a reboot, and has Play now for testing
- xLights import: play .fseq sequences (v1/v2, zstd/zlib/uncompressed, sparse) on your controllers — map each controller's start channel automatically in order or from xlights_networks.xml (matched by IP), preview on the house map, add the song, and use them in playlists and the schedule
- Sequencer layering like xLights: each track has a blend mode (brightest, normal, add, subtract, average, mask, reveal) over the tracks below it, and "Add layer above" stacks effects on the same props; value curves (ramp, up and down, ease, wave, saw, square, random) for speed, size and brightness; in/out transitions (fade, wipe, from middle, from ends, circle, dissolve, blinds)
- Prop models from their wiring: matrix (strings, LEDs per string, direction, start corner, zig-zag), mega tree (180/270/360°, from top or bottom) and star (points, LEDs per point), placed by tapping two corners; their parts (columns, strings, points) and your own LED ranges can be show targets
- Sharing with xLights: export any show as .fseq (40 fps, channel map shown) and import xLights .xsq sequences as editable shows — models matched to props, groups or parts by name, layers, colours, brightness, fades, transitions and the timing track carried over
- More effects: spirals, butterfly, meteors, fire, ripple, shockwave, curtain, pinwheel, galaxy, plasma, marquee, garlands, scrolling text, pictures, and music-driven VU meter, music pulse, beat flash and Sing
- Singing faces: a face prop model (eyes and closed, open and wide mouths) that sings along with the song under the Sing effect
- Other controllers: Falcon, Kulp, ESPixelStick, Falcon Player and the like as E1.31 (sACN), Art-Net or DDP outputs (Settings), mapped on the house and driven by shows and whole-house effects
- Schedule from sunset or dusk (with an offset) until a time or sunrise, only between dates (e.g. Nov 25 – Jan 6), using your location
- Show editing: undo/redo, long-press to multi-select effects and drag them together, copy/cut/paste (across tracks and shows, pasted at the playhead), and speed / brightness ramps within an effect
- Light show simulator (🧪 on the home screen): swaps in a virtual house with four simulated WLED controllers (roofline strip, bullet lights on the windows and door, a mega tree, arches and bushes) so every feature can be tried without real lights — effects, palettes, segments, presets and playlists, holiday themes, LED setup, nightlight, power limiter, groups and scenes, whole-house effects, standalone mode, shows and the schedule. The house is drawn live (tap a light to open its controller) and a small floating live view follows you round the app. Add your own simulated controllers of any size; your real setup is saved and comes back when you leave
- - Backup & restore (⚙ Settings): one .zip with controllers, groups, scenes, favourites, the house map and photo, shows with their songs, imported xLights sequences, playlists and schedules — for a new phone or after a reset
- Lights delay (app-wide) to line the lights up with Bluetooth speakers, and a home-screen card showing what the show schedule is playing or when the next show starts
- Real-world LED spacing per light run: set the house scale once (tap two points a known distance apart, e.g. the ends of a wall), then give each run its spacing (presets for 4 in bullets, C9, 30/60/144 per metre strip) and its LEDs are placed that far apart along the traced line — on the map, in previews and in the simulator
- Hidden stretches: lights that wrap round a porch or corner, or go behind something, can be marked hidden while tracing (with their real length in feet and inches, or LED count) or LED by LED after a camera scan — they aren't drawn on the photo, the LEDs after them stay lined up, and effects and shows still flow through them
- Camera auto-mapping: prop the phone up facing the house and each light flashes in turn while the camera locates it; then fine-tune in the editor (pinch-zoom, drag lights, remove strays, fill gaps, place missed lights one by one, add/remove LEDs)
- Whole-house effects: trace each run of lights on a photo of your house, then play position-based effects (colour wave, rainbow sweep, wipe, ripple, fireworks, snow, twinkle …) across every controller in sync — streamed from the phone over DDP (keeps running with the screen off), or set up one ESP32 controller as master with the others as network outputs and a 2D LED map so WLED's own 2D effects span the house without the phone (backed up, one-tap undo)
- Palette gradient previews, searchable effect/palette lists, effect favourites, 2D/audio filters
- Favourite and recent colours, hex entry, random colour, white-temperature presets
- Quick Settings tile to toggle a light from the notification shade
- Device dashboard: estimated current/power, FPS, Wi-Fi signal, uptime, memory
- Large gradient sliders with haptic feedback; dynamic Material You colours

## Install on your phone

Every push builds a signed APK in GitHub Actions:

1. Open the repository's **Actions** tab → latest **Build APK** run → download **WLED-Controller-apk**.
2. Unzip it and open `WLED-Controller.apk` on your phone (allow "Install unknown apps" for your browser/Files app when asked).

Pushing a tag like `v1.0.0` also attaches the APK to a GitHub Release.

The APK is signed with the key in `keystore/` so newer builds install over older ones
without uninstalling. It's a development key — fine for personal use, not for the Play Store.

## Building locally

Requires JDK 17 and the Android SDK (Android Studio works out of the box).

```
./gradlew assembleRelease
```

Unit tests (state patching, schedule windows, FSEQ decoding, beat detection, house map layout,
sequence rendering, the simulated controllers) run on the JVM and in CI before every build:

```
./gradlew testReleaseUnitTest
```

## Usage tips

- Your phone must be on the same Wi-Fi network as your WLED devices.
- Add the Quick Settings tile by editing your quick panel; pick which device it controls
  from a device's ⋮ menu → *Use for Quick Settings tile*.
- In **Segments**, the checkboxes choose which segments the colour/effect/palette controls change.
- Long-press a scene or favourite colour to remove it.
