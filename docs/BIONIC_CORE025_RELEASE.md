# Auditoria do binário Bachata 0.2.5

Análise estática em 18/10/2026; referência inFAMOUS `7c1ff82`.

## Identidade e alcance

APK fornecido: `bachatas4-0.2.5-release(1).apk`, 211009680 bytes.
SHA-256: `1df49463f396df8b6f042230bdc1bb9f948b8e69894c378915920875c40ab0ee`.
O `assets/runtime/runtime.zip` contém um executável Linux AArch64 PIE
`host/shadps4-arm64-fex`, 75323616 bytes, intérprete glibc.
SHA-256 confirmado contra o manifesto:
`96a31d08f6cc12446f4245e96e6da34072df4cdcb2665979e54bc6a50998e3d2`.
Build ID: `b505e6d4f2dbf557e2d9577ca9029281d685fb96`.
Runtime: `runtime-b7db79837e56`; FEX
`f2b679f6028ce1c38875233aecfcf5d3f8ebecec`.

Esse release não nomeia o ELF `shadps4-arm64-025`. Já o diagnóstico
`BS4-20261018-81874ACC` é do APK 0.2.6 modificado, runtime
`50c8b90b09b433ab0767de44af2d0731cb0748b7`, executável com sufixo `-025`.
O SHA `9addfd4c...` no diagnóstico identifica `GUEST_RUNTIME_BUILD`, não
comprova a identidade do ELF host. Não se deve tratar o runtime revision,
o hash guest e o hash ELF como intercambiáveis. A identidade entre os dois
ELFs não foi demonstrada. Esta auditoria descreve o release 0.2.5 recuperado.
O APK também tem `libbachata_native_runtime.so`; não foi usado como
substituto do executável glibc que interessa ao comparativo Bachata/Turnip.

A fonte pública consultada é `zenithblue-oss/shadps4-arm64` em
`be6bc2e9c60799e071dd2fafa6216e8d80ec619c`. As diferenças abaixo são contra
essa árvore, não contra uma fonte necessariamente correspondente ao release.

## Método e limites

ELF sem símbolos internos: seções, dynamic imports, relocações e tabela
`.eh_frame_hdr` preservados. Desmontagem AArch64 com LLVM 20, referências
ADRP/ADD e chamadas BL verificadas no fluxo das funções. Os endereços são
VAs relativos ao ELF, anteriores ao relocation/ASLR. Limites obtidos da
tabela de unwind são aproximados; nomes de helpers sem símbolo são
interpretações do fluxo, não símbolos recuperados.

Strings foram usadas para localizar funções, não para declarar recursos
ativos. Não houve execução do ELF no aparelho, medição de GPU nem validação
de FPS deste release durante a auditoria.

## Diferenças confirmadas

| Recurso | Evidência no release | Situação no inFAMOUS Bionic |
| --- | --- | --- |
| Download agrupado | `0x11efd08` reúne intervalos e chama helper `0x11fe6b0`; limite de lote `0x02000000` (32 MiB), em `0x11f02bc` e `0x11f030c` | `CollectDownloads`, `DownloadMemory` e readback diferido já agrupam intervalos |
| Buffer de download reaproveitado | `0x11febc0–0x11fec4c`: testa ponteiro/capacidade retidos e aloca somente sem capacidade suficiente | Staging pool já reutiliza blocos; modelo de vida útil diferente |
| Prefetch de readback | `0x11fe144–0x11fe168` arredonda início/fim relativos ao buffer a 64 KiB; falha tenta o intervalo original em `0x11fe2fc` | Janela configurável, padrão 512 KiB, limitada à arena |
| Ticks por página e leitura mapeada | `0x11fe3d0` consulta vetor indexado por página de 4 KiB, reduz ao maior tick, verifica flag e obtenção do mapeamento antes de fornecer callback de espera | Arenas sparse e cópia para staging; não existe esse mesmo caminho direto |
| Métricas detalhadas | `ReportReadbackMetrics` em `0x11ed9e0`; contador de requests/bytes/prefetch/batches/copies/allocations/reuses/retained e tempos de cada fase | `BionicReadbacks` mede nosso modelo; comparar parcelas equivalentes, não nomes |
| Publicação alternativa GPU | `StorageImageSync`, código ausente da árvore pública consultada | Não é prova de ganho para este jogo: whitelist do release exclui `CUSA00004` |

### Leitura mapeada: o candidato novo mais relevante

No helper `0x11fe3d0`:

- O vetor do buffer em `+168..+176` guarda valores por página de 4 KiB;
  o fluxo escalar e NEON seleciona o maior valor para o intervalo solicitado.
