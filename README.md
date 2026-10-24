<div align="center">

<img src="docs/assets/icon-512.png" width="96" alt="GB Launcher icon">

# GB Launcher

**My home screen for Google TV and Android TV: no ads, 60 fps, and a look that borrows from tvOS.**

Made by [Gabriel Baumgratz](https://github.com/gabrielbaumgratz) for his own TVs, shared in case it helps yours.<br>
Inspired by [Free My TV](https://github.com/simbasjungles/free-my-tv). Open source. One button to undo.

English · [Português](README.pt.md)

[What you get](#what-you-get) · [Screenshots](#screenshots) · [Install](#install) · [AirPlay](#airplay) · [Settings](#settings) · [Build](#build-it-yourself) · [Credits](#credits)

<img src="docs/assets/en/home.png" width="880" alt="The GB Launcher home screen: three tabs, big picks from your streaming apps, and a glass shelf of apps">

</div>

## What you get

- **No ads.** No sponsored rows, banners or autoplaying promos.
- **Your apps in front.** A glass shelf with the apps you choose, in the order you choose, edited on the TV.
- **Trending picks from the apps you have.** Three tabs (Explore, Movies, Shows), fed by TMDB with your own free key. Without a key everything else still works.
- **A Control Center.** Press the info button on the remote (or the first round button at the top): Bluetooth headphones in one click, volume, light and dark theme, picture mode and AirPlay.
- **A drawer one press down.** HDMI inputs with a live signal marker, shortcuts, and the weather with an hourly outlook, with no key.
- **A screensaver you can read from the couch.** Full-screen photos, a frosted translucent clock at the top left, the date and the weather at the top right. Pick how much it dims for a dark room.
- **Photos from your phone.** Open a web page on your phone, type the PIN shown on the TV, and send photos for the screensaver and the background. Local network only.
- **Settings laid out like the iPad's.** Categories in a sidebar, grouped cards on the right: General, Home Screen, Weather, Screensaver, Remote Buttons, About.
- **Your name.** "Good morning, Ana": type the name you want in Settings, or leave it empty.
- **Remote buttons that open what you want.** Netflix, YouTube, Prime Video and the brand buttons (TCL, Sony, Vestel, KTC, Formovie) can open any installed app.
- **English, Portuguese and Spanish.** Picked in Settings or taken from the TV.
- **Safe.** No root. Nothing is uninstalled. *Launcher → Original* brings the factory home screen back, and a watchdog does it by itself after repeated crashes.

## Screenshots

| | English | Português |
|---|---|---|
| Home | <img src="docs/assets/en/home.png" width="420"> | <img src="docs/assets/pt/home.png" width="420"> |
| Drawer | <img src="docs/assets/en/drawer.png" width="420"> | <img src="docs/assets/pt/drawer.png" width="420"> |
| Control Center | <img src="docs/assets/en/control-center.png" width="420"> | <img src="docs/assets/pt/control-center.png" width="420"> |
| Settings | <img src="docs/assets/en/settings.png" width="420"> | <img src="docs/assets/pt/settings.png" width="420"> |
| Screensaver settings | <img src="docs/assets/en/settings-3.png" width="420"> | <img src="docs/assets/pt/settings-3.png" width="420"> |
| Remote buttons | <img src="docs/assets/en/settings-4.png" width="420"> | <img src="docs/assets/pt/settings-4.png" width="420"> |
| Photos from your phone | <img src="docs/assets/en/photos.png" width="420"> | <img src="docs/assets/pt/photos.png" width="420"> |

### The screensaver

<img src="docs/assets/en/screensaver.webp" width="720" alt="The screensaver: a full-screen photo, a frosted clock at the top left, date and weather at the top right">

[Watch it as a video](docs/assets/en/screensaver.mp4) · [Em português](docs/assets/pt/screensaver.mp4)

### The page on your phone

<img src="docs/assets/en/upload-phone.png" width="300" alt="The upload page on a phone: a PIN field and an Enter button">

## Install

You need a Google TV or Android TV (Android 8 or newer) and a computer on the same Wi-Fi. About ten minutes.

### With an AI assistant

1. **On the TV**, turn on developer mode: Settings → System → About → press *Android TV OS build* 7 times. Then Settings → System → Developer options → turn on **USB debugging** (or network debugging). Write down the TV's IP address (Settings → Network & Internet).
2. **On your computer**, open an AI coding assistant ([Claude Code](https://claude.com/claude-code), Codex or Antigravity) and say:
   > Install GB Launcher (github.com/gabrielbaumgratz/LauncherGB) on my TV at 192.168.1.50. I live in Lisbon.
3. **On the TV**, open *GB Launcher* → *Launcher* → *GB Launcher*. The TV asks *Allow USB debugging?*: tick *Always allow* and accept.

### By hand

1. **On the TV**, do step 1 above.
2. **Tools:** a JDK 17+, the Android SDK command-line tools (build-tools 34, platform android-34) and adb.
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools   # macOS
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   On Linux use OpenJDK 17 and Android's command-line tools; on Windows use Git Bash or WSL.
3. **Build:**
   ```bash
   ./build.sh
   ```
   The first run creates `config.properties`. Fill in your city and, optionally, your [TMDB key](#the-tmdb-key), then run it again. The result is `GBLauncher.apk`.
4. **Install:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r GBLauncher.apk
   ```
   On TCL TVs, also let it run in the background (TCL blocks it by default, and the switch never starts):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
5. **On the TV**, open *GB Launcher* → *Launcher* → *GB Launcher*, and accept *Allow USB debugging?* once more (this time for GB Launcher itself).

Updates must be signed with the same key: keep `freemytv.keystore`.

**Tested on:** TCL 32S5K (Google TV, Android 14). It should run on any Android TV or Google TV from Android 8 on, such as Sony Bravia, Chromecast with Google TV, Xiaomi Mi Box, Philips, onn. and NVIDIA Shield.

## AirPlay

GB Launcher doesn't ship an AirPlay receiver. It works with a free one, **[android-airplay-server](https://github.com/jqssun/android-airplay-server)** by [jqssun](https://github.com/jqssun): the first open-source AirPlay receiver for Android, with screen mirroring, video and audio (GPL-3.0). Install its APK from the [releases page](https://github.com/jqssun/android-airplay-server/releases):

```bash
adb install -r android-airplay-server.apk
```

Once it's on the TV, an **AirPlay** button shows up in the Control Center and opens the receiver. Then, on an iPhone, iPad or Mac, pick the TV in the AirPlay menu.

## Settings

Everything is on the TV, in *GB Launcher* (the gear at the top, or the app's icon). The first values come from `config.properties`.

| Category | What's in it |
|---|---|
| **General** | Switch between GB Launcher and the original home screen · your name · language · theme (light, dark, automatic) · glass · navigation sounds · the TV's own settings |
| **Home Screen** | The shelf (apps and order) · the round buttons at the top · which HDMI inputs and Bluetooth headphones show up |
| **Weather** | Weather widget on or off · °C, °F or automatic · your city |
| **Screensaver** | Preview · photos from your phone · mine or recommendations · photo interval · brightness (standard, dimmer, dimmest) |
| **Remote Buttons** | The app each remote button opens |
| **About** | Version · credits · memory and speed against the original home screen · recommendations status · notices |

| `config.properties` key | What it does | Default |
|---|---|---|
| `tmdb_key` | Your free TMDB key, for the picks | empty |
| `tmdb_region` | Your country (two letters) | `US` |
| `latitude`, `longitude` | Your city, for the weather and the sunset | New York |
| `city` | The name on the weather card | `Weather` |
| `language` | `auto`, `en`, `pt` or `es` | `auto` |
| `temperature` | `C`, `F` or `auto` | `auto` |

### The TMDB key

Free, two minutes: create an account at [themoviedb.org](https://www.themoviedb.org/signup), open [Settings → API](https://www.themoviedb.org/settings/api) and request a developer key. Paste it after `tmdb_key=`. It's yours: `config.properties` stays out of git.

### Photos from your phone

*Settings → Screensaver → Photos from your phone.* The TV shows an address and a PIN. On your phone (same Wi-Fi), open the address, type the PIN and choose photos. They're turned upright and saved again for the TV, without their GPS tags. The server runs only while that screen is open, and the PIN locks after a few wrong tries.

## Build it yourself

No Gradle, no libraries: only the Android SDK.

```bash
export ANDROID_HOME="/path/to/android/sdk"
./build.sh            # GBLauncher.apk
tools/check.sh        # the build and the translation catalogues
```

Texts live in `app/assets/i18n/<lang>.json`; `python3 tools/translate.py` lists what a language lacks.

## Credits

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)**, by its contributors (MIT): GB Launcher grew from it. The Gradle-free build, the canvas-drawn home screen, the switch to and from the original launcher and the watchdog are theirs.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, by jqssun (GPL-3.0): the AirPlay receiver GB Launcher opens.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): the icons.
- **[TMDB](https://www.themoviedb.org)**: the picks. This product uses the TMDB API but is not endorsed or certified by TMDB.
- **[Open-Meteo](https://open-meteo.com)**: the weather, with no key.
- **SF Pro**, by Apple: the typeface.

## License

[MIT](LICENSE).
