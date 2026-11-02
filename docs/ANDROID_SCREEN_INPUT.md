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

## Comparação com BACHATA S4 0.2.5 Linux/glibc (17/10/2026)

APK de referência: `bachatas4-0.2.5-release(1).apk`. Contém dois
runtimes ARM64 distintos: `lib/arm64-v8a/libbachata_native_runtime.so`
(Android Bionic) e `assets/runtime/runtime.zip`, com
`host/shadps4-arm64-fex` (Linux/glibc, ~75 MB, build ID
`b505e6d4f2dbf557e2d9577ca9029281d685fb96`). Portanto não é
correto comparar só Bionic. O runtime Linux usa a distribuição glibc
e integra FEX, Mesa/Vortek e interfaces gráficas específicas do BACHATA.

A branch `zenithblue-oss/shadps4-arm64` foi conferida nos módulos
`image.cpp`, `image_info.cpp`, `texture_cache.cpp` e `pad.cpp`.
`iss_android` já contém cópia de imagens 2D/3D com tratamento do número
de camadas, fallback Vulkan para formatos que não aceitam uso de storage,
cálculo de tamanho de texturas tiled e backports próprios. Não
substituir componentes equivalentes por implementações antigas: isso
poderia introduzir regressões, e o core BACHATA não é compatível
diretamente com o presenter e o loader específicos do port de inFAMOUS.

O log do jogo demonstra pressão gráfica grande, mas **não prova que
ela cause os erros visuais**. Observou-se `BP09=0` e cópia de imagem
com 1 camada na origem e 256 no destino, mais variação de frametime.
A correção de alias de LUT 1D/2D `BP09`, já existente no fork, passa a
ser habilitada por padrão no launcher Android e permanece configurável.
`TLG_BC_TINY` é opcional, desativado inicialmente. A alteração
isolada de `BP09` tem fundamento na origem das texturas; não se
declara que corrigiu todas as texturas ou o flicker antes do teste
na Adreno 830. Os patches internos CUSA00004 v01.00 continuam separados
e preservados.

Spray: somente o controle **virtual Android** no modo de movimento
sintético foi ajustado (0.48 vs 1.2 rad/s, filtro da direção da
alavanca ~100 ms, integração limitada a 50 ms e ângulos menores).
Outros sticks, teclado, controles SDL reais, R2, Cross e comandos
normais não foram modificados.

Exibição: FIT = 16:9 sem cortes (barras proporcionais); STRETCH =
ocupa a tela toda, sem cortar HUD (proporção distorcida); CROP =
preenchimento proporcional que corta bordas, opção explícita.
A barra de navegação/status Android não deve diminuir a imagem em FIT:
todos os modos usam janela imersiva.