- Em `0x11fe480` é consultada a flag do cache em `+13921`; o construtor a
  recebe de um campo de configuração em `+3141` (`0x11ecbfc–0x11ecc04`).
  O nome e o valor padrão dessa configuração não foram recuperados.
- Com a flag ativa, tick anterior ao tick corrente e mapeamento disponível,
  fornece um callback para `DownloadBufferMemory` em `0x11fe4ec`.
- `0x11e9f94` obtém o ponteiro de mapeamento existente ou tenta mapeamento
  pela camada de alocação; pode retornar nulo. Não basta ser memória unificada.
- O callback `0x11fe5fc` chama a rotina de espera `0x12f8a48` com o tick do
  intervalo. Essa rotina só força submissão quando o tick solicitado ainda
  não é anterior ao corrente; depois aguarda o semáforo.
- `0x11efd54–0x11efd68` obtém mapeamento somente quando há callback.
  No helper de lote, o ponteiro mapeado em `0x11fe6e4` seleciona o ramo
  `0x11fe730`: executa callback, invalida memória (`0x11fe790`) e publica
  intervalos a partir do mapeamento. Sem mapeamento usa staging, grava
  cópia e espera o tick corrente em `0x11fee50–0x11fee5c`.

Portanto há uma otimização concreta que a fonte pública de `ReadMemory` /
`DownloadBufferMemory` consultada não representa: para buffers elegíveis,
pode evitar uma nova cópia e esperar pela última escrita relevante.
**Sua existência não demonstra que esteja ativa para inFAMOUS/Adreno.**
É necessário identificar a configuração, tipos de alocação usados pelo jogo,
e todos os produtores dos ticks antes de adaptar. No nosso modelo, arenas
sparse não oferecem um ponteiro contíguo mapeado equivalente. Esperar um tick
antigo sem rastrear todas as escritas ou ignorar invalidação/barreira Vulkan
pode publicar dados antigos e reintroduzir o flicker. Não basta mudar `Finish()`.

O ramo de publicação ainda salva/restaura intervalos CPU registrados em
estruturas do cache. Isso reforça que o caminho faz parte de um modelo de
coerência maior; a equivalência de todas as escritas CPU não foi demonstrada.

### O que não explica uma vantagem ativa por padrão

`StorageImageSync` em `0x12ecd20` reconhece os IDs `CUSA01623`, `CUSA01715`
e `CUSA01740` antes de selecionar a publicação alternativa. Outros IDs seguem
para modo zero e log de publicação síncrona em `0x12ece98–0x12ecf30`.
O diagnóstico recente do próprio inFAMOUS também informa
`GPU buffer publication mode=disabled; using synchronous publication`.
Não ativar esse caminho supondo que o Bachata já o utiliza neste jogo.

O pool `FHDImageStagingPool` em `ObtainBufferForImage` exige tamanho de pelo
menos 7 MiB e `BACHATA_STAGING_FHD_RING` definido/verdadeiro
(`0x11f750c–0x11f7520`). A fonte pública já contém esse mecanismo e documenta
corrupção gráfica ao forçar o caminho para todos os FHD no Turnip. Não é
uma otimização universal omitida da comparação anterior.

`BACHATA_STAGING_TICK_LAG`, `BACHATA_BUFFER_CACHE_TICK_LAG` e flags strict
são opções condicionais: `0x1206fd0` inicializa lags/flags em zero e só os
altera com variáveis válidas. Strings dessas opções não provam uso no release.

Os tamanhos do construtor (`0x11ec794–0x11ec7ec`) são staging upload
512 MiB, stream 64 MiB, download 32 MiB e device local 128 MiB; staging
upload pode cair a 64 MiB em condição de dispositivo/Mali. Esses valores já
aparecem na fonte pública. Não há evidência para aumentar indiscriminadamente
nossos pools por causa do número 512 MiB.

## Consequência para a investigação

A comparação pública anterior não cobria integralmente o release. O achado
novo prioritário é a publicação direta de buffers mapeáveis, associada a
rastreamento de escritas por página. Agrupamento e retenção de staging já
existem no nosso core e não justificam uma nova revisão de runtime isoladamente.

Esta revisão registra evidências e método; não altera configuração nem código
de execução e não afirma corrigir a diferença de FPS. O APK de `7c1ff82`
continua sendo o que testa a adaptação de cópias host do Bloodborne. Para
implementar o caminho mapeado com segurança ainda faltam configuração ativa,
propriedade das alocações e cobertura dos produtores de ticks no release.
