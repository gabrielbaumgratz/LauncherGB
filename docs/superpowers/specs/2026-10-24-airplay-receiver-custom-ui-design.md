# Especificação de Design: Customização de UI e Integração do AirPlay Server para GB Launcher

## 1. Visão Geral e Objetivos

Este documento especifica a criação, customização de interface e integração de um fork do receptor AirPlay de código aberto [android-airplay-server](https://github.com/jqssun/android-airplay-server) (autor original: `jqssun`, licença GPL-3.0) para uso complementar ao [GB Launcher](file:///c:/Projetos/Pessoais/freemytv).

O objetivo principal é dotar o receptor AirPlay de uma experiência visual e ergonômica perfeitamente alinhada com o design system do GB Launcher (dark glassmorphism, tipografia limpa, cantos arredondados e foco D-pad de alto contraste para controle remoto de TV), mantendo conformidade integral com a licença GPL-3.0 e interoperabilidade nativa com o botão de AirPlay da Central de Controle do launcher.

---

## 2. Arquitetura e Estrutura de Diretórios

### 2.1 Separação Modular
* **GB Launcher (`c:\Projetos\Pessoais\freemytv`)**: Permanece como o launcher leve, com zero dependências externas, compilado via `build.sh` (Java 8 / Android SDK).
* **AirPlay Server Fork (`c:\Projetos\Pessoais\android-airplay-server`)**: Repositório complementar em Kotlin / Jetpack Compose e C++ (UxPlay via JNI / NDK 27), compilado via Gradle.
* **Compatibilidade**: O fork mantém o `applicationId` `io.github.jqssun.airplay`, permitindo que o `ControlCenterActivity.java` o identifique e execute via Intent sem necessidade de alterações estruturais no código-fonte do launcher.

### 2.2 Requisitos de Ambiente Local
* **JDK**: 17+ (instalado em `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\bin`).
* **Android SDK**: `C:\Users\gabri\AppData\Local\Android\Sdk`.
* **Android NDK**: versão 27 (`27.0.12077973`).
* **Git**: instalado com suporte a submódulos.

---

## 3. Design System (GB Launcher Visual Match)

A camada de interface do receptor (escrita em Jetpack Compose) será customizada em `app/src/main/kotlin/io/github/jqssun/airplay/ui/`:

### 3.1 Paleta de Cores e Materiais
* **Fundo / Background**: Preto e cinza escuro translúcido (`#0D0D0D` a `#1A1A1A`) com efeito de vidro fosco (*glassmorphism*).
* **Superfícies de Cartão**: `Color(0xCC202020)` com borda sutil `Color(0x22FFFFFF)`.
* **Foco D-Pad Ativo**: Pílula sólida branca (`#FFFFFF`) com conteúdo em preto (`#000000`), replicando o foco padrão do GB Launcher.
* **Texto Primário**: `Color(0xFFFFFFFF)`.
* **Texto Secundário / Notas**: `Color(0x99FFFFFF)`.

### 3.2 Tipografia e Geometria
* **Tipografia**: Otimizada para leitura a distância em telas de TV (tamanhos de fonte ampliados, pesos semibold/bold em cabeçalhos).
* **Cantos Arredondados**:
  * Cartões de status e grupos de opções: `16.dp`.
  * Botões de ação, chips e indicadores de foco: `24.dp` (estilo pílula).

---

## 4. Estrutura de Telas e Componentes

### 4.1 Tela Principal (Overview)
* **Cartão de Status Central**:
  * Indicador de estado do servidor (Pronto / Conectado / Parado).
  * Nome mDNS anunciado na rede (ex: *"GB Launcher TV"*).
  * Código PIN de autenticação em destaque quando habilitado.
* **Card de Rede**:
  * Endereço IPv4 local da TV e porta do serviço AirPlay.
  * Status da conexão Wi-Fi/Ethernet.
* **Barra de Ações Rápidas**:
  * Botão de Iniciar/Parar Servidor.
  * Botão de Alternar Modo Tela Cheia / Espelhamento.
  * Botão de Acesso aos Ajustes.

### 4.2 Overlay do Player de Vídeo e Áudio
* **Controles OSD (On-Screen Display)**:
  * Barra inferior translúcida contendo Play/Pause, tempo decorrido/total e controle de volume.
  * Fade-out automático após 3 segundos de inatividade do controle remoto.
* **Navegação D-Pad**:
  * Teclas Esquerda/Direita para avanço/retrocesso de reprodução ou ajuste de volume.
  * Tecla OK/Enter para pausar ou continuar.
  * Tecla Back para minimizar ou retornar à tela principal.

### 4.3 Tela de Ajustes (Settings)
* **Agrupamento Otimizado para TV**:
  * Substituição dos seletores genéricos por blocos temáticos no estilo do GB Launcher:
    * **Vídeo**: Resolução (1080p, 720p, 4K), taxa de quadros (30/60 fps), codec H.264/H.265.
    * **Áudio**: Latência de buffer, formato ALAC/AAC, sincronização de volume.
    * **Sistema**: Inicialização com o sistema (Boot), execução em segundo plano (Background Service).
  * Cada item apresenta foco nítido e resposta tátil imediata aos comandos do D-pad.

---

## 5. Estratégia de Licenciamento e Créditos (GPL-3.0)

* O repositório mantém integralmente o arquivo `LICENSE` original com o texto da licença GNU GPL-3.0.
* Mantêm-se os créditos explícitos a `jqssun` e aos mantenedores de dependências nativas (`UxPlay`, `libplist`, `FFmpeg`, `OpenSSL`).
* Um arquivo `NOTICE` ou seção dedicada no `README.md` documentará as modificações de interface feitas para o ecossistema GB Launcher.

---

## 6. Verificação e Testes

1. **Clonagem e Submódulos**:
   * Clonar `https://github.com/jqssun/android-airplay-server` com `--recurse-submodules` em `C:\Projetos\Pessoais\android-airplay-server`.
2. **Compilação**:
   * Executar `./gradlew assembleDebug` validando compilação do código C++ (NDK 27) e Kotlin/Compose.
3. **Instalação e Teste no Emulador/TV**:
   * Instalação via `adb install -r app-debug.apk`.
   * Verificação se o botão de AirPlay no Control Center do GB Launcher detecta o app e o inicia com sucesso.
   * Transmissão de teste a partir de dispositivo Apple (espelhamento de tela e streaming de áudio/vídeo).
