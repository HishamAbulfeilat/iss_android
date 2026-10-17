# Android screen / input regressions

The graffiti help overlay is an SDL input modal. Android TouchControllerOverlay
writes `android-pad.state`, not SDL keyboard events. The Vulkan overlay now
checks the same state for a **new** cross/circle/options press while visible,
closing the modal without faking gamepad events. A short-lived
`android-pad.state.guide` marker also permits pointer clicks on the real
"Got it" button without turning touchscreen taps into in-game R2 input.

The app's launcher offers a real **FPS HUD** (the native presenter timing),
`Fit 16:9` vs `Fill`, and 720p/1080p **output resolution**. Fit/Fill
affects only the Android SurfaceView (Xvfb remains 1280x720); output resolution
affects the Vulkan output target and must **not** be confused with game-internal
resolution patches. The latter require CUSA00004 01.00 offsets and signatures.

The recorded FPS oscillates from ~60 in lighter scenes to ~24 in heavy scenes.
A 12.3-second freeze recovered and texture allocation reached ~6 GiB against a
~3 GiB configured budget. No assumption is made that a display mode or FPS HUD
will fix these independent GPU memory / frame pacing problems.

## Patches internos inFAMOUS CUSA00004 01.00

O XML enviado contém cinco patches para **01.00**: renderização interna
1280x720 ou 1600x900 (exclusivos), desativar Motion Blur, Depth of Field e
Depth Blur. São opções independentes, inicialmente desligadas.

O seletor Android envia a seleção pelo `ISS_PATCHES` ao compilador de
patches. Os `Type="mask"` são procurados unicamente na memória executável
file-backed de `eboot.bin` dentro do `boot-linked.bin`. Cada assinatura
exige correspondência única e cada escrita precisa ficar na região válida.
Uma assinatura ausente, duplicada ou sobreposta interrompe a seleção com
erro explícito, sem gerar `patches.bin` parcial nem tocar o eboot original.

Os arquivos experimentais enviados têm `CUSA00223` e `01.07` ou
`AppVer=mask`: não são carregados na CUSA00004 01.00. Os XMLs não contêm
patch de sombras; não inventar offsets. A opção "resolução de saída" é
independente do patch de **resolução interna**.
