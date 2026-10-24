<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-white.png">
  <img src="docs/assets/logo-black.png" width="220" alt="GB Home TV">
</picture>

# GB Home TV

**Una pantalla de inicio más rápida y sin anuncios para tu Google TV o Android TV.**<br>
Se configura sola con los ajustes de tu propia TV, y un clic devuelve la antigua.

[English](README.md) · [Português](README.pt.md) · Español

[Por qué](#por-qué-gb-home-tv) · [Instalar](#instalar-en-5-minutos) · [Cómo se usa](#cómo-se-usa) · [Qué incluye](#qué-incluye) · [Capturas](#capturas) · [Qué apaga](#qué-apaga) · [Extras](#extras-opcionales-paso-a-paso) · [Preguntas](#preguntas) · [Créditos](#créditos)

<img src="docs/assets/es/hero.gif" width="880" alt="GB Home TV: la pantalla de inicio, el cajón con las entradas, el clima y los partidos, y el Centro de control">

</div>

## ¿Por qué GB Home TV?

- **Sin anuncios.** Nada de filas patrocinadas, banners ni vídeos que arrancan solos.
- **Abre más rápido.** En una TV real mostró la pantalla de inicio en 154 ms; la original tardó 217 ms ([los números](#medido-en-una-tv-real)).
- **Nada que configurar.** Se abre en el idioma de tu TV, sigue su claro y oscuro, usa sus unidades y su reloj y adivina tu ciudad por la zona horaria. Después puedes cambiarlo todo.
- **A tu manera.** Elige tus apps, tus equipos, lo que muestra el Centro de control, incluso qué abre cada botón del mando, en cualquier TV.
- **Seguro.** No toca el sistema y no desinstala nada. Un clic vuelve a la pantalla de inicio original, y si falla tres veces seguidas, vuelve sola.
- **Gratis y de código abierto.** Sin cuenta, sin suscripción.

## Instalar en 5 minutos

Necesitas tu TV (Google TV o Android TV, Android 8 o más nuevo) y un ordenador en la misma Wi-Fi. La TV y el ordenador se quedan encendidos.

### La forma fácil y más rápida: que lo haga una IA (recomendado)

Un asistente de código con IA ([Claude Code](https://claude.com/claude-code), Codex, Antigravity...) hace todos los pasos técnicos por ti. Tú solo pulsas unos botones en la TV.

**1. En la TV: activa el modo desarrollador** (es un menú, sin riesgo)
- Abre *Ajustes → Sistema → Acerca de* y pulsa *Compilación de Android TV OS* (o *Número de compilación*) **7 veces**.
- Vuelve a *Ajustes → Sistema → Opciones de desarrollador* y activa la **Depuración USB** (algunas TV la llaman *Depuración de red*).
- Averigua la **dirección (la IP)** de la TV: *Ajustes → Red e Internet*, toca tu Wi-Fi y busca *Dirección IP*. Son cuatro números separados por puntos, como `192.168.1.50`. **La tuya es distinta: anótala y úsala donde esta página ponga `192.168.1.50`.**

**2. En el ordenador: pídeselo a la IA.** Abre tu asistente y pega esto, **cambiando los números por la dirección de tu TV**:

> Instala GB Home TV (github.com/gabrielbaumgratz/LauncherGB) en mi TV en 192.168.1.50.

Descarga el proyecto, instala lo que necesita, compila la app y la pone en tu TV. Preguntará antes de cualquier cosa importante, y la primera vez puede tardar unos minutos.

**3. En la TV: di que sí.** Un mensaje pregunta *¿Permitir la depuración USB?*. Marca *Permitir siempre* y pulsa OK. Luego abre **GB Home TV** en tus apps y elige *Launcher → GB Home TV*. Listo: pulsa Inicio y ahí está.

<details>
<summary><b>¿Prefieres hacerlo tú? Los pasos a mano</b></summary>

1. **En la TV**, haz el paso 1 de arriba.
2. **Herramientas:** un JDK 17+, las herramientas de línea de comandos del Android SDK (build-tools 34, plataforma android-34) y adb.
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools   # macOS
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   En Linux usa OpenJDK 17 y las herramientas de línea de comandos de Android; en Windows usa Git Bash o WSL.
3. **Compila:**
   ```bash
   ./build.sh
   ```
   La primera vez crea `config.properties`; editarlo es opcional (mira [los extras](#extras-opcionales-paso-a-paso)). El resultado es `GBHomeTV.apk`.
4. **Instala:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r GBHomeTV.apk
   ```
   En TV TCL, permite también que se ejecute en segundo plano (TCL lo bloquea por defecto y el cambio nunca empieza):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
5. **En la TV**, abre *GB Home TV* → *Launcher* → *GB Home TV* y acepta *¿Permitir la depuración USB?* una vez más.

Las actualizaciones deben firmarse con la misma clave: guarda `freemytv.keystore`.
</details>

## Cómo se usa

- **Inicio:** tus apps en el estante de cristal y tres pestañas de recomendaciones (Explorar, Películas, Series).
- **Abajo:** el cajón: entradas HDMI, accesos directos, el clima y el widget de partidos.
- **El botón de información (i) del mando, o el primer botón redondo de arriba:** el Centro de control: auriculares Bluetooth, volumen, tema, modo de imagen, AirPlay.
- **El engranaje de arriba:** Ajustes. Elige *Launcher → Original* cuando quieras para volver a la pantalla de inicio de tu TV.
- **En reposo:** el salvapantallas, con tus fotos.

## Qué incluye

- **Un Centro de control** donde eliges qué bloques aparecen.
- **Un widget de partidos** junto al clima: tus equipos y campeonatos (fútbol, baloncesto, NFL, béisbol, hockey, tenis), cada uno con el símbolo de su deporte. Datos de ESPN, sin clave.
- **Un salvapantallas que se lee desde el sofá:** fotos a pantalla completa con acabado mate, un reloj esmerilado, la fecha y el clima. Fotos en orden o al azar, y puede atenuarse para una habitación a oscuras.
- **Fotos desde el móvil:** escanea el código QR de la TV (en un ordenador, escribe la dirección y el PIN) y envía fotos. Solo en la red local.
- **Recomendaciones de tus apps:** títulos en tendencia de las apps de streaming que tienes (con una clave de TMDB gratuita, [explicada abajo](#extras-opcionales-paso-a-paso); lo demás funciona sin ella).
- **Ajustes al estilo del iPad,** con el aspecto de la propia pantalla de inicio.
- **Dispositivos Bluetooth:** elige cuáles aparecen, cámbiales el nombre u olvídalos.
- **Tu nombre:** "Buenos días, Ana" (o sin nombre).
- **Botones del mando:** en TCL, Sony, Vestel, KTC y Formovie los botones de apps ya se conocen; en **cualquier** TV puedes enseñarle un botón (*Ajustes → Botones del mando → Añadir un botón*): púlsalo y elige la app que abre.
- **Español, inglés y portugués,** siguiendo a la TV salvo que elijas.

## Capturas

| | |
|---|---|
| Inicio | <img src="docs/assets/es/home.png" width="520"> |
| Cajón | <img src="docs/assets/es/drawer.png" width="520"> |
| Centro de control | <img src="docs/assets/es/control-center.png" width="520"> |
| Ajustes | <img src="docs/assets/es/settings.png" width="520"> |
| Partidos | <img src="docs/assets/es/settings-games.png" width="520"> |
| Salvapantallas | <img src="docs/assets/es/settings-screensaver.png" width="520"> |
| Rendimiento | <img src="docs/assets/es/settings-performance.png" width="520"> |
| Botones del mando | <img src="docs/assets/es/settings-remote.png" width="520"> |
| Fotos desde el móvil | <img src="docs/assets/es/photos.png" width="520"> |

### En movimiento

| El Centro de control | Ajustes |
|---|---|
| <img src="docs/assets/es/control-center.webp" width="420"> | <img src="docs/assets/es/settings.webp" width="420"> |

El salvapantallas ([vídeo](docs/assets/es/screensaver.mp4)):

<img src="docs/assets/es/screensaver.webp" width="880" alt="El salvapantallas">

## Qué apaga

La promesa es una pantalla de inicio sin anuncios, espionaje ni telemetría. Cuando cambias a GB Home TV, apaga (nunca desinstala) las apps de publicidad, demostración y rastreo del fabricante que conoce, y *Launcher → Original* lo vuelve a encender todo. En TCL, por ejemplo: los canales de publicidad y anuncios (TCL Channel y su TV en vivo), las cajas de cuenta y mensajes, la demostración de tienda, las apps de guía de reparación y de recogida de registros, y la segmentación de anuncios de Android. En otras marcas, lo mismo: Anoki ACR (que ve lo que hay en pantalla), la analítica de Xiaomi, las promociones de Mi TV+ y Coolita, demostraciones de tienda. Imagen, sonido, entradas, mando, actualizaciones y los servicios de Google están protegidos y no se tocan. La lista completa está en [`HomeMode.java`](app/src/io/freemytv/HomeMode.java).

## Medido en una TV real

*Ajustes → Acerca de* mide las dos pantallas de inicio en tu propia TV y las muestra una al lado de la otra. Estos son los números que mostró en una TCL 32S5K (Google TV, Android 14), sin redondear a favor de GB Home TV:

| | Pantalla de inicio original | GB Home TV |
|---|---|---|
| Tiempo del botón Inicio hasta mostrar la pantalla de inicio | 217 ms | 154 ms |
| Memoria libre en la TV | 289 MB | 165 MB |
| Memoria que usa la pantalla de inicio | 89 MB | 152 MB |

GB Home TV abre más rápido y no tiene anuncios, pero lo dibuja todo él mismo y guarda las imágenes en memoria, así que usa **más** memoria que la pantalla de inicio de fábrica. Su memoria propia es de unos 46 MB con todo activado y de unos 41 MB con las opciones de memoria desactivadas (*Ajustes → Rendimiento → Desactivar todo esto*): un ahorro de unos 5 MB, porque la mayor parte de lo que el sistema le cuenta son bibliotecas compartidas. El objetivo aquí es sin anuncios, sin telemetría y una pantalla de inicio más rápida, no el menor uso de RAM posible. Tus números serán otros.

## Más sobre…

### Extras opcionales, paso a paso

**Recomendaciones (las tres pestañas con títulos en tendencia): la clave gratuita de TMDB.** Sin ella lo demás funciona; con ella la pantalla de inicio muestra títulos en tendencia de las apps de streaming que tienes.

1. Entra en [themoviedb.org](https://www.themoviedb.org/signup), crea una cuenta gratuita y confirma el correo.
2. Abre tu foto de perfil (arriba a la derecha) → *Ajustes* → *API* (en el menú de la izquierda).
3. Pide una clave y elige **Developer**. Acepta los términos y rellena el formulario (nombre, correo y, en el uso, escribe "uso personal, pantalla de inicio de TV").
4. Cuando la aprueben, la página muestra dos cosas: *API Key* y **API Read Access Token** (un texto muy largo). Copia el **API Read Access Token**.
5. En la carpeta del proyecto, abre el archivo `config.properties` con cualquier editor de texto (el Bloc de notas sirve). Lo crea el primer `./build.sh`. Busca la línea `tmdb_key=` y pega el token justo después del `=`, sin espacios ni comillas:
   `tmdb_key=eyJhbGciOi...`
6. Compila e instala otra vez: `./build.sh` y luego `adb install -r GBHomeTV.apk` (antes, `adb connect` con la dirección de tu TV, como en la instalación). O pídeselo a tu asistente de IA: *"Compila GB Home TV otra vez e instálalo en mi TV en 192.168.1.50."*

Esa clave es tuya: no la compartas. `config.properties` nunca va a GitHub.

**AirPlay (enviar la pantalla del iPhone, iPad o Mac a la TV): la app receptora gratuita.** GB Home TV solo añade el botón; el receptor es una app gratuita aparte.

1. Descarga el `.apk` más reciente de [android-airplay-server → Releases](https://github.com/jqssun/android-airplay-server/releases).
2. Con el ordenador conectado a la TV (`adb connect 192.168.1.50:5555`, con tu dirección), ejecuta: `adb install -r el-archivo-que-descargaste.apk`. O pídeselo a tu asistente de IA: *"Instala este APK del receptor de AirPlay en mi TV en 192.168.1.50."*
3. Abre el **Centro de control** (botón de información del mando): aparece un botón **AirPlay**. En el iPhone, iPad o Mac, abre el menú de AirPlay y elige la TV.

**Partidos: sin ninguna clave.** *Ajustes → Partidos*: actívalo, añade tu equipo y marca los campeonatos. Los datos vienen de las páginas públicas de ESPN.

**Clima: sin ninguna clave.** Usa Open-Meteo, y tu ciudad se adivina por la zona horaria de la TV (cámbiala en *Ajustes → Clima*).

### Fotos desde el móvil (opcional)

*Ajustes → Salvapantallas → Fotos desde tu móvil.* La TV muestra un código QR y, debajo, la dirección y un PIN. Escanea el código con el móvil (misma Wi-Fi) y elige las fotos; el PIN ya va dentro. En un ordenador, que no tiene cámara, abre la dirección y escribe el PIN. Las fotos se enderezan y se guardan de nuevo para la TV, sin sus datos de GPS. El servidor solo funciona mientras esa pantalla está abierta, y el PIN se bloquea tras unos intentos fallidos.

### AirPlay

GB Home TV no incluye un receptor de AirPlay. Funciona con uno gratuito, **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, de [jqssun](https://github.com/jqssun) (GPL-3.0): duplicación de pantalla, vídeo y audio. Instala su APK desde la [página de versiones](https://github.com/jqssun/android-airplay-server/releases) (`adb install -r android-airplay-server.apk`). Entonces aparece un botón **AirPlay** en el Centro de control que abre el receptor; en un iPhone, iPad o Mac, elige la TV en el menú de AirPlay.

## Preguntas

**¿Va a estropear mi TV?** No. Es una app normal y un launcher; no toca el sistema. En algunas TV apaga las apps de publicidad del fabricante (nunca las desinstala), y *Launcher → Original* las vuelve a encender.

**¿Cómo vuelvo atrás?** *Ajustes → General → Launcher → Original.* Si GB Home TV falla tres veces seguidas, lo hace solo.

**¿Qué TV?** Probado en una TCL 32S5K (Google TV, Android 14). Debería funcionar en cualquier Android TV o Google TV desde Android 8, como Sony Bravia, Chromecast con Google TV, Xiaomi Mi Box, Philips, onn. y NVIDIA Shield. Las TV LG y Samsung no usan Android: usa un streamer Google TV por HDMI.

**¿Recoge algún dato?** No. Las únicas llamadas de red son el clima (Open-Meteo), las recomendaciones (TMDB, si pones una clave) y los partidos (ESPN).

**Algo no va bien.** Abre una [issue](https://github.com/gabrielbaumgratz/LauncherGB/issues) con el modelo de tu TV y lo que viste.

## Compílalo tú

Sin Gradle, sin librerías: solo el Android SDK.

```bash
export ANDROID_HOME="/ruta/al/android/sdk"
./build.sh            # GBHomeTV.apk
tools/check.sh        # la compilación y los catálogos de traducción
```

Los textos están en `app/assets/i18n/<idioma>.json`; `python3 tools/translate.py` lista lo que le falta a un idioma.

## Créditos

Creado por [Gabriel Baumgratz](https://github.com/gabrielbaumgratz), inspirado en [Free My TV](https://github.com/simbasjungles/free-my-tv).

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)**, de sus colaboradores (MIT): GB Home TV nació de él. La compilación sin Gradle, la pantalla de inicio dibujada en canvas, el cambio a y desde el launcher original y el vigilante son suyos.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, de jqssun (GPL-3.0): el receptor de AirPlay que abre.
- **SF Pro**, de Apple: la tipografía. Los archivos vienen del repositorio de código abierto [sahibjotsaggu/San-Francisco-Pro-Fonts](https://github.com/sahibjotsaggu/San-Francisco-Pro-Fonts); los términos de licencia de Apple para la fuente siguen vigentes.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): los iconos.
- **[TMDB](https://www.themoviedb.org)**: las recomendaciones. Este producto usa la API de TMDB, pero TMDB no lo respalda ni lo certifica.
- **[Open-Meteo](https://open-meteo.com)**: el clima, sin clave. Datos públicos de **ESPN**: los partidos.

## Licencia

[MIT](LICENSE).
