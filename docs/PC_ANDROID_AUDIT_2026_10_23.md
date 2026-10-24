> Build 76 follow-up: R1–R8, N1–N4 and N9 integrated with Android-specific preservation and fallbacks. N5–N8, R9/R10 and V1–V5 remain outside this build. The audit below describes the pre-integration Build 75 comparison. Device validation is pending.

# PC → Android Bionic: auditoria de desempenho, gráficos e estabilidade

Data: 23/10/2026. Auditoria de código; nenhuma medição nova no aparelho.

## Escopo e método

- Android: `experimental-bionic`, HEAD `97d1fb8afd1137895d0d5fe943993cf55fcba166` (Build 75).
- As alterações locais posteriores de legendas/inicialização foram preservadas. Não alteram a comparação do GPU/HLE.
- PC: checkout da branch padrão `developer`, fixado em `7a48167dcba8b25ef66fb3f99d712415f225790f`.
- Releases consultadas: estável `v1.0` (`cde38af`) e notas beta de 23/10.
- Comparação direta de `port/gpu`, `port/src`, configurações, histórico e `port/gpu_v2`.
  O diff bruto de `port/gpu` contém 134 arquivos alterados/adicionados/removidos;
  isso NÃO equivale a 134 correções que devam ser portadas.
- Lidos também `BIONIC_PC_PERFORMANCE_PORT.md`, notas da reversão e
  `docs/TEST_PR227_MAIN.md` no PC. Uma alteração revertida não é novidade.
- Ausência foi verificada no consumidor e no caminho de build, não apenas nas variáveis do launcher.
- Nada desta auditoria foi incorporado ao renderer. Compatibilidade de fonte não comprova ganho nem segurança visual em Turnip.

## Candidatos ausentes, independentes de portar o Renderer v2 inteiro

| ID | Mudança e origem | Evidência / caminho | Viabilidade e validação necessária |
|---|---|---|---|
| N1 | Stride dinâmico de buffers compute, PR #402 | PC: `shader_recompiler/recompiler_toggles.h`, `info.h`, `specialization.h`, `ir/passes/resource_patching_pass.cpp`, captura de descritores. Android não tem `DynamicBufferStride`/`UsesDynamicStride`. | Forte candidato gráfico: PC relata fumaça sem flicker e menos variantes. Portar o conjunto, inclusive operações pós-fetch e salt do cache; testar fumaça, fogo e partículas em movimento. Não é só ativar uma variável. |
| N2 | Oclusão Vulkan real, `900ba47` / PR #402 | PC acrescenta `vk_occlusion.{h,cpp}`, integração rasterizer/runtime/Liverpool e recursos de queries na Instance. Android responde contadores sintéticos, modo 3, sem essas classes. | Correção de visibilidade de brilho/faróis, não desligamento da iluminação. Depende de host query reset e capacidades do driver. Preservar modo 3 de fallback e não introduzir espera de query por draw. Validar luz atrás de paredes e à vista. |
| N3 | Write-backs do GC em lote, `af14cf5` | `texture_cache.cpp/.h`, `TLG_GC_BATCH_DOWNLOAD`; seleciona imagens, grava cópias e faz uma espera para o lote antes de write-back/liberação. Ausente no Android. | Adaptável, opcional no PC. Manter imagens e proteção de páginas até o write-back, limitar staging (PC usa até 256 MiB no lote) e evitar liberar duas vezes. Comparar stutter e pico de RAM. Pouco efeito quando não há expulsões com download. |
| N4 | Blocos VMA de 64 MiB, PR #547 | V2 `gpu/overrides/vk_instance.cpp`: `preferredLargeHeapBlockSize`, `ISS_VMA_BLOCK_MB`. Android usa tamanho padrão do VMA. | A política pode ser adaptada à Instance atual sem native passes. O valor é tamanho preferido de bloco, não limite de alocação nem orçamento total. Benefício de até ~300 MB pertence ao teste PC; medir fragmentação, número de alocações e falhas no Android. |
| N5 | Construção diferida de pipelines gráficos sem pular draw, `e3f7e99` | `vk_graphics_pipeline.{h,cpp}`: `TLG_GFX_DEFERRED_BIND`, armazenamento durável de create-info, trabalho no compilador e espera em Handle/bind. Ausente no Android. | Experimental, desligado no PC. Pode sobrepor compilações, mas ainda espera quando a pipeline é necessária. Exige vida útil correta de pointers, layouts, callbacks e teardown; preservar templates Android e limite de workers para não competir excessivamente com FEX. |
| N6 | Reabastecer caches dos workers após novos pipelines, `e3f7e99` | `vk_pipeline_cache.cpp`, `TLG_RESEED_WORKER_CACHES`, marcador de pipelines novos e fila ociosa. Android faz seed inicial apenas. | Opcional/desligado no PC. Pode reduzir recompilação redundante; merge também custa tempo e memória. Executar com workers ociosos e comparar número de compilações e duração do merge. |
| N7 | Mesclar barreiras adjacentes de imagens | `texture_cache/barrier_merge.h`, `image.cpp`, `test_barrier_merge.cpp`; `TLG_BARRIER_MERGE=1`. Ausente no Android. | Experimental: só unir mesma imagem, estados, acessos, layouts, famílias e subrecursos adjacentes. Preservar regras Android de barreiras e tracking. PC não comprovou que isso resolve o crash Polaris; não anunciar como correção de crash. |
| N8 | Join local de readback | `buffer_cache.cpp/.h`, `TLG_READBACK_LOCAL_JOIN`: aguardar cópia já registrada sem nova ida ao GPU command thread. Ausente no Android. | Caminho Precise/deferred; o padrão Bionic é Relaxed, portanto não é melhoria automaticamente ativa no nosso uso. Integrar apenas preservando geração de writes, invalidação e propriedade do scheduler. |
| N9 | Teto do orçamento forçado pelo orçamento atual da GPU integrada, `fa7beb9` | `TextureCache::GarbageCollectImages`: min(orçamento forçado, orçamento atual). Android usa o forçado quando fornecido; boot Bionic normal deliberadamente não força 3500 MB. | Pequena proteção útil para overrides; no padrão atual a condição problemática não é ativada. Não vender como novo gerenciador automático de RAM. |
| N10 | Máscara de diagnóstico com todos os bits, PR #272 | `runtime_memory.c::toggle_watcher`: separar estado não lido do sentinel ULLONG_MAX. Android ainda usa sentinel. | Correção pequena de ferramenta de diagnóstico. Não aumenta FPS nem corrige renderização normal. |
| N11 | Limitar vibração repetida, PR #508 | `runtime_pad.c`, `rumble_gate.h`: rate limit antes de SDL_RumbleGamepad. Ausente no Android. | Resolve sobrecarga do caminho SDL/InputPlumber no PC. Só tem efeito se o controle efetivamente usar esse backend de rumble; não presume corrigir o caminho de eventos Java do Android. |

