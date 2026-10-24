<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-white.png">
  <img src="docs/assets/logo-black.png" width="220" alt="GB Home TV">
</picture>

# GB Home TV

**Una pantalla de inicio más rápida y sin anuncios para tu Google TV o Android TV.**<br>
Un clic cambia. Otro clic trae la original de vuelta.

[English](README.md) · [Português](README.pt.md) · Español

[Por qué](#por-qué) · [Instalar](#instalar) · [Después de instalar](#después-de-instalar) · [Cómo se usa](#cómo-se-usa) · [Qué incluye](#qué-incluye) · [Capturas](#capturas) · [Qué apaga](#qué-apaga) · [Memoria](#memoria-ram) · [Preguntas](#preguntas)

<img src="docs/assets/es/hero.gif" width="880" alt="GB Home TV: la pantalla de inicio, el cajón, el clima y los partidos, y el Centro de control">

</div>

## Por qué

| | |
|---|---|
| 🚫 **Sin anuncios** | Nada de filas patrocinadas, banners ni videos que arrancan solos. |
| ⚡ **Más rápida** | En una TV real: 154 ms para mostrar el inicio, contra 217 ms de la original. |
| 🪄 **Lista al instalar** | Usa el idioma, el tema, las unidades y el reloj de la TV. Lo cambias después si quieres. |
| 🛟 **Reversible** | No desinstala nada. *Launcher → Original* lo devuelve todo, y si se cierra 3 veces, vuelve sola. |
| 🔓 **Gratis y abierta** | Sin cuenta, sin suscripción. |

## Instalar

**Necesitas:** una TV con Google TV o Android TV (Android 8+) y un computador en la misma red Wi-Fi.

### 1. Prepara la TV (igual en los dos modos)

1. *Ajustes → Sistema → Acerca de*: pulsa **7 veces** en *Versión de Android TV OS* (o *Número de compilación*).
2. *Ajustes → Sistema → Opciones de desarrollador*: activa **Depuración USB** (o *Depuración de red*).
3. *Ajustes → Red e Internet* → tu Wi-Fi: anota la **dirección IP** (como `192.168.1.50`). La tuya será distinta.

### 2A. Con una IA (lo más fácil)

Abre un asistente de código ([Claude Code](https://claude.com/claude-code), Codex, Antigravity…) y pega esto, con la IP de tu TV:

> Instala GB Home TV (github.com/gabrielbaumgratz/LauncherGB) en mi TV en 192.168.1.50.

Descarga, compila e instala. Cuando la TV pregunte *¿Permitir depuración USB?*, marca *Permitir siempre*.

### 2B. A mano

```bash
# Herramientas: JDK 17+, Android SDK (build-tools 34, android-34) y adb
./build.sh                                 # genera GBHomeTV.apk
adb connect 192.168.1.50:5555              # la IP de tu TV
adb install -r GBHomeTV.apk
```

- **TCL:** ejecuta también `adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"` (sin esto TCL bloquea el cambio).
- **macOS / Linux:** instala las herramientas con `brew install --cask temurin android-commandlinetools android-platform-tools` o con tu gestor de paquetes. **Windows:** usa Git Bash o WSL.
- Guarda `freemytv.keystore`: las actualizaciones necesitan la misma firma.

### 3. Cambia en la TV

Abre **GB Home TV → General → Launcher → GB Home TV** y acepta la depuración USB una vez más. Pulsa Inicio: ahí está.

## Después de instalar

Todo es opcional. Lo demás ya funciona sin esto.

### Recomendaciones (clave de TMDB)

Muestra títulos en tendencia de las apps de streaming que tienes. Necesita una clave gratuita.

1. Crea una cuenta en [themoviedb.org](https://www.themoviedb.org/signup) y confirma el correo.
2. Foto de perfil → *Ajustes* → *API* → pide una clave tipo **Developer** (uso: "personal, pantalla de inicio de TV").
3. Copia el **API Read Access Token**.
4. En `config.properties` (lo crea el primer `./build.sh`), pégalo después de `tmdb_key=`, sin espacios ni comillas.
5. Compila e instala de nuevo (`./build.sh` y `adb install -r GBHomeTV.apk`), o pídeselo a la IA.

La clave es tuya: no la compartas. `config.properties` nunca va a GitHub.

<details>
<summary>Otras opciones de <code>config.properties</code></summary>

| Clave | Qué hace | Por defecto |
|---|---|---|
| `tmdb_region` | Tu país (dos letras) | el de la TV |
| `latitude`, `longitude`, `city` | Tu ciudad, para clima y atardecer | por la zona horaria |
| `language` | `auto`, `en`, `pt` o `es` | `auto` |
| `temperature` | `C`, `F` o `auto` | `auto` |

Compila de nuevo después de cambiar.
</details>

### AirPlay

GB Home TV solo añade el botón; quien recibe la pantalla es la app gratuita [android-airplay-server](https://github.com/jqssun/android-airplay-server) (jqssun, GPL-3.0).

1. Descarga el `.apk` de [Releases](https://github.com/jqssun/android-airplay-server/releases).
2. `adb install -r archivo.apk` (o pídeselo a la IA).
3. El Centro de control ahora tiene un botón **AirPlay**. En el iPhone, iPad o Mac, elige la TV en el menú de AirPlay.

> Consejo: esa app gasta memoria mientras está abierta (medimos [~40 MB libres menos](#memoria-ram)). En TVs con poca RAM, ciérrala cuando no la uses.

### Fotos desde el celular

*Ajustes → Protector de pantalla → Fotos de tu celular.* Apunta la cámara al código QR de la TV (en un computador, escribe la dirección y el PIN) y elige las fotos. Solo en la red local, el PIN se bloquea tras intentos fallidos, y el servidor funciona solo mientras esa pantalla está abierta.

### Clima y partidos

Sin clave y sin instalar nada. *Ajustes → Clima* y *Partidos*.

## Cómo se usa

| | |
|---|---|
| **Inicio** | Tus apps en el estante de cristal y pestañas de destacados. |
| **Hacia abajo** | El cajón: entradas HDMI, atajos, clima y partidos. |
| **Primer botón redondo, arriba** | El Centro de control: Bluetooth, volumen, tema, imagen, AirPlay. |
| **Engranaje** | Ajustes. *Launcher → Original* vuelve a la pantalla de la TV. |
| **TV en reposo** | El protector de pantalla, con tus fotos. |

### Botones del control

Cada control es distinto, así que nada viene atado a un botón. Para abrir algo con un botón de tu control:

*Ajustes → Botones del control → Añadir un botón*, pulsa el botón y elige qué abre: **una app**, **el Centro de control** o **los ajustes de la TV**. Funciona en cualquier marca. En TCL, Sony, Vestel, KTC y Formovie también se reconocen los botones de app de fábrica.

## Qué incluye

| | |
|---|---|
| 🎛️ **Centro de control** | Tú eliges qué bloques se muestran. |
| ⚽ **Partidos** | Tus equipos, tenistas y torneos, con datos de ESPN (sin clave). |
| 🌙 **Protector de pantalla** | Fotos a pantalla completa, reloj, fecha y clima. Seis niveles de brillo. |
| 🎬 **Destacados** | Títulos en tendencia de tus apps de streaming. |
| 🎧 **Bluetooth** | Elige, renombra y olvida dispositivos. |
| 🎮 **Botones del control** | Enseña a cualquier botón a abrir una app o una pantalla. |
| 🌍 **Español, inglés y portugués** | Sigue a la TV, salvo que elijas. |

## Capturas

| | |
|---|---|
| **Inicio** | <img src="docs/assets/es/home.png" width="440"> |
| **Cajón** | <img src="docs/assets/es/drawer.png" width="440"> |
| **Centro de control** | <img src="docs/assets/es/control-center.png" width="440"> |
| **Ajustes** | <img src="docs/assets/es/settings.png" width="440"> |
| **Partidos** | <img src="docs/assets/es/settings-games.png" width="440"> |
| **Protector de pantalla** | <img src="docs/assets/es/settings-screensaver.png" width="440"> |
| **Rendimiento** | <img src="docs/assets/es/settings-performance.png" width="440"> |
| **Botones del control** | <img src="docs/assets/es/settings-remote.png" width="440"> |
| **Fotos desde el celular** | <img src="docs/assets/es/photos.png" width="440"> |

En movimiento: [Centro de control](docs/assets/es/control-center.webp) · [Ajustes](docs/assets/es/settings.webp) · [Protector de pantalla](docs/assets/es/screensaver.mp4)

## Qué apaga

**Por qué:** una TV viene con apps del fabricante que funcionan todo el tiempo: canales con publicidad, demostración de tienda, medición de uso y servicios de anuncios. Gastan memoria y vuelven lenta la TV. Al cambiar a GB Home TV, **apaga** (nunca desinstala) solo las que conoce, para que la TV quede más ligera y sin anuncios.

| Tipo | Ejemplos |
|---|---|
| Canales con anuncios | TCL Channel, Mi TV+, Coolita |
| Demostración de tienda | Modo tienda, manuales de demostración |
| Medición de uso y anuncios | Analítica de Xiaomi, Anoki ACR, segmentación de anuncios de Android |
| Soporte y registros | Guías de reparación, recolección de registros |

**Nunca se toca:** imagen, sonido, entradas HDMI, control remoto, actualizaciones y servicios de Google. *Launcher → Original* lo enciende todo de nuevo. Lista completa en [`HomeMode.java`](app/src/io/freemytv/HomeMode.java).

## Memoria (RAM)

Las TVs de 1 GB (muchas TCL y Xiaomi/Redmi) tienen poca memoria libre. Lo que más ayuda, en orden:

1. **Cierra el receptor de AirPlay cuando no lo uses.** Medido en una TCL de 1 GB (3 lecturas de memoria libre):

   | | Memoria libre |
   |---|---|
   | App de AirPlay abierta todo el tiempo | ~184 MB |
   | App de AirPlay cerrada | ~223 MB |

2. **Cambia a GB Home TV:** apaga las apps del fabricante que siguen corriendo en segundo plano (ver arriba).
3. **Apaga lo que no uses:** *Ajustes → Rendimiento → Apagar todo esto* (recomendaciones, clima, partidos y zoom). Ahorra unos 5 MB.

Sin maquillar los números: GB Home TV abre más rápido, pero dibuja todo por su cuenta y usa **más** memoria que la pantalla original (152 MB contra 89 MB en una TCL 32S5K). La ganancia es velocidad y cero anuncios, y apagar las apps del fabricante compensa una parte. *Ajustes → Acerca de* lo mide en tu TV.

## Preguntas

**¿Va a dañar mi TV?** No. Es una app común; no toca el sistema ni desinstala nada.

**¿Cómo vuelvo atrás?** *Ajustes → General → Launcher → Original.*

**¿Qué TVs?** Probado en una TCL 32S5K (Google TV, Android 14). Debería funcionar en cualquier Android TV o Google TV (Android 8+): Sony, Xiaomi, Philips, onn., Chromecast con Google TV, NVIDIA Shield. LG y Samsung no usan Android: usa un streamer Google TV por HDMI.

**¿Recopila datos?** No. Las únicas conexiones son el clima (Open-Meteo), los destacados (TMDB, si pones una clave) y los partidos (ESPN).

**Algo salió mal.** Abre un [issue](https://github.com/gabrielbaumgratz/LauncherGB/issues) con el modelo de tu TV y lo que viste.

## Compílalo tú mismo

Sin Gradle y sin bibliotecas: solo el Android SDK.

```bash
export ANDROID_HOME="/ruta/al/android/sdk"
./build.sh            # GBHomeTV.apk
tools/check.sh        # compilación y catálogos de traducción
```

## Créditos

Creado por [Gabriel Baumgratz](https://github.com/gabrielbaumgratz), inspirado en [Free My TV](https://github.com/simbasjungles/free-my-tv).

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)** (MIT): GB Home TV nació de él. La compilación sin Gradle, la pantalla dibujada en canvas, el cambio desde y hacia el launcher original y el vigilante son suyos.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, de jqssun (GPL-3.0): el receptor de AirPlay.
- **SF Pro**, de Apple: la fuente, vía [sahibjotsaggu/San-Francisco-Pro-Fonts](https://github.com/sahibjotsaggu/San-Francisco-Pro-Fonts); rigen los términos de Apple.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): los iconos.
- **[TMDB](https://www.themoviedb.org)**: los destacados. Usa la API de TMDB, sin respaldo ni certificación suya.
- **[Open-Meteo](https://open-meteo.com)**: el clima. **ESPN**: datos públicos de los partidos.

## Licencia

[MIT](LICENSE).
