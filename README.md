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

## Usage tips

- Your phone must be on the same Wi-Fi network as your WLED devices.
- Add the Quick Settings tile by editing your quick panel; pick which device it controls
  from a device's ⋮ menu → *Use for Quick Settings tile*.
- In **Segments**, the checkboxes choose which segments the colour/effect/palette controls change.
- Long-press a scene or favourite colour to remove it.
