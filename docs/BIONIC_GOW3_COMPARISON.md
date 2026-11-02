# Comparação com GoW3 Bionic — 18/10/2026

Referências fixadas para permitir repetir a comparação:

- [GoW3 experimental-bionic, f8a774887263](https://github.com/erickmendes22/gow3_android_daedalus_fork/tree/f8a7748872631015d9d3389ee12ebaf03726f35a).
- inFAMOUS antes desta adaptação: `06021e211fce87ad44634c778b4ab4f0ab297dad`.

O código do GoW3 tem diferenças de política de CPU que ainda faltavam aqui.
Isso identifica candidatos para teste; não demonstra que o GoW3 tenha medido
ganho com essas políticas nem que elas eliminem a diferença para o Bachata.

| Área | GoW3 Bionic | inFAMOUS anterior, 8 CPUs | Decisão |
| --- | --- | --- | --- |
| Preparação especulativa | 0 workers no Android | 4 workers + scanner | Adaptar: 0 no Bionic nativo |
| Execução de draws | Caminho direto no Android | Pipeline com consumidor extra | Adaptar: caminho direto no Bionic nativo |
| Compilação assíncrona | 2 compiladores, 1 capturador | 4 compiladores, 2 capturadores | Adaptar o orçamento móvel |
| Prioridade de captura | Baixa no Android | Alta | Adaptar prioridade baixa |
| Buffer stream | Device por padrão fora de NVIDIA | Host cached | Manter; precisa comparar os tipos de memória escolhidos pelo VMA no Adreno e medir CPU/GPU |
| Cópias paralelas | Mesmo mecanismo de batching | Já presente | Sem port necessário |
| Build GPU | RelWithDebInfo, LTO desativado | Release, LTO ativado | Manter inFAMOUS |
| Reciclagem de recursos | Código anterior de pools e staging | Correções de conclusão/reuso e GC limitado | Manter inFAMOUS |
| Cache Vulkan persistente | Sem o mecanismo equivalente no arquivo comparado | Blob por GPU/driver, gravação periódica | Manter inFAMOUS |
| Texturas | Hash parcial no caminho MaybeCpuDirty | Hash do mesmo intervalo usado na referência | Preservar correção de flicker |
| Predicação | Implementação adicional no GoW3 | Comando ainda não implementado | Não portar parcialmente; depende de semântica PM4, contadores e suporte Vulkan, e falta evidência desse custo no log 21 |

As mudanças estão limitadas a `__ANDROID__ && ISS_ANDROID_BIONIC`; os padrões
de Glibc e desktop continuam iguais. Não alteram afinidade, driver, resolução,
readback Relaxed, saves ou os consertos de texturas e sincronização.

Com preparação desativada, `DrawPreparation::Build` retorna antes de copiar
o command buffer, contar draws e alocar resultados. Não se trata apenas de
criar workers que depois ficam parados. O caminho direto também evita os
pacotes e as transferências para o consumidor do draw pipe. As proteções de
ownership continuam disponíveis quando esse pipeline é explicitamente ativado.

## Validação e comparação no aparelho

Os testes de host compilam as políticas reais com e sem as macros Bionic,
conferindo defaults em 2/8/32 CPUs, limites, valores inválidos e overrides.
Os testes de reuso de recursos, limite de submissões, constant ring e vida útil
das preparações continuam na suíte. Isso não substitui gameplay em Adreno.

O log 21 confirma os quatro workers de preparação e scanner ativos. Suas
últimas janelas continuam abaixo do Bachata mesmo com zero compilações;
reduzir compiladores sozinho não explica esse trecho. O principal candidato
desta adaptação é retirar a preparação e os handoffs extras de cada draw.

Comparar o mesmo save, percurso, resolução e estado térmico, com cache aquecido,
contra o APK de `06021e2`. Os novos logs identificam `draw execution: direct`,
zero scanner/workers e `2 compile, 1 capture`. Os tempos de CPU de threads
acumulados não são tempos exclusivos nem podem ser somados para inferir FPS.

Overrides no processo nativo permitem isolar a diferença:

| Experimento | Variáveis |
| --- | --- |
| Novo padrão | Sem overrides |
| Somente preparação anterior | `TLG_PREP_WORKERS=4` |
| Somente draw pipe anterior | `TLG_DRAW_PIPE=1` |
| Ambos anteriores | `TLG_PREP_WORKERS=4 TLG_DRAW_PIPE=1` |
| Orçamento de compilação anterior | `TLG_GPU_COMPILER_WORKERS=4` (captura continua em 1) |

Não há promessa de ganho de FPS antes dessa medição. Em particular, retirar
paralelismo útil pode reduzir throughput; os overrides preservam o A/B sem
remover a implementação nem suas correções de corrida.