## Ausentes porque foram revertidos após a regressão de iluminação

As mudanças abaixo já haviam sido aprovadas/portadas no histórico do Android.
São candidatas à reintrodução seletiva, não recursos novos. O culpado da regressão
do conjunto não foi isolado por teste de aparelho. Não aplicar tudo junto novamente.

| ID | Origem | Falta no Build 75 / cuidado |
|---|---|---|
| R1 | `9743950` | Memo de samplers com geração para invalidar handles após GC. Requer invalidação e LRU sob lock, não simples reaproveitamento de ponteiros. |
| R2 | `9743950` | Storage persistente para descriptor writes. Preservar contagem ativa e os três caminhos Android: template, push e update normal; zerar ponteiros inativos. |
| R3 | `7326e04` | Resumo de páginas GPU-modificadas para pular regiões vazias. Atualizar contadores em todos os modos de readback e não criar falso negativo. |
| R4 | `af14cf5` | Retorno imediato em ProcessDownloadImages sem trabalho, evitando waits/locks em fences vazias. Marcar cada enqueue/clear/erase; cópias pendentes continuam sendo retiradas. |
| R5 | `e3f7e99` | Carregar programas salvos em todo miss normal, mesmo sem warmup/lazy_programs. Separar índices/variantes e invalidar cache ao alterar shaders. |
| R6 | `e3f7e99` | Mover blobs/paths para o writer assíncrono em vez de copiá-los; conservar escrita atômica e vida útil. |
| R7 | `8dec022` | Não serializar/mesclar cache do driver sem pipelines novos e limitar arquivo a 1024 MiB. Não limita RAM/VRAM durante a execução. A correção de inicializar o saver após a lista dos workers já foi mantida no Android; não é novidade. |
| R8 | `028f31c` | Pular clear da apresentação só quando o blit cobre todo o destino. Não copiar swapchain desktop: adaptar AHB e Bloodframe, mantendo limpeza das barras 16:9 e filtros corretos. |
| R9 | `b37381d` | Perfil de tradução com tamanho real do subgroup compute e requiredSubgroupSize apenas em stages permitidos pelo driver. É correção de consistência Vulkan, porém fazia parte do lote revertido; precisa de validação isolada com novo cache. Não forçar wave64 universalmente. |
| R10 | `e51a229` | Alinhar explicitamente a carga de ballot a 8 bytes. Não tratar como ganho/fix visual independente: no backend inspecionado EmitLoadSharedU64 já faz offset >> 3, e (offset & ~7) >> 3 dá o mesmo índice. O documento PC TEST_PR227_MAIN confirma essa limitação. |

## Renderer v2: ausências reais, com dependências maiores

