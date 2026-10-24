<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-white.png">
  <img src="docs/assets/logo-black.png" width="220" alt="GB Home TV">
</picture>

# GB Home TV

**Uma tela inicial mais rápida e sem anúncios para a sua Google TV ou Android TV.**<br>
Um clique troca. Outro clique traz a original de volta.

[English](README.md) · Português · [Español](README.es.md)

[Por quê](#por-quê) · [Instalar](#instalar) · [Depois de instalar](#depois-de-instalar) · [Como usar](#como-usar) · [O que tem](#o-que-tem) · [Capturas](#capturas) · [O que ele desliga](#o-que-ele-desliga) · [Memória](#memória-ram) · [Perguntas](#perguntas)

<img src="docs/assets/pt/hero.gif" width="880" alt="GB Home TV: a tela inicial, a gaveta, o clima e os jogos, e a Central de Controle">

</div>

## Por quê

| | |
|---|---|
| 🚫 **Sem anúncios** | Nada de linhas patrocinadas, banners ou vídeos que começam sozinhos. |
| ⚡ **Mais rápida** | Numa TV de verdade: 154 ms para mostrar a tela inicial, contra 217 ms da original. |
| 🪄 **Já vem pronta** | Usa o idioma, o tema, as unidades e o relógio da TV. Você muda depois, se quiser. |
| 🛟 **Reversível** | Não desinstala nada. *Launcher → Original* volta tudo, e se ela travar 3 vezes, volta sozinha. |
| 🔓 **Grátis e aberta** | Sem conta, sem assinatura. |

## Instalar

**Você precisa de:** uma TV com Google TV ou Android TV (Android 8+) e um computador na mesma rede Wi-Fi.

### 1. Prepare a TV (igual nos dois jeitos)

1. *Configurações → Sistema → Sobre*: aperte **7 vezes** em *Versão do Android TV OS* (ou *Número da versão*).
2. *Configurações → Sistema → Opções do desenvolvedor*: ligue **Depuração USB** (ou *Depuração de rede*).
3. *Configurações → Rede e Internet* → seu Wi-Fi: anote o **endereço IP** (como `192.168.1.50`). O seu é diferente.

### 2A. Com uma IA (mais fácil)

Abra um assistente de código ([Claude Code](https://claude.com/claude-code), Codex, Antigravity…) e cole, trocando pelo IP da sua TV:

> Instale o GB Home TV (github.com/gabrielbaumgratz/LauncherGB) na minha TV em 192.168.1.50.

Ela baixa, compila e instala. Na TV, quando perguntar *Permitir depuração USB?*, marque *Sempre permitir*.

### 2B. À mão

```bash
# Ferramentas: JDK 17+, Android SDK (build-tools 34, android-34) e adb
./build.sh                                 # gera o GBHomeTV.apk
adb connect 192.168.1.50:5555              # o IP da sua TV
adb install -r GBHomeTV.apk
```

- **TCL:** rode também `adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"` (sem isso a TCL bloqueia a troca).
- **macOS / Linux:** instale as ferramentas com `brew install --cask temurin android-commandlinetools android-platform-tools` ou pelo gerenciador de pacotes. **Windows:** use o Git Bash ou o WSL.
- Guarde o `freemytv.keystore`: as atualizações precisam da mesma assinatura.

### 3. Troque na TV

Abra **GB Home TV → Geral → Launcher → GB Home TV** e aceite a depuração USB mais uma vez. Aperte Início: ele está lá.

## Depois de instalar

Tudo aqui é opcional. O resto já funciona sem nada disso.

### Recomendações (chave do TMDB)

Mostra títulos em alta dos apps de streaming que você tem. Precisa de uma chave gratuita.

1. Crie uma conta em [themoviedb.org](https://www.themoviedb.org/signup) e confirme o e-mail.
2. Foto de perfil → *Configurações* → *API* → peça uma chave do tipo **Developer** (uso: "pessoal, tela inicial de TV").
3. Copie o **API Read Access Token**.
4. No `config.properties` (criado pelo primeiro `./build.sh`), cole depois de `tmdb_key=`, sem espaços nem aspas.
5. Compile e instale de novo (`./build.sh` e `adb install -r GBHomeTV.apk`), ou peça à IA.

A chave é sua: não compartilhe. O `config.properties` nunca vai para o GitHub.

<details>
<summary>Outras opções do <code>config.properties</code></summary>

| Chave | O que faz | Padrão |
|---|---|---|
| `tmdb_region` | Seu país (duas letras) | o da TV |
| `latitude`, `longitude`, `city` | Sua cidade, para clima e pôr do sol | pelo fuso horário |
| `language` | `auto`, `en`, `pt` ou `es` | `auto` |
| `temperature` | `C`, `F` ou `auto` | `auto` |

Compile de novo depois de mudar.
</details>

### AirPlay

O GB Home TV só adiciona o botão; quem recebe a tela é o app gratuito [android-airplay-server](https://github.com/jqssun/android-airplay-server) (jqssun, GPL-3.0).

1. Baixe o `.apk` em [Releases](https://github.com/jqssun/android-airplay-server/releases).
2. `adb install -r arquivo.apk` (ou peça à IA).
3. Na Central de Controle aparece o botão **AirPlay**. No iPhone, iPad ou Mac, escolha a TV no menu do AirPlay.

> Dica: esse app consome memória enquanto está aberto (medimos [~40 MB livres a menos](#memória-ram)). Em TVs com pouca RAM, feche-o quando não usar.

### Fotos do celular

*Configurações → Tela de Descanso → Fotos do seu celular.* Aponte a câmera para o QR code na TV (no computador, digite o endereço e o PIN) e escolha as fotos. Só na rede local, o PIN bloqueia depois de tentativas erradas, e o servidor só roda enquanto essa tela está aberta.

### Clima e jogos

Sem chave e sem instalação. *Configurações → Clima* e *Jogos*.

## Como usar

| | |
|---|---|
| **Início** | Seus apps na prateleira de vidro e abas de destaques. |
| **Para baixo** | A gaveta: entradas HDMI, atalhos, clima e jogos. |
| **Primeiro botão redondo, no topo** | A Central de Controle: Bluetooth, volume, tema, imagem, AirPlay. |
| **Engrenagem** | Configurações. *Launcher → Original* volta à tela da TV. |
| **TV parada** | A tela de descanso, com as suas fotos. |

### Botões do controle

Cada controle é diferente, então nada vem preso a um botão. Para abrir algo com um botão do seu controle:

*Configurações → Botões do Controle → Adicionar um botão*, aperte o botão e escolha o que ele abre: **um app**, **a Central de Controle** ou **as configurações da TV**. Funciona em qualquer marca. Em TCL, Sony, Vestel, KTC e Formovie, os botões de app de fábrica também são reconhecidos.

## O que tem

| | |
|---|---|
| 🎛️ **Central de Controle** | Você escolhe quais blocos aparecem. |
| ⚽ **Jogos** | Seus times, tenistas e campeonatos, com dados da ESPN (sem chave). |
| 🌙 **Tela de descanso** | Fotos em tela cheia, relógio, data e clima. Seis níveis de brilho. |
| 🎬 **Destaques** | Títulos em alta dos seus apps de streaming. |
| 🎧 **Bluetooth** | Escolha, renomeie e esqueça dispositivos. |
| 🎮 **Botões do controle** | Ensine qualquer botão a abrir um app ou uma tela. |
| 🌍 **Português, inglês e espanhol** | Segue a TV, a menos que você escolha. |

## Capturas

| | |
|---|---|
| **Início** | <img src="docs/assets/pt/home.png" width="440"> |
| **Gaveta** | <img src="docs/assets/pt/drawer.png" width="440"> |
| **Central de Controle** | <img src="docs/assets/pt/control-center.png" width="440"> |
| **Configurações** | <img src="docs/assets/pt/settings.png" width="440"> |
| **Jogos** | <img src="docs/assets/pt/settings-games.png" width="440"> |
| **Tela de descanso** | <img src="docs/assets/pt/settings-screensaver.png" width="440"> |
| **Desempenho** | <img src="docs/assets/pt/settings-performance.png" width="440"> |
| **Botões do controle** | <img src="docs/assets/pt/settings-remote.png" width="440"> |
| **Fotos do celular** | <img src="docs/assets/pt/photos.png" width="440"> |

Em movimento: [Central de Controle](docs/assets/pt/control-center.webp) · [Configurações](docs/assets/pt/settings.webp) · [Tela de descanso](docs/assets/pt/screensaver.mp4)

## O que ele desliga

**Por quê:** uma TV vem com apps do fabricante que rodam o tempo todo: canais com propaganda, demonstração de loja, medição de uso e serviços de anúncios. Eles gastam memória e deixam a TV lenta. Ao trocar para o GB Home TV, ele **desliga** (nunca desinstala) só os que ele conhece, para a TV ficar mais leve e sem anúncios.

| Tipo | Exemplos |
|---|---|
| Canais com anúncios | TCL Channel, Mi TV+, Coolita |
| Demonstração de loja | Modo loja, manuais de demonstração |
| Medição de uso e anúncios | Análise da Xiaomi, Anoki ACR, segmentação de anúncios do Android |
| Suporte e logs | Guias de reparo, coleta de logs |

**Nunca é mexido:** imagem, som, entradas HDMI, controle remoto, atualizações e serviços do Google. *Launcher → Original* liga tudo de volta. Lista completa em [`HomeMode.java`](app/src/io/freemytv/HomeMode.java).

## Memória (RAM)

TVs de 1 GB (muitas TCL e Xiaomi/Redmi) têm pouca memória sobrando. O que mais ajuda, em ordem:

1. **Feche o receptor de AirPlay quando não usar.** Medido numa TCL de 1 GB (3 leituras de memória livre):

   | | Memória livre |
   |---|---|
   | App de AirPlay aberto o tempo todo | ~184 MB |
   | App de AirPlay fechado | ~223 MB |

2. **Troque para o GB Home TV:** ele desliga os apps do fabricante que ficam rodando ao fundo (veja acima).
3. **Desligue o que não usa:** *Configurações → Desempenho → Desligar tudo isso* (recomendações, clima, jogos e zoom). Economiza uns 5 MB.

Sem maquiar os números: o GB Home TV abre mais rápido, mas desenha tudo sozinho e usa **mais** memória que a tela original (152 MB contra 89 MB numa TCL 32S5K). O ganho é velocidade e ausência de anúncios, e o desligamento dos apps do fabricante compensa em parte. *Configurações → Sobre* mede na sua TV.

## Perguntas

**Vai estragar minha TV?** Não. É um app comum; não mexe no sistema nem desinstala nada.

**Como volto atrás?** *Configurações → Geral → Launcher → Original.*

**Quais TVs?** Testado numa TCL 32S5K (Google TV, Android 14). Deve rodar em qualquer Android TV ou Google TV (Android 8+): Sony, Xiaomi, Philips, onn., Chromecast com Google TV, NVIDIA Shield. LG e Samsung não rodam Android: use um streamer Google TV no HDMI.

**Coleta algum dado?** Não. As únicas conexões são o clima (Open-Meteo), os destaques (TMDB, se você pôr uma chave) e os jogos (ESPN).

**Deu errado.** Abra uma [issue](https://github.com/gabrielbaumgratz/LauncherGB/issues) com o modelo da TV e o que viu.

## Compile você mesmo

Sem Gradle e sem bibliotecas: só o Android SDK.

```bash
export ANDROID_HOME="/caminho/do/android/sdk"
./build.sh            # GBHomeTV.apk
tools/check.sh        # compilação e catálogos de tradução
```

## Créditos

Criado por [Gabriel Baumgratz](https://github.com/gabrielbaumgratz), inspirado no [Free My TV](https://github.com/simbasjungles/free-my-tv).

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)** (MIT): o GB Home TV nasceu dele. A compilação sem Gradle, a tela desenhada em canvas, a troca de e para o launcher original e o vigia são deles.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, de jqssun (GPL-3.0): o receptor de AirPlay.
- **SF Pro**, da Apple: a fonte, via [sahibjotsaggu/San-Francisco-Pro-Fonts](https://github.com/sahibjotsaggu/San-Francisco-Pro-Fonts); valem os termos da Apple.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): os ícones.
- **[TMDB](https://www.themoviedb.org)**: os destaques. Usa a API do TMDB, sem endosso ou certificação dele.
- **[Open-Meteo](https://open-meteo.com)**: o clima. **ESPN**: dados públicos dos jogos.

## Licença

[MIT](LICENSE).
