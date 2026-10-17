# inFAMOUS Second Son — plano do port Android

Projeto `iss_android` separado dos ports de God of War III e Bloodborne. Base upstream: `HayatoG/iss_pc`, commit `1898d449a7edf506d12ef63445f8fc5432e60105`.

## Estado verificado em 17/10/2026

| Etapa | Estado | Evidência/limitação |
| --- | --- | --- |
| Ponte `guest_cpu`, FEXCore, TLS e fibras ARM64 | Implementada; compilada | Contextos FEX independentes; testes sintéticos anteriores não substituem teste real com jogo |
| Biblioteca Vulkan `libtlggpu.so` | Compilada para ARM64 | Mantidas correções do inFAMOUS do PC; ainda sem renderização validada no dispositivo |
| Loader `iss-probe` glibc | Compilado para ARM64 | Inclui FreeType ARM64; checagem CPUID x86 mantida somente no PC |
| Launcher Android, controles, sensor para grafite, áudio e bridge de imagem | Compilados/empacotados | Interfaces de dispositivo ainda não validadas no jogo |
| Rootfs, scripts do jogo e APK | Empacotados/assinados | [Actions Android glibc aprovado](https://github.com/erickmendes22/iss_android/actions/runs/39031239053); assinatura APK v3 verificada |
| Boot/menu/save/gameplay no Android | **Pendente** | Precisa de teste em aparelho real e logs, sem prometer desempenho |
| Migração para Bionic | **Não iniciada nesta branch** | Somente após estabilidade glibc |

## Sequência técnica

1. A camada x86-64 do `iss_pc` usa `guest_cpu`/FEX no host ARM64 Linux/glibc, preservando a execução de PC.
2. Na primeira etapa Android, a imagem passa por Xvfb/bridge fexdroid até a `Surface` do aplicativo. A substituição desta cadeia por uma apresentação Android/Bionic realmente nativa é uma etapa posterior.
3. O launcher recebe uma pasta extraída com `eboot.bin` e executa os scripts originais de preparação/vinculação de módulos antes de iniciar `iss-probe`. Nenhum arquivo de jogo é incluído no APK.
4. O controle Android é publicado num arquivo de estado consumido pelo runtime do inFAMOUS. L3+R3 alterna o modo sensor do grafite, sem modificar a configuração do controle de PC.
5. O APK da branch `android-glibc` é produzido pelo workflow `.github/workflows/android-glibc.yml`. O artefato é assinado com chave **de desenvolvimento local**; instalações sucessivas podem ter certificados diferentes.
6. Antes de qualquer alegação de funcionamento, validar instalação limpa, arranque do glibc, `iss-probe`, apresentação Vulkan, controles, grafite, saves, menus e gameplay, nesta ordem.
7. Somente depois de validar os blocos essenciais, planejar migração Bionic com a mesma semântica de CPU, GPU e HLE.

## Regras de integração

- Não transferir indiscriminadamente endereços, patch de executável, resolução, FPS ou readback específicos de God of War III.
- Não descartar otimizações e correções exclusivas do `iss_pc`.
- Não confundir APK gerado em CI com compatibilidade comprovada em dispositivo.
- Manter a branch `main` separada da integração experimental e preservar créditos/licenças.
- Não distribuir dumps de jogo, firmware, saves de terceiros ou bibliotecas proprietárias do jogo.

## Falhas de build corrigidas nesta integração

- `CPUState` do FEX possui contador de sinais não atribuível. A cópia dos estados de fibras preserva os componentes internos de cada thread.
- A busca de informações binárias de shaders usa conversões de ponteiros em tempo de execução: funções não são mais declaradas `constexpr` inválidas no Clang ARM64.
- FreeType usa `ft2build.h` sob o diretório `freetype2` do sysroot ARM64.
- `cpu_check.c` tem instruções CPUID de x86 e não deve entrar no link do loader ARM64.

## Correções de inicialização após teste real (17/10/2026)

O log do primeiro APK parou antes de abrir o jogo: `/rootfs/usr/bin/mkdir: No such file or directory`.
A causa observável é a seleção de um ELF glibc por `PATH` no `/system/bin/sh` Android,
não um crash dentro do inFAMOUS. Além disso, o empacotador legado migrava
`PT_INTERP` para `com.iss.android`, embora o application ID seja `com.daedalus.iss`.
Esta revisão usa somente `/system/bin` para ferramentas do shell Android,
repara interpretadores ARM64 do rootfs em cache, valida seus executáveis/ICD e inclui
`launch.py`, antes ausente. O launcher passa a pré-validar os arquivos originais
do jogo, executar a preparação do `iss_pc` com glibc explícita e preservar
as opções gráficas do port PC. Logs identificam agora as etapas de preparação.

**Estado:** correções de código não equivalem a boot comprovado, menu, renderização,
controles, áudio ou desempenho em dispositivo. Esses itens continuam pendentes de teste real.

## Vulkan Linux/glibc (Adreno Gen8)

A primeira build continha a `libvulkan_freedreno.so` do rootfs de fexdroid,
mas não executava `build_turnip.sh gen8` nem trazia
`libvulkan_freedreno_gen8.so`. A etapa CI agora compila a variante Gen8
da Mesa/Turnip com suporte KGSL, suas correções e o ICD Vulkan associado;
a montagem do APK exige os dois arquivos. O helper ARM64
`kgsl-chip-id` consulta `/dev/kgsl-3d0`; chip IDs `0x44...`
selecionam o ICD Gen8. Outros IDs preservam a versão base do rootfs.
Os arquivos do APK são ELFs Linux/ARM64/glibc, não drivers Android/Bionic.
Compilação e empacotamento são verificações distintas de inicialização Vulkan
e renderização em dispositivo, que permanecem pendentes.

## Revisão preventiva do fluxo pós-seleção (17/10/2026)

- **Dados do jogo:** inFAMOUS usa `art/`, não `dvdroot_ps4/` ou `*.psarc`.
  Preflight verifica arquivos reais do dump (`eboot.bin`, `sce_sys/param.sfo`,
  `libc.prx`, `libSceFios2.prx`) e acesso a `art/`, sem ler assets de ~33 GB.
  `prepare.py` passa a relatar categorias e tamanhos sob `art/`.
- **Parâmetros:** o launcher Android usa o preset do `port/launcher/infamous_run.py`
  (precise readbacks, write merge, deferred readback, cache 3500 MB,
  upscaler/jitter/object motion off). Opções `ISS_*` explícitas continuam prioritárias;
  não aplica presets nem patches de God of War III.
- **Configuração:** saves, shader cache e `tlg.ini` estão no diretório `issport/user`;
  `ISS_CONFIG`/`TLG_CONFIG` apontam para o mesmo local.
- **Reinicialização:** no Android `runtime_restart` volta ao `run-iss.sh` usando
  `/system/bin/sh` em vez do `bash run.sh` inexistente. Continua sem validação
  prática de reinicialização no jogo.
- **Verificação automatizada:** testes com estruturas de pastas artificiais
  verificam a aceitação de `art/` e a rejeição de um dump incompleto.
  Não representam teste de gameplay, módulos reais ou GPU física.