| ID | Mudança | Decisão |
|---|---|---|
| V1 | PR #497, `ClampHugeRead`: bindings somente leitura >1024 MiB passam a janela de 64 MiB; aplicado também aos caminhos native material/pass/recorder | Pode-se estudar adaptação ao rasterizer atual, mas não aplicar corte cego. O próprio código informa que leituras fora da janela devolvem zero por robust buffer access. Identificar shaders/tabelas/faixas efetivamente usadas no nosso eboot; não cortar writers ou o heap menor usado pelos draws. O ganho de ~2,4 GB é do PC. |
| V2 | PR #501, orçamento automático de texturas do v2 | Não confundir com o budget dinâmico que já temos. V2 calcula orçamento das texturas considerando arena, staging e demais usos; seus números de headroom de placa dedicada não são preset para RAM compartilhada de celular. Adaptável como política, mediante accounting integrado. |
| V3 | Native passes / materiais e gravação por segmentos | Não são uma flag implementada no APK: `port/gpu_v2` nem participa do build Bionic. Exige novo backend, caminhos de apresentação Android, sincronização, tradutor/driver, e hooks compatíveis com guest x86-64 sob FEX e host ARM64. Endereços US 1.07 não podem ser reaproveitados em outra região. |
| V4 | PR #490 e #548: proteção contra imagens mortas nos templates e reaprendizado | Dependem de V3. Sem native templates, não há o bug específico de template ficar para sempre em fallback ou usar image view morta. Trazer junto se V3 for implementado, não antes como patch isolado. |
| V5 | AOT de compute e jobs gráficos do v2 | Potencial para menos compilações durante gameplay; exige formato e coletor do v2, precompile/caches próprios, compatibilidade por build/driver e orçamento de workers/RAM. Não distribuir cache binário de outra GPU e não prometer eliminar todo shader stutter. |

## O que já existe e não deve entrar como novidade

- VK_EXT_memory_budget e consulta de budget/uso; Bionic não fixa 3500 MB no boot normal.
- GC que move imagens não descartáveis para o fim da LRU e limita a busca a 256 imagens.
- Write-back de GC antes da liberação da imagem/proteção das páginas.
- Cache persistente do driver, compilação compute em workers e caches de shaders/SPIR-V.
- Readbacks deferred/precise e várias proteções específicas Bionic; padrão de jogo Relaxed.
- LTO, apresentação AHB, ajustes de CPU/worker, descriptor templates e Bloodframe Android.
- Correções Android em ExpandImage (proteger destino e refrescar fonte) e hash de imagem MaybeCpuDirty.
  O código PC correspondente ainda possui versões mais antigas em alguns desses trechos.

Não substituir arquivos completos só porque o PC é mais novo: isso removeria essas proteções,
os caminhos ARM64 e as correções de apresentação/coerência que não estão no desktop.

## Exclusões

- Perfis NVIDIA/AMD dedicadas, InputPlumber, Win32/GDI, empacotamento ICU/Qt e afinidade em core 14: não são presets Android.
- `TLG_ASYNC_SHADERS=1` com draws pulados: já existe; reduz espera sacrificando desenhos temporariamente, não é nova correção segura de shader stutter.
- `TLG_LAGGED_READS`/stale reads: podem entregar estado GPU antigo. Além do risco visual/lógico, não é o caminho padrão Relaxed.
- Memória unificada por importação host: exige extensão, alinhamento e coherência do driver. RAM compartilhada não implica suporte Vulkan; o PC relata até piora de waits/FPS. Não recomendar por padrão.
- SPIR-V reconvergence/float/contraction e readback fixes Android não devem ser substituídos por defaults PC em bloco.
- PR #541 / #226 / #273 e trace de stalls: diagnóstico, não correção demonstrada de tela branca ou desempenho.
- PR #617: câmera/mouse, ultrawide/HUD e karma são trabalho funcional separado, não melhoria provada de desempenho do Android.

## Ordem sugerida e validação

1. Reintroduzir individualmente R4, R5, R6, R7, R3; depois R1/R2 e R8.
2. N4 e N9 como políticas de memória com fallback; N3 com limite de staging adaptado.
3. N1 e N2 em testes gráficos separados; R9 isolado. Renovar chave de shader e medir após recompilação.
4. N5/N6/N7/N8 opcionais e avaliados por logs, sem novos defaults silenciosos.
5. V1 somente após validação dos acessos; V3/V4/V5 são projeto separado.

Usar mesmo save/cena: câmera parada e movimento; grafite; fumaça/fogo; noite/faróis;
interiores; mudança de área; abrir/fechar menu; 16:9 e fullscreen; Bloodframe ligado/desligado.
Comparar picos de frame time, compilações, GC/write-backs, memória residente/staging,
timeouts e imagens. FPS médio sozinho não valida stutter nem correção visual.

## Fontes

- https://github.com/HayatoG/iss_pc/tree/7a48167dcba8b25ef66fb3f99d712415f225790f
- https://github.com/HayatoG/iss_pc/releases/tag/v1.0
- https://github.com/HayatoG/iss_pc/pull/402
- https://github.com/HayatoG/iss_pc/pull/497
- https://github.com/HayatoG/iss_pc/pull/501
- https://github.com/HayatoG/iss_pc/pull/547
- https://github.com/HayatoG/iss_pc/pull/548
- https://github.com/HayatoG/iss_pc/blob/7a48167dcba8b25ef66fb3f99d712415f225790f/docs/TEST_PR227_MAIN.md
- Histórico local Android: docs/BIONIC_PC_PERFORMANCE_PORT.md e docs/RELEASE_NOTES_v0.1.md.
