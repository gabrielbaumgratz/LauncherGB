<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-white.png">
  <img src="docs/assets/logo-black.png" width="220" alt="GB Home TV">
</picture>

# GB Home TV

**A faster, ad-free home screen for your Google TV or Android TV.**<br>
One click switches. Another brings the original back.

English · [Português](README.pt.md) · [Español](README.es.md)

[Why](#why) · [Install](#install) · [After installing](#after-installing) · [How to use](#how-to-use) · [Features](#features) · [Screenshots](#screenshots) · [What it turns off](#what-it-turns-off) · [Memory](#memory-ram) · [FAQ](#faq)

<img src="docs/assets/en/hero.gif" width="880" alt="GB Home TV: the home screen, the drawer, weather and games, and the Control Center">

</div>

## Why

| | |
|---|---|
| **No ads** | No sponsored rows, banners or videos that start on their own. |
| **Faster** | On a real TV: 154 ms to show the home screen, against 217 ms for the original. |
| **Ready out of the box** | Uses your TV's language, theme, units and clock. Change it later if you like. |
| **Reversible** | Uninstalls nothing. *Launcher → Original* brings everything back, and after 3 crashes it goes back by itself. |
| **Free and open** | No account, no subscription. |

## Install

**You need:** a TV with Google TV or Android TV (Android 8+) and a computer on the same Wi-Fi.

### 1. Prepare the TV (same for both ways)

1. *Settings → System → About*: press **7 times** on *Android TV OS build* (or *Build number*).
2. *Settings → System → Developer options*: turn on **USB debugging** (or *Network debugging*).
3. *Settings → Network & Internet* → your Wi-Fi: note the **IP address** (like `192.168.1.50`). Yours will differ.

### 2A. With an AI assistant (easiest)

Open a coding assistant ([Claude Code](https://claude.com/claude-code), Codex, Antigravity…) and paste this, using your TV's IP:

> Install GB Home TV (github.com/gabrielbaumgratz/LauncherGB) on my TV at 192.168.1.50.

It downloads, builds and installs. When the TV asks *Allow USB debugging?*, tick *Always allow*.

### 2B. By hand

```bash
# Tools: JDK 17+, Android SDK (build-tools 34, android-34) and adb
./build.sh                                 # makes GBHomeTV.apk
adb connect 192.168.1.50:5555              # your TV's IP
adb install -r GBHomeTV.apk
```

- **TCL:** also run `adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"` (without it TCL blocks the switch).
- **macOS / Linux:** install the tools with `brew install --cask temurin android-commandlinetools android-platform-tools` or your package manager. **Windows:** use Git Bash or WSL.
- Keep `freemytv.keystore`: updates must be signed with the same key.

### 3. Switch on the TV

Open **GB Home TV → General → Launcher → GB Home TV** and accept USB debugging once more. Press Home: it's there.

## After installing

All optional. Everything else already works without it.

### Recommendations (TMDB key)

Shows trending titles from the streaming apps you have. Needs a free key.

1. Sign up at [themoviedb.org](https://www.themoviedb.org/signup) and confirm your email.
2. Profile picture → *Settings* → *API* → request a **Developer** key (use: "personal, TV home screen").
3. Copy the **API Read Access Token**.
4. In `config.properties` (created by the first `./build.sh`), paste it after `tmdb_key=`, no spaces or quotes.
5. Build and install again (`./build.sh`, then `adb install -r GBHomeTV.apk`), or ask the AI.

The key is yours: don't share it. `config.properties` never goes to GitHub.

<details>
<summary>Other <code>config.properties</code> options</summary>

| Key | What it does | Default |
|---|---|---|
| `tmdb_region` | Your country (two letters) | the TV's |
| `latitude`, `longitude`, `city` | Your city, for weather and sunset | from the time zone |
| `language` | `auto`, `en`, `pt` or `es` | `auto` |
| `temperature` | `C`, `F` or `auto` | `auto` |

Build again after changing.
</details>

### AirPlay

GB Home TV only adds the button; the free app [android-airplay-server](https://github.com/jqssun/android-airplay-server) (jqssun, GPL-3.0) does the receiving.

1. Download the `.apk` from [Releases](https://github.com/jqssun/android-airplay-server/releases).
2. `adb install -r file.apk` (or ask the AI).
3. The Control Center now has an **AirPlay** button. On your iPhone, iPad or Mac, pick the TV in the AirPlay menu.

> Tip: that app uses memory while it's open (we measured [~40 MB less free](#memory-ram)). On low-RAM TVs, close it when you're not using it.

### Photos from your phone

*Settings → Screensaver → Photos from your phone.* Point your camera at the QR code on the TV (on a computer, type the address and PIN) and pick photos. Local network only, the PIN locks after wrong tries, and the server runs only while that screen is open.

### Weather and sports

No key and no install. *Settings → Weather* and *Sports*.

## How to use

| | |
|---|---|
| **Home** | Your apps on the glass shelf, and highlight tabs. |
| **Down** | The drawer: HDMI inputs, shortcuts, weather and games. |
| **First round button, at the top** | The Control Center: Bluetooth, volume, theme, picture, AirPlay. |
| **Gear** | Settings. *Launcher → Original* returns to the TV's own screen. |
| **TV idle** | The screensaver, with your photos. |

### Remote buttons

Every remote is different, so nothing is tied to a button. To open something with a button on your remote:

*Settings → Remote Buttons → Add a button*, press the button and choose what it opens: **an app**, **the Control Center** or **the TV's settings**. Works on any brand. On TCL, Sony, Vestel, KTC and Formovie, the factory app buttons are recognized too.

## Features

| | |
|---|---|
| **Control Center** | You pick which blocks show. |
| **Sports** | Your teams, your athletes by name (tennis players, golfers, Formula 1 drivers, UFC fighters) and leagues, from ESPN (no key). Tennis shows the top 10 of each ranking. |
| **Screensaver** | Full-screen photos, clock, date and weather. Six brightness levels. |
| **Highlights** | Trending titles from your streaming apps. |
| **Bluetooth** | Choose, rename and forget devices. |
| **Remote buttons** | Teach any button to open an app or a screen, name it, and restore every button to default in one click. |
| **Light, dark or automatic theme** | Automatic follows the sun where you live; you can also pick one for good. |
| **English, Portuguese, Spanish** | Follows the TV unless you choose. |

## Screenshots

| | |
|---|---|
| **Home** | <img src="docs/assets/en/home.png" width="440"> |
| **Drawer** | <img src="docs/assets/en/drawer.png" width="440"> |
| **Control Center** | <img src="docs/assets/en/control-center.png" width="440"> |
| **Settings** | <img src="docs/assets/en/settings.png" width="440"> |
| **Games** | <img src="docs/assets/en/settings-games.png" width="440"> |
| **Screensaver** | <img src="docs/assets/en/settings-screensaver.png" width="440"> |
| **Performance** | <img src="docs/assets/en/settings-performance.png" width="440"> |
| **Remote buttons** | <img src="docs/assets/en/settings-remote.png" width="440"> |
| **Photos from your phone** | <img src="docs/assets/en/photos.png" width="440"> |

In motion: [Control Center](docs/assets/en/control-center.webp) · [Settings](docs/assets/en/settings.webp) · [Screensaver](docs/assets/en/screensaver.mp4)

## What it turns off

**Why:** a TV ships with manufacturer apps that run all the time: ad-supported channels, store demos, usage measurement and ad services. They use memory and slow the TV down. When you switch to GB Home TV, it **turns off** (never uninstalls) only the ones it knows, so the TV gets lighter and ad-free.

| Kind | Examples |
|---|---|
| Ad-supported channels | TCL Channel, Mi TV+, Coolita |
| Store demo | Store mode, demo manuals |
| Usage and ad measurement | Xiaomi analytics, Anoki ACR, Android ad targeting |
| Support and logs | Repair guides, log collection |

**Never touched:** picture, sound, HDMI inputs, remote, updates and Google services. *Launcher → Original* turns everything back on. Full list in [`HomeMode.java`](app/src/io/freemytv/HomeMode.java).

## Memory (RAM)

1 GB TVs (many TCL and Xiaomi/Redmi) have little memory to spare. What helps most, in order:

1. **Close the AirPlay receiver when you're not using it.** Measured on a 1 GB TCL (3 free-memory readings):

   | | Free memory |
   |---|---|
   | AirPlay app open all the time | ~184 MB |
   | AirPlay app closed | ~223 MB |

2. **Switch to GB Home TV:** it turns off the manufacturer apps that keep running in the background (see above).
3. **Switch to the Performance mode:** *Settings → Performance → Mode*. *Full* keeps everything; *Performance* turns off recommendations, the weather and games widgets and the slow zoom (the page lists what each mode does). Saves about 5 MB.

No spin on the numbers: GB Home TV opens faster, but it draws everything itself and uses **more** memory than the stock screen (152 MB against 89 MB on a TCL 32S5K). The gain is speed and no ads, and turning off the manufacturer apps makes up for part of it. *Settings → About* measures it on your TV.

## FAQ

**Will it break my TV?** No. It's a regular app; it doesn't touch the system or uninstall anything.

**How do I go back?** *Settings → General → Launcher → Original.*

**Which TVs?** Tested on a TCL 32S5K (Google TV, Android 14). Should run on any Android TV or Google TV (Android 8+): Sony, Xiaomi, Philips, onn., Chromecast with Google TV, NVIDIA Shield. LG and Samsung don't run Android: use a Google TV streamer on HDMI.

**Does it collect data?** No. The only connections are weather (Open-Meteo), highlights (TMDB, if you add a key) and games (ESPN).

**Something went wrong.** Open an [issue](https://github.com/gabrielbaumgratz/LauncherGB/issues) with your TV model and what you saw.

## Build it yourself

No Gradle, no libraries: only the Android SDK.

```bash
export ANDROID_HOME="/path/to/android/sdk"
./build.sh            # GBHomeTV.apk
tools/check.sh        # build and translation catalogues
```

## Credits

Made by [Gabriel Baumgratz](https://github.com/gabrielbaumgratz), inspired by [Free My TV](https://github.com/simbasjungles/free-my-tv).

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)** (MIT): GB Home TV started from it. The Gradle-free build, the canvas-drawn home screen, switching to and from the original launcher and the watchdog are theirs.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, by jqssun (GPL-3.0): the AirPlay receiver.
- **SF Pro**, by Apple: the font, via [sahibjotsaggu/San-Francisco-Pro-Fonts](https://github.com/sahibjotsaggu/San-Francisco-Pro-Fonts); Apple's own terms still apply.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): the icons.
- **[TMDB](https://www.themoviedb.org)**: the highlights. Uses the TMDB API, not endorsed or certified by TMDB.
- **[Open-Meteo](https://open-meteo.com)**: the weather. **ESPN**: public game data.

## License

[MIT](LICENSE).
