# GB Launcher

English | [Português](README.pt.md)

**A clean, fast, ad-free Apple TV style launcher for Google TV and Android TV.**

![GB Launcher Home](docs/assets/home.png)

GB Launcher replaces the cluttered, slow, ad-heavy interface on your Smart TV with a clean, fluid, and responsive experience inspired by Apple TV and tvOS. Built in pure native Java with zero heavy frameworks, it runs locked at 60 FPS while protecting your privacy.

Keywords: Apple TV Launcher, tvOS Launcher, Google TV Apple Style Launcher, Clean Android TV Launcher, Ad-Free TV Launcher.

---

## Comparison: What Changes on Your TV

| What You Gain | What You Lose | What Stops Tracking You |
| :--- | :--- | :--- |
| Clean, ad-free home screen | Full-screen video ads that play automatically | Continuous tracking of your viewing habits |
| Smooth 60 FPS remote navigation | Lag, bloatware, and frozen screens | Constant telemetry sent to ad networks |
| Slide-over Control Center | Cluttered sponsored content rows | Unwanted promotional banners |
| Instant Bluetooth audio switching | Locked, unchangeable interface | Background usage logging |
| Bottom drawer with HDMI inputs and weather | | |
| Custom remote button remapping | | |
| Safe 1-click restore to factory launcher | | |

---

## Screenshots

### Home Screen
Clean layout showing your favorite apps, clean tabs, and fluid navigation.

![Home Screen](docs/assets/home.png)

### Control Center
Slide-over side panel accessible from anywhere. Connect or disconnect Bluetooth devices (headphones, soundbars, speakers), adjust volume, switch picture modes, or toggle Dark / Light themes without digging through TV system menus.

![Control Center](docs/assets/control-center.png)

### Bottom Drawer (Inputs, Shortcuts & Weather)
Press Down on your remote to reveal HDMI inputs (with connected signal indicators), quick app shortcuts, and an accurate local weather forecast with high/low temperatures and hourly outlook.

![Bottom Drawer](docs/assets/drawer.png)

### iPad-Style Settings & Remote Button Remapping
Organized in clean grouped cards. Customize app shelves, select screensavers, and remap the physical buttons on your TV remote to launch your preferred apps.

![Settings](docs/assets/settings.png)

---

## Key Features

### 1. Control Center
* Bluetooth Audio: Quickly connect or disconnect Bluetooth headphones, earbuds, soundbars, or speakers.
* Volume & Picture Mode: Instant volume slider control and quick TV picture profile adjustments.
* Theme Toggle: Switch between Dark, Light, or Automatic mode (matches local sunrise and sunset).

### 2. Bottom Drawer
* HDMI Inputs: Displays all TV inputs (HDMI 1, HDMI 2, etc.) and highlights those receiving an active video signal.
* Quick Shortcuts: Direct shortcuts to Live TV and system tools.
* Weather Forecast: Accurate temperature, weather conditions, daily highs and lows, and upcoming hourly forecast without needing an API key.

### 3. Remote Button Remapping
* Remap physical remote buttons (such as Netflix, YouTube, Prime Video, or custom branded buttons) to open any installed app you prefer.
* Configured directly in Settings > Remote Buttons.

### 4. Safe 1-Click Restore
* You can switch back to the TV's original factory home screen at any time with a single click inside Settings > Home Screen.
* GB Launcher never damages or modifies system partitions.

---

## Supported Devices

Compatible with TVs and streaming boxes running Android TV or Google TV (Android 8.0+ / API 26+), including:
* TCL Google TVs
* Sony Bravia (Google TV & Android TV)
* Chromecast with Google TV (HD / 4K)
* Xiaomi Mi Box / TV Stick
* Philips Android TV
* Onn Google TV Box
* Nvidia Shield TV

---

## How to Install

### Option A: Install via AI Assistant (Easiest)

If you use an AI coding assistant (such as Antigravity, Claude Code, or Cursor), ensure your TV and computer are on the same Wi-Fi network and copy-paste this prompt:

```text
Install GB Launcher on my TV at <YOUR_TV_IP>. My TV has developer options and network debugging enabled.
```

The assistant will connect via ADB, install the APK, and guide you through selecting it.

### Option B: Manual Installation via ADB

1. Enable Developer Options on your TV:
   * Go to Settings > System > About.
   * Click on "Android TV OS build" (or "Build Number") 7 times until developer mode is unlocked.
   * Go to Settings > System > Developer Options and turn on "USB debugging" (or "Network debugging").
   * Check your TV IP address in Settings > Network & Internet.

2. Connect and Install from your computer:
   ```bash
   adb connect <YOUR_TV_IP>:5555
   adb install -r GBLauncher.apk
   ```

3. Open GB Launcher:
   * Launch GB Launcher from your apps list.
   * Select it as your default home screen (you can restore the original launcher at any time).

---

## Building from Source

GB Launcher compiles directly using Android SDK command-line tools without Gradle bloat:

```bash
# Set your Android SDK path
export ANDROID_HOME="/path/to/android/sdk"

# Compile, align, and sign the APK
./build.sh
```

The compiled package `GBLauncher.apk` will be output directly in the project root directory.

---

## Credits and Acknowledgments

GB Launcher is built upon and inspired by [Free My TV](https://github.com/simbasjungles/free-my-tv). Full credit and sincere appreciation go to the original creators for their pioneering work on Gradle-free compilation, smooth Android TV rendering, and system launcher interaction mechanics.

---

## License

Distributed under the [MIT](LICENSE) License.
