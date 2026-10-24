# AirPlay Receiver Custom UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configurar o fork local de `android-airplay-server` em `C:\Projetos\Pessoais\android-airplay-server`, reciclando os layouts, componentes e botões originais do Compose e aplicando estritamente as cores e o foco do GB Launcher.

**Architecture:** Manter a estrutura existente de Jetpack Compose do app, adaptando o `Theme.kt`, `TvFocus.kt` e as superfícies de cartões e overlays em `MainScreen.kt` e `SettingsScreen.kt` com a paleta exata do GB Launcher (fundo `0xFF141416`, superfícies translúcidas `0x24FFFFFF`/`0xFF1C1C20`, foco pílula branca `0xFFFFFFFF` com tinta `0xFF1C1C1E`, texto `0xF2FFFFFF` e acento `0xFF30D158`). Manter intacto o pipeline C++/JNI UxPlay e a compatibilidade do pacote `io.github.jqssun.airplay`.

**Tech Stack:** Kotlin, Jetpack Compose, Android SDK (build-tools 34, compileSdk 35), NDK 27 (`27.0.12077973`), CMake, Gradle.

**Spec:** [2026-10-24-airplay-receiver-custom-ui-design.md](file:///c:/Projetos/Pessoais/freemytv/docs/superpowers/specs/2026-10-24-airplay-receiver-custom-ui-design.md)

## Global Constraints

- Manter intactos todos os avisos de licença GPL-3.0, arquivos `LICENSE` e créditos ao autor original `jqssun`.
- Não inventar cores ou formas inexistentes: utilizar estritamente a paleta do GB Launcher (`0xFF141416`, `0xFF1C1C20`, `0x24FFFFFF`, foco `0xFFFFFFFF` com tinta `0xFF1C1C1E`, texto `0xF2FFFFFF` e `0x8CFFFFFF`, acento `0xFF30D158`).
- Reciclar os formatos de botões, chips e controles existentes do código original em Compose, modificando apenas a estilização de cores e foco D-pad.
- Manter o `applicationId` como `io.github.jqssun.airplay` para compatibilidade total com o botão AirPlay da Central de Controle do GB Launcher.

## Review Focus

1. **Compilação C++ com NDK 27:** O projeto depende de submódulos nativos (`UxPlay`, `libplist`, `openssl-cmake`, `ffmpeg`); os submódulos devem ser inicializados e compilados sem erros pelo Gradle.
2. **Navegação com Controle Remoto (D-pad):** Na TV, todo elemento selecionável deve exibir alto contraste no foco (pílula branca e texto escuro), sem perda de foco entre linhas/cartões.
3. **Comportamento em Segundo Plano:** O serviço AirPlay deve continuar escutando conexões mDNS e RTSP normalmente, abrindo o overlay quando a transmissão começar.
4. **Legibilidade OSD:** Os controles de reprodução (Play/Pause, barra de progresso, volume) sobrepostos ao vídeo devem permanecer legíveis sobre qualquer conteúdo.
5. **Ajustes e Persistência:** Todas as opções de configuração alteradas na tela de ajustes devem persistir no `SharedPreferences` e responder aos broadcasts do launcher.

---

### Task 1: Clonar o Repositório e Configurar Ambiente de Build

**Files:**
- Directory: `C:\Projetos\Pessoais\android-airplay-server`
- Create: `C:\Projetos\Pessoais\android-airplay-server\local.properties`

**Steps:**
- [x] Verificar se o diretório `C:\Projetos\Pessoais\android-airplay-server` já existe; caso contrário, clonar `https://github.com/jqssun/android-airplay-server.git`.
- [x] Inicializar todos os submódulos git recursivamente: `git submodule update --init --recursive --depth 1`.
- [x] Criar o arquivo `local.properties` apontando para o SDK e NDK instalados:
  ```properties
  sdk.dir=C\:\\Users\\gabri\\AppData\\Local\\Android\\Sdk
  ndk.dir=C\:\\Users\\gabri\\AppData\\Local\\Android\\Sdk\\ndk\\27.0.12077973
  ```
- [x] Executar `./gradlew --version` e verificar se o Gradle wrapper responde corretamente.
- [x] Executar `./gradlew assembleDebug` para validar a integridade do build original antes de qualquer modificação.

---

### Task 2: Customizar o Tema e Paleta (Theme.kt e Cores GB Launcher)

**Files:**
- Modify: `C:\Projetos\Pessoais\android-airplay-server\app\src\main\kotlin\io/github/jqssun/airplay/ui/theme/Theme.kt`

**Steps:**
- [x] Atualizar o esquema de cores escuro em `Theme.kt` para usar exatamente os valores do GB Launcher:
  - `background`: `Color(0xFF141416)` (cinza escuro do launcher)
  - `surface`: `Color(0xFF1C1C20)` (superfície de cartões)
  - `surfaceVariant`: `Color(0xFF26262B)`
  - `onBackground` e `onSurface`: `Color(0xFFFFFFFF)` (texto primário)
  - `onSurfaceVariant`: `Color(0x8CFFFFFF)` (texto secundário/dim)
  - `primary`: `Color(0xFFFFFFFF)`
  - `onPrimary`: `Color(0xFF1C1C1E)`
  - `secondary`: `Color(0xFF30D158)` (verde ativo do launcher)
- [x] Garantir que o modo escuro seja padrão na TV, mantendo alto contraste em todas as telas.
- [x] Compilar `./gradlew compileDebugKotlin` para validar a sintaxe e tipos.

---

### Task 3: Refinar o Foco D-Pad (TvFocus.kt)

**Files:**
- Modify: `C:\Projetos\Pessoais\android-airplay-server\app\src\main\kotlin\io/github/jqssun/airplay/ui/TvFocus.kt`

**Steps:**
- [x] Analisar o modificador de foco existente `Modifier.dpadFocus(...)`.
- [x] Ajustar as cores do foco quando focado para coincidir com a assinatura do GB Launcher:
  - Fundo do foco: `Color(0xFFFFFFFF)`
  - Conteúdo/texto quando focado: `Color(0xFF1C1C1E)`
  - Elevação e borda: `Color(0x33FFFFFF)` quando desfocado.
- [x] Preservar a forma (pill/arredondada) e animação suave de transição de foco.
- [x] Compilar `./gradlew compileDebugKotlin` para verificar conformidade.

---

### Task 4: Ajustar MainScreen e Player Overlay Reciclando o Layout Original

**Files:**
- Modify: `C:\Projetos\Pessoais\android-airplay-server\app\src\main\kotlin\io/github/jqssun/airplay/ui/MainScreen.kt`

**Steps:**
- [x] Manter todos os elementos visuais originais (cartão de status do servidor, conexões, PIN, tabs Overview/Logs/Settings).
- [x] Reciclar os containers existentes aplicando o fundo translúcido `Color(0x24FFFFFF)` e bordas sutis `Color(0x18FFFFFF)`.
- [x] Garantir que os botões de ação (Iniciar/Parar servidor, alternar tela cheia) usem a pílula de foco branca com texto invertido.
- [x] Ajustar o `DebugOverlay` e o `MiniController` de vídeo para que a barra de controles sobreposta ao vídeo use o fundo escuro translúcido padrão do launcher (`Color(0xE41E1E22)`).
- [x] Compilar `./gradlew compileDebugKotlin`.

---

### Task 5: Ajustar SettingsScreen Reciclando os Componentes Originais

**Files:**
- Modify: `C:\Projetos\Pessoais\android-airplay-server\app\src\main\kotlin\io/github/jqssun/airplay/ui/SettingsScreen.kt`

**Steps:**
- [x] Manter todos os componentes originais (`SettingSwitch`, `SettingChips`, `SettingSegmented`, `SettingTextField`).
- [x] Atualizar as cores de estado dos switches:
  - Switch ligado: track em verde `Color(0xFF30D158)` ou branco `Color(0xFFFFFFFF)`, alinhado com o [SettingsActivity.java](file:///c:/Projetos/Pessoais/freemytv/app/src/io/freemytv/SettingsActivity.java).
  - Switch desligado: `Color(0x47FFFFFF)`.
- [x] Assegurar que ao navegar pelas opções com o controle remoto, a linha ou chip selecionado receba o destaque de foco claro com texto escuro.
- [x] Compilar `./gradlew compileDebugKotlin`.

---

### Task 6: Validação Final, Build do APK e Documentação

**Files:**
- Modify: `C:\Projetos\Pessoais\android-airplay-server\README.md`
- Output: `C:\Projetos\Pessoais\android-airplay-server\app\build\outputs\apk\debug\app-debug.apk`

**Steps:**
- [x] Executar `./gradlew assembleDebug` gerando o APK final customizado.
- [x] Verificar que o build foi concluído com sucesso e que o APK gerado possui as bibliotecas nativas de arquitetura correspondentes (`arm64-v8a`, `armeabi-v7a`).
- [x] Adicionar ao `README.md` do fork a seção de notas sobre a customização para o GB Launcher, preservando a atribuição e licença GPL-3.0.
- [x] Salvar commit com as alterações no repositório do fork.
