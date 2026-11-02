# inFAMOUS Second Son — Daedalus Fork v0.1

Primeira versão pública do port Android com runtime Bionic, tradução de CPU pelo FEX e renderização Vulkan nativa via AHardwareBuffer.

## Ajustes finais desta build

- Classificação Android como jogo, equivalente à configuração do GoW3, para identificação pelo modo de jogos do fabricante.
- Áudio Bionic pelo misturador PCM nativo, com saída s16 estéreo a 48 kHz para o AudioBridge; mantém conversão, volume e portas Audio3d do inFAMOUS.
- Fullscreen apenas em Início → Vídeo, desligado por padrão. Desligado usa 16:9 automático e bloqueia os modos; ligado permite esticar ou cortar bordas.
- Driver Vulkan apenas em Configurações. Resolução definida apenas em Patches, com saída correspondente: 720p, 900p ou 1080p original.

- 16:9 usa toda a altura disponível do jogo, com barras laterais e sem barras de status/navegação do Android. Fullscreen controla apenas o preenchimento da imagem.
- Renderização restaurada à base anterior aos portes de PC, após relato de regressão de iluminação. A causa individual ainda não foi isolada; confirmação visual depende do aparelho.

## Recursos

- Interface com tema de inFAMOUS e menu de configurações dentro do jogo.
- Inglês como idioma padrão e português opcional.
- Metas de 30/60/120/240 FPS, opção de VSync e ajuste de frequência da tela.
- Bloodframe com geração de quadros, HUD configurável e importação da sua própria Lossless.dll.
- D-pad com swipe incremental e continuação ao segurar; TOUCH com dois dedos e controles de grafite.
- Instalação de jogo e atualização por PKG, importação/exportação de saves e exportação de logs.
- Importação de drivers ZIP; Turnip Gen8 V38 Patched para Adreno 8xx e Turnip v27.0.0-R1 para Adreno 6xx/7xx incluídos.

## Renderização

Os oito portes recentes de desempenho e as alterações de subgroup/ballot foram retirados desta build para retomar a base gráfica anterior (0f9a9a4), após relato de iluminação piscando. A ordem segura de inicialização do salvador de cache foi preservada. As melhorias de interface, áudio, controles, drivers e gerenciamento permanecem.

## Instalação

Android 11 ou superior, ARM64 e GPU/driver Vulkan compatível. Snapdragon/Adreno é o alvo principal. Instale `inFAMOUS-Second-Son-Daedalus-v0.1.apk`, conceda acesso aos arquivos e selecione a pasta do jogo ou instale seu PKG local. A configuração de FPS define uma meta, sem garantir a taxa em todas as cenas.

Esta versão mantém o pacote e o certificado das builds Bionic anteriores e usa código interno 11. Atualize mantendo os dados do app para preservar saves e configurações. A versão glibc é um aplicativo separado.

Nenhum jogo, firmware, chave ou Lossless.dll é distribuído. Para geração de quadros, importe a DLL da sua própria instalação do Lossless Scaling.

Créditos: erickmendes22 / Daedalus Emulator, HayatoG/iss_pc, shadPS4, FEX-Emu, Bloodborne-Android, Daedalus GoW3, Bloodframe, fexdroid, Mesa/Turnip, StevenMXZ e colaboradores. Licenças e atribuições estão preservadas no código e no runtime.

---

## English

This build adds Android game classification, native fixed-format PCM audio output, and an optional Fullscreen switch only in Home → Video that defaults to off. Off automatically uses 16:9 and disables mode selection; on enables stretch or crop. Vulkan drivers are only in Settings. Output resolution follows the resolution patch (720p, 900p or native 1080p).

Initial public Android Bionic release of inFAMOUS Second Son — Daedalus Fork. Includes the themed launcher and in-game menu, English/Portuguese selection, FPS/VSync settings, Bloodframe, configurable HUD and controls, incremental touchpad swipes, PKG installation, save transfer and driver ZIP import.

This build restores the GPU paths preceding the recent PC performance and subgroup/ballot ports after a reported lighting regression. The individual culprit is not yet isolated; on-device visual confirmation is pending. Shader caches refresh automatically. Safe cache-saver startup ordering is retained. 16:9 now keeps Android system bars hidden and uses the available display height. Fullscreen still controls image filling.

Requires Android 11+, ARM64 and a compatible Vulkan GPU/driver. Snapdragon/Adreno is the primary target. The APK retains the previous Bionic package and signing certificate, with version code 11. No game files, firmware, keys or Lossless.dll are included. Supply your own compatible game and, for frame generation, your own Lossless Scaling DLL.
