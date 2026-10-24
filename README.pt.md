<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-white.png">
  <img src="docs/assets/logo-black.png" width="220" alt="GB Home TV">
</picture>

# GB Home TV

**Uma tela inicial mais rápida e sem anúncios para a sua Google TV ou Android TV.**<br>
Ela se configura sozinha pelas configurações da própria TV, e um clique traz a antiga de volta.

[English](README.md) · Português · [Español](README.es.md)

[Por quê](#por-que-o-gb-home-tv) · [Instalar](#instalar-em-5-minutos) · [Como usar](#como-usar) · [O que tem](#o-que-tem) · [Capturas](#capturas-de-tela) · [O que ele desliga](#o-que-ele-desliga) · [Extras](#extras-opcionais-passo-a-passo) · [Perguntas](#perguntas) · [Créditos](#créditos)

<img src="docs/assets/pt/hero.gif" width="880" alt="GB Home TV: a tela inicial, a gaveta com as entradas, o clima e os jogos, e a Central de Controle">

</div>

## Por que o GB Home TV?

- **Sem anúncios.** Nada de linhas patrocinadas, banners ou vídeos que começam sozinhos.
- **Abre mais rápido.** Numa TV de verdade ele mostrou a tela inicial em 154 ms; a original levou 217 ms ([os números](#medido-numa-tv-de-verdade)).
- **Nada para configurar.** Abre no idioma da sua TV, segue o claro e o escuro dela, usa as unidades e o relógio dela e adivinha a sua cidade pelo fuso horário. Você muda tudo depois, se quiser.
- **Do seu jeito.** Escolha seus apps, seus times, o que a Central de Controle mostra, até o que cada botão do controle abre, em qualquer TV.
- **Seguro.** Não mexe no sistema e não desinstala nada. Um clique volta para a tela inicial original, e se ele travar três vezes, volta sozinho.
- **Grátis e de código aberto.** Sem conta, sem assinatura.

## Instalar em 5 minutos

Você precisa da sua TV (Google TV ou Android TV, Android 8 ou mais novo) e de um computador no mesmo Wi-Fi. A TV e o computador ficam ligados.

### O jeito fácil e mais rápido: deixe uma IA fazer (recomendado)

Um assistente de código com IA ([Claude Code](https://claude.com/claude-code), Codex, Antigravity...) faz todos os passos técnicos por você. Você só aperta alguns botões na TV.

**1. Na TV: ligue o modo desenvolvedor** (é um menu, não tem risco)
- Abra *Configurações → Sistema → Sobre* e aperte *Versão do Android TV OS* (ou *Número da versão*) **7 vezes**.
- Volte para *Configurações → Sistema → Opções do desenvolvedor* e ligue a **Depuração USB** (algumas TVs chamam de *Depuração de rede*).
- Descubra o **endereço (o IP)** da TV: *Configurações → Rede e Internet*, toque no seu Wi-Fi e procure *Endereço IP*. São quatro números separados por pontos, como `192.168.1.50`. **O seu é diferente: anote e use no lugar de `192.168.1.50` onde esta página mostrar.**

**2. No computador: peça para a IA.** Abra o seu assistente e cole isto, **trocando os números pelo endereço da sua TV**:

> Instale o GB Home TV (github.com/gabrielbaumgratz/LauncherGB) na minha TV em 192.168.1.50.

Ela baixa o projeto, instala o que precisa, compila o app e coloca na sua TV. Vai perguntar antes de qualquer coisa importante, e na primeira vez pode levar alguns minutos.

**3. Na TV: diga sim.** Uma mensagem pergunta *Permitir depuração USB?*. Marque *Sempre permitir* e aperte OK. Depois abra o **GB Home TV** nos seus apps e escolha *Launcher → GB Home TV*. Pronto: aperte Início e ele está lá.

<details>
<summary><b>Prefere fazer você mesmo? Os passos à mão</b></summary>

1. **Na TV**, faça o passo 1 acima.
2. **Ferramentas:** um JDK 17+, as ferramentas de linha de comando do Android SDK (build-tools 34, plataforma android-34) e o adb.
   ```bash
   brew install --cask temurin android-commandlinetools android-platform-tools   # macOS
   sdkmanager "build-tools;34.0.0" "platforms;android-34"
   ```
   No Linux use o OpenJDK 17 e as ferramentas de linha de comando do Android; no Windows use o Git Bash ou o WSL.
3. **Compile:**
   ```bash
   ./build.sh
   ```
   A primeira execução cria o `config.properties`; editar é opcional (veja [os extras](#extras-opcionais-passo-a-passo)). O resultado é o `GBHomeTV.apk`.
4. **Instale:**
   ```bash
   adb connect 192.168.1.50:5555
   adb install -r GBHomeTV.apk
   ```
   Em TVs TCL, permita também que ele rode em segundo plano (a TCL bloqueia por padrão, e a troca nunca começa):
   ```bash
   adb shell "appops set io.freemytv AUTO_START allow || appops set io.freemytv APP_AUTO_START allow"
   ```
5. **Na TV**, abra *GB Home TV* → *Launcher* → *GB Home TV* e aceite *Permitir depuração USB?* mais uma vez.

As atualizações precisam da mesma assinatura: guarde o `freemytv.keystore`.
</details>

## Como usar

- **Início:** seus apps na prateleira de vidro e três abas de destaques (Explorar, Filmes, Séries).
- **Para baixo:** a gaveta: entradas HDMI, atalhos, o clima e o widget de jogos.
- **O botão de informação (i) do controle, ou o primeiro botão redondo no topo:** a Central de Controle: fone Bluetooth, volume, tema, modo de imagem, AirPlay.
- **A engrenagem no topo:** Configurações. Escolha *Launcher → Original* a qualquer hora para voltar à tela inicial da própria TV.
- **Parado:** a tela de descanso, com as suas fotos.

## O que tem

- **Uma Central de Controle** em que você escolhe quais blocos aparecem.
- **Um widget de jogos** ao lado do clima: seus times e campeonatos (futebol, basquete, NFL, beisebol, hóquei, tênis), cada um com o símbolo do esporte. Dados da ESPN, sem chave.
- **Uma tela de descanso legível do sofá:** fotos em tela cheia com acabamento fosco, um relógio fosco, a data e o clima. Fotos em ordem ou aleatórias, e ela pode escurecer para um quarto apagado.
- **Fotos do celular:** aponte a câmera para o QR code na TV (no computador, digite o endereço e o PIN) e envie fotos. Só na rede local.
- **Destaques dos seus apps:** títulos em alta dos apps de streaming que você tem (com uma chave do TMDB gratuita, [explicada abaixo](#extras-opcionais-passo-a-passo); o resto funciona sem ela).
- **Configurações no estilo do iPad,** com o visual da própria tela inicial.
- **Dispositivos Bluetooth:** escolha quais aparecem, renomeie, esqueça.
- **Seu nome:** "Bom dia, Ana" (ou sem nome).
- **Botões do controle:** em TCL, Sony, Vestel, KTC e Formovie os botões de app já são conhecidos; em **qualquer** TV você pode ensinar um botão (*Configurações → Botões do Controle → Adicionar um botão*): aperte e escolha o app que ele abre.
- **Português, inglês e espanhol,** seguindo a TV, a menos que você escolha.

## Capturas de tela

| | Português | English |
|---|---|---|
| Início | <img src="docs/assets/pt/home.png" width="420"> | <img src="docs/assets/en/home.png" width="420"> |
| Gaveta | <img src="docs/assets/pt/drawer.png" width="420"> | <img src="docs/assets/en/drawer.png" width="420"> |
| Central de Controle | <img src="docs/assets/pt/control-center.png" width="420"> | <img src="docs/assets/en/control-center.png" width="420"> |
| Configurações | <img src="docs/assets/pt/settings.png" width="420"> | <img src="docs/assets/en/settings.png" width="420"> |
| Jogos | <img src="docs/assets/pt/settings-games.png" width="420"> | <img src="docs/assets/en/settings-games.png" width="420"> |
| Tela de descanso | <img src="docs/assets/pt/settings-screensaver.png" width="420"> | <img src="docs/assets/en/settings-screensaver.png" width="420"> |
| Desempenho | <img src="docs/assets/pt/settings-performance.png" width="420"> | <img src="docs/assets/en/settings-performance.png" width="420"> |
| Botões do controle | <img src="docs/assets/pt/settings-remote.png" width="420"> | <img src="docs/assets/en/settings-remote.png" width="420"> |
| Fotos do celular | <img src="docs/assets/pt/photos.png" width="420"> | <img src="docs/assets/en/photos.png" width="420"> |

### Em movimento

| A Central de Controle | Configurações |
|---|---|
| <img src="docs/assets/pt/control-center.webp" width="420"> | <img src="docs/assets/pt/settings.webp" width="420"> |

A tela de descanso ([vídeo](docs/assets/pt/screensaver.mp4)):

<img src="docs/assets/pt/screensaver.webp" width="880" alt="A tela de descanso">

## O que ele desliga

A promessa é uma tela inicial sem anúncios, espionagem ou telemetria. Quando você troca para o GB Home TV, ele desliga (nunca desinstala) os apps de propaganda, demonstração e rastreamento do fabricante que ele conhece, e *Launcher → Original* liga tudo de volta. Na TCL, por exemplo: os canais de propaganda e anúncios (TCL Channel e sua TV ao vivo), as caixas de conta e mensagens, a demonstração de loja, os apps de guia de reparo e de coleta de logs, e a segmentação de anúncios do Android. Em outras marcas, o mesmo tipo de coisa: o Anoki ACR (que vê o que está na tela), a análise da Xiaomi, as propagandas do Mi TV+ e da Coolita, demonstrações de loja. Imagem, som, entradas, controle, atualizações e os serviços do Google são protegidos e nunca são mexidos. A lista completa está em [`HomeMode.java`](app/src/io/freemytv/HomeMode.java).

## Medido numa TV de verdade

*Configurações → Sobre* mede as duas telas iniciais na sua própria TV e mostra lado a lado. Estes são os números que apareceram numa TCL 32S5K (Google TV, Android 14), sem arredondar a favor do GB Home TV:

| | Tela inicial original | GB Home TV |
|---|---|---|
| Tempo do botão Início até mostrar a tela inicial | 217 ms | 154 ms |
| Memória livre na TV | 289 MB | 165 MB |
| Memória usada pela tela inicial | 89 MB | 152 MB |

O GB Home TV abre mais rápido e não tem anúncios, mas desenha tudo sozinho e guarda as imagens na memória, então usa **mais** memória que a tela inicial de fábrica. A memória própria dele é de uns 46 MB com tudo ligado e de uns 41 MB com as opções de memória desligadas (*Configurações → Desempenho → Desligar tudo isso*): uma economia de uns 5 MB, porque a maior parte do que o sistema conta para ele são bibliotecas compartilhadas. O objetivo aqui é sem anúncios, sem telemetria e uma tela inicial mais rápida, não o menor uso de RAM possível. Os seus números serão outros.

## Mais sobre…

### Configurações

| Categoria | O que tem |
|---|---|
| **Geral** | Launcher ou original · seu nome · idioma · tema · formato da hora · vidro · sons de navegação · as configurações da própria TV |
| **Início** | A prateleira · os botões redondos do topo · saudação · recomendações · entradas HDMI · Bluetooth: mostrar, renomear, esquecer |
| **Central de Controle** | Quais blocos ela mostra, inclusive extras que vêm desligados: tela de descanso, mudo, rede |
| **Clima** | Ligado ou desligado · °C, °F ou automático · sua cidade |
| **Jogos** | Ligado ou desligado · seus times · campeonatos por esporte |
| **Tela de Descanso** | Ver agora · suas fotos · ordem aleatória · intervalo · brilho (seis níveis, do mais claro ao bem escuro e fosco) · zoom lento · relógio · data e clima |
| **Desempenho** | Memória em uso agora · recomendações, clima, jogos, zoom e saudação ligados ou desligados · tudo desligado de uma vez |
| **Botões do Controle** | O app que cada botão abre, e os botões que você ensina |
| **Sobre** | Versão · números medidos · avisos · créditos |

### Extras opcionais, passo a passo

**Destaques (as três abas com títulos em alta): a chave gratuita do TMDB.** Sem ela o resto funciona; com ela a tela inicial mostra títulos em alta dos apps de streaming que você tem.

1. Entre em [themoviedb.org](https://www.themoviedb.org/signup), crie uma conta gratuita e confirme o e-mail.
2. Abra a sua foto de perfil (canto superior direito) → *Configurações* → *API* (no menu da esquerda).
3. Clique para pedir uma chave e escolha **Developer**. Aceite os termos e preencha o formulário (nome, e-mail e, no uso, escreva "uso pessoal, tela inicial de TV").
4. Quando for aprovada, a página mostra duas coisas: *API Key* e **API Read Access Token** (um texto bem longo). Copie o **API Read Access Token**.
5. Na pasta do projeto, abra o arquivo `config.properties` com qualquer editor de texto (o Bloco de Notas serve). Ele é criado pelo primeiro `./build.sh`. Ache a linha `tmdb_key=` e cole o token logo depois do `=`, sem espaços nem aspas:
   `tmdb_key=eyJhbGciOi...`
6. Compile e instale de novo: `./build.sh` e depois `adb install -r GBHomeTV.apk` (antes, `adb connect` com o endereço da sua TV, como na instalação). Ou peça ao seu assistente de IA: *"Compile o GB Home TV de novo e instale na minha TV em 192.168.1.50."*

Essa chave é sua: não compartilhe. O `config.properties` nunca vai para o GitHub.

**AirPlay (mandar a tela do iPhone, iPad ou Mac para a TV): o app receptor gratuito.** O GB Home TV só adiciona o botão; o receptor é um app gratuito à parte.

1. Baixe o `.apk` mais recente em [android-airplay-server → Releases](https://github.com/jqssun/android-airplay-server/releases).
2. Com o computador conectado à TV (`adb connect 192.168.1.50:5555`, com o seu endereço), rode: `adb install -r o-arquivo-que-voce-baixou.apk`. Ou peça ao seu assistente de IA: *"Instale este APK do receptor de AirPlay na minha TV em 192.168.1.50."*
3. Abra a **Central de Controle** (botão de informação do controle): aparece um botão **AirPlay**. No iPhone, iPad ou Mac, abra o menu do AirPlay e escolha a TV.

**Jogos: sem chave nenhuma.** *Configurações → Jogos*: ligue, adicione o seu time e marque os campeonatos. Os dados vêm das páginas públicas da ESPN.

**Clima: sem chave nenhuma.** Usa o Open-Meteo, e a sua cidade é adivinhada pelo fuso horário da TV (mude em *Configurações → Clima*).

### Fotos do celular (opcional)

*Configurações → Tela de Descanso → Fotos do seu celular.* A TV mostra um QR code e, abaixo, o endereço e um PIN. Aponte a câmera do celular (mesmo Wi-Fi) para o código e escolha as fotos; o PIN já vai dentro dele. No computador, que não tem câmera, abra o endereço e digite o PIN. As fotos são endireitadas e salvas de novo para a TV, sem as marcas de GPS. O servidor só roda enquanto essa tela está aberta, e o PIN bloqueia depois de algumas tentativas erradas.

### AirPlay

O GB Home TV não traz um receptor de AirPlay. Ele funciona com um gratuito, o **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, do [jqssun](https://github.com/jqssun) (GPL-3.0): espelhamento de tela, vídeo e áudio. Instale o APK dele pela [página de versões](https://github.com/jqssun/android-airplay-server/releases) (`adb install -r android-airplay-server.apk`). Aí aparece um botão **AirPlay** na Central de Controle que abre o receptor; no iPhone, iPad ou Mac, escolha a TV no menu do AirPlay.

### Opções do `config.properties`

Opcional: a TV já se adapta. Compile de novo depois de mudar.

| Chave | O que faz | Padrão |
|---|---|---|
| `tmdb_key` | Sua chave gratuita do TMDB, para os destaques | vazio |
| `tmdb_region` | Seu país (duas letras) | o da TV |
| `latitude`, `longitude`, `city` | Sua cidade, para o clima e o pôr do sol | adivinhada pelo fuso horário |
| `language` | `auto`, `en`, `pt` ou `es` | `auto` |
| `temperature` | `C`, `F` ou `auto` | `auto` |

## Perguntas

**Vai estragar minha TV?** Não. É um app comum e um launcher; não mexe no sistema. Em algumas TVs ele desliga os apps de propaganda do fabricante (nunca desinstala), e *Launcher → Original* liga de volta.

**Como volto atrás?** *Configurações → Geral → Launcher → Original.* Se o GB Home TV travar três vezes seguidas, ele faz isso sozinho.

**Quais TVs?** Testado numa TCL 32S5K (Google TV, Android 14). Deve rodar em qualquer Android TV ou Google TV a partir do Android 8, como Sony Bravia, Chromecast com Google TV, Xiaomi Mi Box, Philips, onn. e NVIDIA Shield. TVs LG e Samsung não rodam Android: use um streamer Google TV no HDMI.

**Ele coleta alguma coisa?** Não. As únicas chamadas de rede são o clima (Open-Meteo), os destaques (TMDB, se você pôr uma chave) e os jogos (ESPN).

**Algo deu errado.** Abra uma [issue](https://github.com/gabrielbaumgratz/LauncherGB/issues) com o modelo da sua TV e o que você viu.

## Compile você mesmo

Sem Gradle, sem bibliotecas: só o Android SDK.

```bash
export ANDROID_HOME="/caminho/do/android/sdk"
./build.sh            # GBHomeTV.apk
tools/check.sh        # a compilação e os catálogos de tradução
```

Os textos ficam em `app/assets/i18n/<idioma>.json`; `python3 tools/translate.py` lista o que falta num idioma.

## Créditos

Criado por [Gabriel Baumgratz](https://github.com/gabrielbaumgratz), inspirado no [Free My TV](https://github.com/simbasjungles/free-my-tv).

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)**, dos seus colaboradores (MIT): o GB Home TV nasceu dele. A compilação sem Gradle, a tela inicial desenhada em canvas, a troca de e para o launcher original e o vigia são deles.
- **[android-airplay-server](https://github.com/jqssun/android-airplay-server)**, do jqssun (GPL-3.0): o receptor de AirPlay que ele abre.
- **SF Pro**, da Apple: a fonte. Os arquivos vêm do repositório de código aberto [sahibjotsaggu/San-Francisco-Pro-Fonts](https://github.com/sahibjotsaggu/San-Francisco-Pro-Fonts); os termos de licença da própria Apple para a fonte continuam valendo.
- **[Material Symbols](https://fonts.google.com/icons)** (Apache 2.0): os ícones.
- **[TMDB](https://www.themoviedb.org)**: os destaques. Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.
- **[Open-Meteo](https://open-meteo.com)**: o clima, sem chave. Dados públicos da **ESPN**: os jogos.

## Licença

[MIT](LICENSE).
