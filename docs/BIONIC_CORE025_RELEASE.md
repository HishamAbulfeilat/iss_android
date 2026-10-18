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
  A análise adicional identificou a origem como uma flag do bloco interno de
  compatibilidade por jogo; não é a opção de menu Direct Memory Access.
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
**A análise adicional abaixo confirma a flag habilitada para CUSA00004.**
Isso ainda não comprova sucesso do mapeamento ou frequência de uso no aparelho.
É necessário verificar os tipos de alocação usados pelo jogo e todos os
produtores dos ticks antes de adaptar. No nosso modelo, arenas
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
rastreamento de escritas por página e habilitada por ID para CUSA00004. Agrupamento e retenção de staging já
existem no nosso core e não justificam uma nova revisão de runtime isoladamente.

Esta revisão registra evidências e método; não altera configuração nem código
de execução e não afirma corrigir a diferença de FPS. O APK de `7c1ff82`
continua sendo o que testa a adaptação de cópias host do Bloodborne. Para
implementar o caminho mapeado com segurança ainda faltam propriedade das
alocações, visibilidade GPU→host e cobertura dos produtores de ticks. A
elegibilidade por ID foi confirmada na investigação adicional abaixo.


## Investigação adicional após os logs 25 e 26

### CUSA00004 habilita o caminho; a flag foi rastreada até sua origem

A hipótese de uma configuração não identificada foi resolvida:

1. `Run`, em `0x13a46b4–0x13a46c0`, passa tamanho, dados do serial e o
   bloco da instância de settings em `+3124` para `0x13ae324`.
2. O inicializador desse bloco compara vários IDs e escreve suas flags.
   `0x13ae33c`, `0x13ae348` e `0x13ae354` montam `CUSA0000` em `x22`.
3. `0x13ae910` compara os primeiros oito bytes; `0x13ae914–0x13ae918`
   compara o nono com ASCII 52 (`4`). O branch de igualdade em `0x13ae91c`
   vai a `0x13ae9b4`, onde o caminho escreve **1** na flag `+17`
   (`0x13ae9b8`, `0x13ae9c4`). Portanto o ID é **CUSA00004**.
4. `3124 + 17 = 3141`: esse é precisamente o byte que o construtor do
   buffer cache lê em `0x11ecbfc` e guarda em `cache + 13921`.
5. O helper de readback consulta `cache + 13921` em `0x11fe478–0x11fe488`.
   Depois verifica tick e mapeamento; se ambos permitem, usa o callback
   de espera do intervalo. Mapeamento indisponível mantém o fallback.

**A elegibilidade é confirmada estaticamente para o jogo, não observada em
execução.** Não foi recuperado o nome original do campo em C++; o seu papel
é determinado pelo consumo no código de máquina. Ele não é a opção
`GPU.direct_memory_access_enabled`: seu valor vem da comparação de serial,
independentemente daquele Setting. O diagnóstico recente com Direct Memory
Access false não permite descartar este outro mecanismo.

A ferramenta `tools/android/runtime/core025_release_audit.py` lê o ELF ou o APK,
rejeita SHA-256 desconhecido, confere 24 instruções da cadeia, reconstrói o
ID e calcula o destino do branch. Não executa o core. Exemplo:

```sh
python tools/android/runtime/core025_release_audit.py --apk /caminho/bachatas4-0.2.5-release.apk
```

A saída verificada foi preservada em `docs/CORE025_RELEASE_EVIDENCE.json`.
A desmontagem completa foi feita com LLVM; a ferramenta reduzida deixa as
instruções da conclusão principal verificáveis sem dependência do LLVM.

### Alocação e rastreamento: como o release torna o caminho possível

O release usa buffers por intervalo, em vez das nossas arenas sparse:
`0x11f8b54–0x11f8b68` constrói um Buffer com MemoryUsage 0 (DeviceLocal).
O construtor chama a alocação, consulta propriedades físicas e guarda
host-coherent / host-visible (`0x11e9e5c–0x11e9e94`). `0x11e9f94` usa um
mapeamento existente ou tenta obtê-lo sob demanda quando host-visible. Essa
caminhada chega à camada VMA em `0x1229808`; há retorno de falha e o
readback mantém a cópia convencional. A tabela de flags de VMA em
`0x210b910` é `{0, 0x404, 0x804, 0x404}`: não há evidência de uma troca
universal de DeviceLocal para memória host pelo simples fato de ativar a flag.

