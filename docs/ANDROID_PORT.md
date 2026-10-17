# Plano do port Android

## Escopo inicial

Projeto separado de God of War III: preservar a base de inFAMOUS Second Son e adaptar apenas as camadas necessárias ao Android ARM64. Primeiro Linux/glibc com FEXCore; depois Android/Bionic.

Base upstream: HayatoG/iss_pc, commit 1898d449a7edf506d12ef63445f8fc5432e60105.

## Etapas de implementação

1. Introduzir a interface guest_cpu entre o runtime e a execução do jogo, mantendo o caminho x86-64 de PC. Adaptar imports, callbacks, entradas de threads, TLS/GS, contadores de tempo e tratamento de sinais.
2. Integrar FEXCore com versão fixada e compilar o runtime Linux ARM64 com glibc. Auditar caminhos de assembly x86, fibras e acessos a registradores de contexto.
3. Adaptar o renderizador e sua ponte ao apresentador Android, sem depender de um servidor X11. Auditar o walker SRT, a proteção de páginas e os recursos Vulkan disponíveis no dispositivo.
4. Integrar o aplicativo Android: seleção de jogo, instalação, controles, áudio, apresentação, logs e empacotamento do runtime. Preservar o sensor de movimento e touchpad específicos do inFAMOUS.
5. Validar inicialização, menus, carregamento de save e gameplay em aparelho real antes de avaliar desempenho ou declarar compatibilidade.
6. Migrar para Android/Bionic usando a ponte de CPU e interfaces de apresentação já isoladas, revalidando comportamento e empacotamento.

## Regras de integração

- Não aplicar patches de executável, endereços, resoluções, limites de FPS ou configurações de readback específicos de God of War III ao inFAMOUS.
- Preservar as correções gráficas e serviços próprios do iss_pc; avaliar equivalência antes de trazer otimizações de outro port.
- Não apresentar uma compilação bem-sucedida como confirmação de funcionamento no aparelho.
- Manter créditos, licenças e referências das partes incorporadas.
- Não distribuir dumps de jogo, firmware, saves de terceiros ou bibliotecas proprietárias.

## Bloqueios identificados no código de origem

- port/src/probe.c exige x86-64 e acessa registradores e assembly dessa arquitetura.
- port/src/runtime_thread.c usa arch_prctl/ARCH_SET_GS e instruções x86 de estado de ponto flutuante.
- O tratamento de sinais e diagnóstico de falhas de memória assume contextos x86 em vários caminhos.
- port/src/runtime_gr2.c contém fibras com getcontext/makecontext/swapcontext, que exigirão auditoria na etapa Bionic.
- port/gpu/CMakeLists.txt exige X11 nas plataformas fora do Windows e ainda não tem um alvo Android.
- O projeto não possui integração guest_cpu/FEXCore na base inicial.

Esta preparação adiciona somente identificação e documentação do projeto Android. Não inclui ainda as adaptações acima nem um workflow de APK.
