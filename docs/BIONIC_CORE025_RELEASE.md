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

A etapa inicial registrou evidências e método sem alterar o código de execução.
A adaptação posterior de timer slack está descrita ao final e ainda não tem
medição de FPS no aparelho. O APK de `7c1ff82`
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
rejeita SHA-256 desconhecido, confere 73 instruções da cadeia, reconstrói o
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


## Continuação com DMA desativado: ajuste de esperas curtas

O usuário confirmou que **DMA não foi usado nos testes**. Essa condição foi
mantida. O mecanismo de buffers mapeáveis não exige o modo shader DMA, mas
sua utilização real no aparelho continua não medida; não é uma causa provada
da diferença de FPS. A investigação prosseguiu para outras flags do release.

Foi encontrada uma segunda otimização específica de compatibilidade, que não
depende daquele caminho nem de DMA:

- O mesmo prefixo `CUSA0000` e nono byte ASCII `4` são comparados em
  `0x13aeac0–0x13aead0`. O branch vai a `0x13aeb64`, define 1 e grava a
  flag **+24** em `0x13aeb6c`. Na instância, `3124 + 24 = 3148`.
- O wrapper de sono em `0xa83828` valida o timespec e calcula a duração
  total em nanossegundos. Em `0xa8388c–0xa8389c`, só entra no caminho de
  ajuste para duração **até 999999 ns**, incluindo zero.
- `0xa8393c` consulta exatamente o byte `settings +3148`. Um byte TLS evita
  repetir o ajuste na mesma thread. `0xa83964–0xa83980` chama
  `prctl(PR_SET_TIMERSLACK=29, 1, 0, 0, 0)`.
- Em seguida, `0xa83994` chama `AccurateSleep`; essa função continua usando
  `nanosleep`, inclusive para duração zero (`0x78dd7c`). Não troca a duração
  do jogo por 1 us nem depende de o patch externo de sleep ter sido aplicado.

