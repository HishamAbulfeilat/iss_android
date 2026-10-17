# inFAMOUS Second Son — Daedalus Android Fork

Projeto **iss_android**, mantido por [erickmendes22 — Daedalus Emulator](https://www.youtube.com/@daedalusemulator), baseado em [HayatoG/iss_pc](https://github.com/HayatoG/iss_pc).

## Estado atual

**Preparação do port para Android ARM64. Ainda não há APK Android nem gameplay Android validada neste projeto.**

A primeira etapa utilizará **Linux/glibc + FEXCore**, reaproveitando a infraestrutura dos ports de God of War III e Bloodborne. Após validar inicialização e gameplay, a etapa seguinte será a migração para o runtime nativo **Android/Bionic**.

O código original do jogo é x86-64 e precisará de tradução para ARM64. O renderizador traduz comandos gráficos e shaders do PS4 para Vulkan.

## Base preservada

- Código, submódulos e correções específicas do inFAMOUS provenientes de `HayatoG/iss_pc`.
- Base inicial: `1898d449a7edf506d12ef63445f8fc5432e60105`.
- Documentação original preservada em [README.upstream.md](README.upstream.md). Seus requisitos e instruções de execução descrevem a versão de PC.
- Os workflows herdados ainda compilam a versão de PC; não geram um APK Android.

Consulte o [plano de adaptação](docs/ANDROID_PORT.md) para as etapas e os pontos que precisam de implementação.

## Créditos

- [HayatoG/iss_pc](https://github.com/HayatoG/iss_pc) — projeto de origem, runtime e adaptações específicas do inFAMOUS.
- [God of War III — Daedalus Fork](https://github.com/erickmendes22/gow3_android_daedalus_fork) — referência para a infraestrutura Android e integração FEX.
- [ducvd89/Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android) — referência para a etapa Android/Bionic.
- [FEX-Emu](https://github.com/FEX-Emu/FEX) — tradução dinâmica x86-64 para ARM64 planejada.
- [shadPS4](https://github.com/shadps4-emu/shadPS4) e demais projetos e colaboradores creditados na [documentação original](README.upstream.md).

As atribuições e licenças existentes nos arquivos e componentes continuam preservadas. Os projetos Android citados são referências de integração; sua infraestrutura ainda não foi incorporada nesta preparação inicial.

Nenhum arquivo do jogo ou firmware é distribuído. É necessária uma cópia própria do jogo. Projeto sem afiliação com Sony Interactive Entertainment ou Sucker Punch Productions.