`0x11ee3f4` registra o tick corrente do buffer. O fluxo seguinte limita o
vetor por página a buffers de **até 64 MiB**, com índice por **4 KiB**;
buffers maiores preservam o tick coarse. O vetor é inicializado com um
tick conservador antes de receber atualizações por intervalo. Atualizações
inline equivalentes aparecem em caminhos de cópia/escrita e na junção de
buffers (`0x11f3b44–0x11f3c2c`, `0x11f9274–0x11f9360`, entre outros).
Não se deve confundir o helper standalone com a cobertura total: o compilador
inlined os produtores em múltiplas funções.

No nosso core, `arena_memory_type_index` já escolhe um tipo DeviceLocal,
mas `Backing` guarda apenas VkDeviceMemory e offset. Não expõe um ponteiro
CPU das alocações residentes. Isso não significa que o hardware não permita
mapear: significa que a nossa camada não implementa esse acesso. As arenas
podem ter gigabytes; copiar o vetor de ticks de um Buffer pequeno para cada
arena inteira também mudaria custo e precisão.

Uma adaptação adequada exige:

- Mapear/invalidate **as alocações residentes**, traduzindo cada intervalo
  através de Backing e seu offset; não presumir um ponteiro contíguo da arena.
- Manter alocações/mapeamentos vivos enquanto houver leitores; contemplar
  rollback de alocação, migração, unmap e desligamento.
- Rastrear os ticks de todos os produtores, incluindo uploads, fill/copy,
  shader writes e image→buffer. Tick de uso/leitura não substitui tick de escrita.
- Garantir visibilidade GPU→host antes de esperar o tick já submetido. Esperar
  um número antigo ou invalidar cache CPU isoladamente não estabelece toda
  a dependência de memória. Referência: documentação Khronos de
  [sincronização](https://docs.vulkan.org/spec/latest/chapters/synchronization.html)
  e [invalidate](https://github.com/KhronosGroup/Vulkan-Docs/blob/main/chapters/memory.adoc).
- Impedir leitura concorrente com escritas GPU posteriores e preservar bytes
  CPU modificados; integrar invalidação por página aos readbacks diferidos.

O motivo para priorizar esse caminho é o que ele evita: no nosso
`StartDeferredReadback`, `runtime.CopyBuffer` e `scheduler.Flush` criam um
novo trabalho antes da espera. No release, um intervalo mapeável cujo tick
já foi submetido pode aguardar a escrita anterior e publicar sem essa cópia
nem forçar submissão do command buffer aberto. O ganho depende da frequência
real desses casos; não foi medido. Agrupamento de staging não obtém o mesmo efeito.

### O que acrescenta o log 26

Logs 25 e 26 mostram a mesma data de compilação (`18/10 17:54:58`), configurações
observáveis e host-copy tracking ativo. O log não imprime SHA do executável;
portanto isso identifica a mesma revisão aparente, não prova identidade binária.

| Amostra | Primeiras oito janelas de gameplay | Duração | Média gameplay completa |
| --- | --- | --- | --- |
| Log 24 | 35,984 FPS | 40,10 s | 36,615 FPS em 45,12 s |
| Log 25 | 35,921 FPS | 40,12 s | 35,921 FPS em 40,12 s |
| Log 26 | 37,655 FPS | 40,37 s | 38,231 FPS em 45,39 s |

Médias ponderadas por `window_s`, excluindo as primeiras três janelas de
boot/menu/transição em cada arquivo. Alinhamento por janela não assegura
mesma câmera, trajeto ou estado térmico. O log 26 supera o 25 em ~4,8% no
recorte, mas não há mudança de código observável entre eles a que atribuir
esse ganho. Os pipelines também estavam mais aquecidos no segundo teste:
warm-up de 0,51 s contra 0,75 s e tempo de builds de 0,057 contra 1,266 thread-s.

O fallback stale foi 136/(11033+136) = **1,218%** no log 26,
contra 288/(10376+288) = **2,701%** no 25. Esses contadores têm janelas
próprias e incluem uma transição; não são exatamente o recorte da tabela.
A espera acumulada do gravador no log 26 fica entre ~237 e 629 ms por janela
de gameplay; readback continua aparecendo como espera relevante. Nenhuma
dessas somas por threads identifica isoladamente o gargalo do frame.
Não há medição de execução GPU nem evidência suficiente para afirmar que a
última adaptação aumentou o FPS, ou para confirmar ausência de flicker.