Timer slack é a tolerância que o kernel pode usar para agrupar despertares.
Reduzi-lo não garante acordar no prazo exato e não elimina atrasos do
escalonador. É distinto de afinidade, prioridade ou busy waiting. Referência:
[Linux PR_SET_TIMERSLACK](https://man7.org/linux/man-pages/man2/PR_SET_TIMERSLACK.2const.html).

Na máquina host da análise, um diagnóstico com 300 amostras por caso e
`nanosleep` via ctypes observou mediana de ~59,6 us para pedido de 1 us
com slack de 50000 ns, contra ~15,2 us com slack de 1 ns. Para pedido zero,
~58,7 contra ~15,1 us. Esses números incluem overhead Python/ctypes e são
**somente um diagnóstico host do mecanismo**, não medição do Snapdragon,
nem previsão de FPS. O valor original foi restaurado no processo do teste.

### Adaptação aplicada no port

`runtime_prepare_short_sleep` em `port/src/runtime_kernel.c` aplica o ajuste
uma vez por thread, limitado a Android/Bionic e a pedidos abaixo de 1 ms.
O caminho real `sleep_ns` chama essa função antes de dormir; as bibliotecas
vendorizadas também passam por ela em `Libraries::Kernel::sceKernelUsleep`.
Sono pedido, tratamento de EINTR e retorno de erro do loop permanecem os mesmos.

`TLG_SHORT_SLEEP_SLACK=0` recupera a política anterior para A/B. Se `prctl`
falhar, a thread continua com a política de sono do sistema; não há falha
fatal nem repetição da syscall em todo sleep. O errno anterior é preservado.
O log `BionicSleep` registra uma vez por thread o valor anterior/atual,
resultado da configuração e primeiro pedido. Ele permite verificar se o
jogo realmente alcançou o caminho e se o Android aceitou o ajuste.

Os testes compilam as funções C de produção com syscalls controladas:
limiares incluindo zero e 1 ms, uma aplicação por thread sob concorrência,
falha/override, errno, pedido original e continuação após EINTR. Também
verificam que o caminho não-Bionic não modifica timer slack. Nenhum ganho de
FPS é declarado antes do teste do APK no aparelho.

### Outras flags ativas verificadas

A leitura da tabela completa de compatibilidade para CUSA00004 também levou
a flags de afinidade em `0x11d5e7c`, `0x11d5fd4`, `0x11d6134`. O log do
release identifica o mecanismo como `Big-core affinity target=GpuComm`.
Foi apenas identificado, não adaptado, conforme a restrição do usuário.

O byte `+3126` eleva o modo de readback ao mínimo Relaxed, sem transformar
Relaxed em Precise (`0x13abfd0–0x13abff0`). Como os testes já usam modo 1,
essa regra não oferece uma otimização adicional. As flags usadas pelo helper
`0x13af1b4` pertencem a outros IDs; não foram transplantadas para inFAMOUS.


## Continuação: contração aritmética em fragment shaders

A flag interna `+9`, ainda sem equivalente no nosso port, foi rastreada até
seu consumidor. No release ela é escrita com valor 1 no final do inicializador
(`0x13af194–0x13af198`), inclusive no caminho CUSA00004. Não depende de DMA.
Em `0x12640a4–0x12640bc`, o compilador carrega `settings+3133` e grava
`PipelineCache+456`: o perfil começa em `+400`, portanto é `Profile+56`.
O construtor do contexto SPIR-V guarda a referência ao perfil em `+280`
(`0x115e0f0`). Esse rastreamento evita confundir o byte `+56` do objeto de
logging, usado por muitas funções, com o byte `+56` do perfil.

O helper `0x11552f4–0x1155380` e suas versões inline nos emissores de FP
consultam esse campo. A decoração `NoContraction` (valor SPIR-V 42) só é
omitida quando todos os seguintes testes passam:

- Flag do perfil ativa (`0x1155300–0x1155310`).
- Hardware stage 0, fragment (`0x1155318–0x115531c`).
- Ausência do componente zero do export de profundidade: bit 0 em
  `Info+7770` (`0x1155324–0x1155328`). O mesmo campo seleciona a definição
  da saída FragDepth, BuiltIn 22, em `0x116240c–0x116241c` e `0x11625ac`.
- No máximo um alvo de cor exportado: oito bytes `Info+7762..+7769`
  viram bits por comparação com zero, máscara de pesos
  `[1,2,4,8,16,32,64,128]` (constante `0x2a64de0`) e soma horizontal;
  `mask & (mask - 1)` testa se há mais de um bit (`0x115532c–0x1155358`).
  A rotina de definição das saídas de cor também usa a base `7762`
  (`0x1161fc8`). Contam-se alvos, não componentes RGBA.

Em todos os demais casos chama Sirit Decorate com 42
(`0x115535c–0x115536c`). Zero alvos também satisfaz a regra do release.
O port e a fonte pública fixada decoravam essas operações incondicionalmente;
portanto é uma diferença efetiva do executável distribuído, não apenas uma
opção encontrada por nome ou uma inferência a partir da árvore pública.

### Adaptação

O perfil Bionic habilita a regra acima e o contexto calcula a elegibilidade
uma vez por shader. Os emissores deixam de impor `NoContraction` apenas no
caso elegível. O driver pode formar FMA em cadeias multiply/add; isso não
obriga fusão e não ativa FP16 nem flags gerais de fast math. Outros estágios,
MRTs e shaders com export de profundidade mantêm a decoração.

`TLG_FRAGMENT_CONTRACTION=0` restaura a emissão anterior. O modo aparece no
log de inicialização (`GPU: fragment contraction on/off`). O novo campo faz
parte da comparação serializada de `Shader::Profile`, incluindo a troca A/B;
o campo da política usa uma palavra de 32 bits para ampliar o tamanho do
perfil, sem interpretar padding antigo como bool. A versão do binário de
shader sobe de 9 para 10 para rejeitar metadados/SPIR-V anteriores.
Não é preciso apagar saves. A primeira sessão recompila shaders; comparar
sessões já aquecidas de cada modo, no mesmo trecho, driver e configurações.

A contração muda arredondamento de algumas expressões: precisa conferir a
imagem no aparelho, particularmente pós-processamento/iluminação. A regra
restrita preserva a política mais conservadora do release para profundidade
e múltiplos alvos, mas não comprova ausência de regressão visual no port.
Também não comprova aumento de FPS: os testes host verificam a política e a
emissão da decoração com sink simulado, não executam shaders no Adreno.

A auditoria reproduzível passou de 44 a 73 instruções verificadas e confere os
oito pesos da constante. Os testes novos compilam o header/perfil reais e o
helper `Decorate` real com um sink de SPIR-V: percorrem todas as 256 máscaras
de alvo e combinações de habilitação/estágio/profundidade, verificam o
retorno da instrução, presença/ausência da decoração, override, isolamento
não-Bionic e diferença de perfil para invalidação do cache.

Validação final: 71 testes host passaram (39,120 s); auditoria do APK original
e do ELF extraído concorda, 73 instruções e constante de pesos verificadas.
Compilação Android e desempenho/qualidade visual no aparelho ainda dependem
do workflow e do teste do APK.
