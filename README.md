# inFAMOUS Second Son — Daedalus Android Fork

Projeto **iss_android**, mantido por [erickmendes22 — Daedalus Emulator](https://www.youtube.com/@daedalusemulator), baseado em [HayatoG/iss_pc](https://github.com/HayatoG/iss_pc).

## Estado atual (17/10/2026)

**APK experimental Android ARM64 gerado por CI, usando Linux/glibc + FEXCore. O jogo ainda não foi validado em um dispositivo Android.**

A branch [`android-glibc`](https://github.com/erickmendes22/iss_android/tree/android-glibc) contém a primeira integração de runtime, tradução x86-64 → ARM64, renderizador Vulkan, launcher Android, controles, áudio e empacotamento. A branch `main` permanece separada.

O [GitHub Actions Android glibc](https://github.com/erickmendes22/iss_android/actions/workflows/android-glibc.yml) compilou os binários nativos, gerou o APK, assinou-o com uma chave local de desenvolvimento e verificou o pacote em 17/10/2026 ([execução aprovada](https://github.com/erickmendes22/iss_android/actions/runs/39031239053)). O APK está dentro do artefato `iss-android-glibc-apk-...` dessa execução, junto do checksum.

**Não há confirmação de boot, menu, saves, renderização ou gameplay no Redmagic/Android.** A aprovação do CI comprova somente compilação e empacotamento; a etapa posterior é testar com uma cópia própria e compatível do jogo. O APK usa assinatura local experimental, que pode mudar entre builds e exigir desinstalar a versão anterior para instalar outra.

## Arquitetura da etapa glibc

- **CPU:** código x86-64 do jogo traduzido por FEXCore; ponte `guest_cpu` adaptada para threads, TLS, exceções, callbacks e fibras ARM64.
- **GPU:** biblioteca Vulkan `libtlggpu.so` preservando correções e serviços do `iss_pc`; erros de compilação ARM64 foram resolvidos sem importar patches específicos de outros jogos.
- **Android:** launcher Java, apresentação via bridge fexdroid/Xvfb, controles físicos e virtuais, modo sensor para grafite, áudio e exportação de logs.
- **Empacotamento:** rootfs glibc e scripts de preparação do `iss_pc` incluídos; `eboot.bin`, arquivos de jogo, saves de terceiros e firmware não são distribuídos.
- **Primeiro teste:** exige arquivos extraídos do inFAMOUS Second Son, com atualização 1.07 integrada na mesma pasta (referência upstream `CUSA00223`). A compatibilidade em Android permanece não confirmada.

O build experimental contém a implementação **glibc**, e **não** um runtime Bionic. A migração para Android/Bionic é uma etapa separada, posterior à validação da inicialização e do jogo na base glibc.

## Base preservada

- Código, submódulos, serviços e correções específicas do inFAMOUS provenientes de `HayatoG/iss_pc`.
- Base inicial: `1898d449a7edf506d12ef63445f8fc5432e60105`.
- Documentação original de PC preservada em [README.upstream.md](README.upstream.md).
- Build de PC preservado; o workflow Android experimental é separado.

Consulte [docs/ANDROID_PORT.md](docs/ANDROID_PORT.md) para o estado de cada etapa.

## Créditos

- [HayatoG/iss_pc](https://github.com/HayatoG/iss_pc) — projeto de origem e adaptações do inFAMOUS.
- [God of War III — Daedalus Fork](https://github.com/erickmendes22/gow3_android_daedalus_fork) — referência da infraestrutura Linux/glibc Android e FEX, sem reaproveitar patches de jogo.
- [ducvd89/Bloodborne-Android](https://github.com/ducvd89/Bloodborne-Android) — referência de integração Android e futura etapa Bionic.
- [FEX-Emu](https://github.com/FEX-Emu/FEX) — tradução dinâmica de x86-64 para ARM64.
- [shadPS4](https://github.com/shadps4-emu/shadPS4), [fexdroid](https://github.com/cobrabm12/fexdroid) e demais autores e colaboradores creditados na documentação e fontes.

Atribuições e licenças existentes devem ser preservadas. Nenhum arquivo de jogo ou firmware é distribuído. Projeto sem afiliação com Sony Interactive Entertainment ou Sucker Punch Productions.
