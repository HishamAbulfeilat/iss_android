# inFAMOUS Second Son — Daedalus Fork v0.1

Primeira versão pública do port Android com runtime Bionic, tradução de CPU pelo FEX e renderização Vulkan nativa via AHardwareBuffer.

## Recursos

- Interface com tema de inFAMOUS e menu de configurações dentro do jogo.
- Inglês como idioma padrão e português opcional.
- Metas de 30/60/120/240 FPS, opção de VSync e ajuste de frequência da tela.
- Bloodframe com geração de quadros, HUD configurável e importação da sua própria Lossless.dll.
- D-pad com swipe incremental e continuação ao segurar; TOUCH com dois dedos e controles de grafite.
- Instalação de jogo e atualização por PKG, importação/exportação de saves e exportação de logs.
- Importação de drivers ZIP; Turnip Gen8 V38 Patched para Adreno 8xx e Turnip v27.0.0-R1 para Adreno 6xx/7xx incluídos.

## Desempenho e correções

- Reutilização de samplers e armazenamento de descritores entre desenhos.
- Menos trabalho em regiões sem páginas GPU modificadas e em filas vazias de readback.
- Reutilização de shaders salvos em todos os modos de inicialização e escrita de cache com menos cópias.
- Cache do driver gravado somente após novas compilações e apresentação sem limpeza redundante quando a imagem cobre a saída.
- O tamanho de subgrupo usado na tradução de compute corresponde ao solicitado na pipeline; o tamanho obrigatório é aplicado somente aos estágios aceitos pelo driver.
- Alinhamento explícito das leituras wave64 de ballot, conforme o porte do PC.

A alteração upstream relacionada a partículas, névoa, água e fogo está incluída. Seu efeito visual ainda precisa ser confirmado no aparelho; os testes de código e a compilação não substituem essa confirmação. O código de shaders atualizado inicia um cache novo automaticamente, podendo recompilar shaders na primeira execução.

## Instalação

Android 11 ou superior, ARM64 e GPU/driver Vulkan compatível. Snapdragon/Adreno é o alvo principal. Instale `inFAMOUS-Second-Son-Daedalus-v0.1.apk`, conceda acesso aos arquivos e selecione a pasta do jogo ou instale seu PKG local. A configuração de FPS define uma meta, sem garantir a taxa em todas as cenas.

Esta versão mantém o pacote e o certificado das builds Bionic anteriores e usa código interno 7. Atualize mantendo os dados do app para preservar saves e configurações. A versão glibc é um aplicativo separado.

Nenhum jogo, firmware, chave ou Lossless.dll é distribuído. Para geração de quadros, importe a DLL da sua própria instalação do Lossless Scaling.

Créditos: erickmendes22 / Daedalus Emulator, HayatoG/iss_pc, shadPS4, FEX-Emu, Bloodborne-Android, Daedalus GoW3, Bloodframe, fexdroid, Mesa/Turnip, StevenMXZ e colaboradores. Licenças e atribuições estão preservadas no código e no runtime.

---

## English

Initial public Android Bionic release of inFAMOUS Second Son — Daedalus Fork. Includes the themed launcher and in-game menu, English/Portuguese selection, FPS/VSync settings, Bloodframe, configurable HUD and controls, incremental touchpad swipes, PKG installation, save transfer and driver ZIP import.

This release integrates the eight approved PC performance improvements, subgroup-size consistency and explicit wave64 ballot-load alignment. The upstream particle/fog/water/fire change is included; its visual effect still needs device confirmation. Shader caches refresh automatically after the update. Frame-rate settings are targets, not guaranteed performance.

Requires Android 11+, ARM64 and a compatible Vulkan GPU/driver. Snapdragon/Adreno is the primary target. The APK retains the previous Bionic package and signing certificate, with version code 7. No game files, firmware, keys or Lossless.dll are included. Supply your own compatible game and, for frame generation, your own Lossless Scaling DLL.
