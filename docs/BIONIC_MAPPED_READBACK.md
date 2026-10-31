# Readback mapeado para as arenas sparse do inFAMOUS Bionic

## Por que esta alteração

O log 32 (`586172f`) eliminou 90,84% dos reenvios de vertex input, mas
continuou no patamar de ~37 FPS. Nos primeiros oito intervalos de gameplay
(40,10 s), houve 3762 esperas de semáforo, ~93,82/s, com 21.488,89 ms
**agregados** de espera host. Os perfis apontam predominantemente para os
readbacks diferidos e seu fallback síncrono. Esse total pode sobrepor
esperas de threads; não é tempo de GPU por frame nem deve ser somado à CPU.

A auditoria do release Bachata 0.2.5, documentada em
`BIONIC_CORE025_RELEASE.md`, identificou uma diferença concreta: o ramo
`0x11fe3d0` pode fornecer um mapeamento e esperar o último tick de escrita
do intervalo; `0x11fe730` publica esses dados sem uma nova cópia GPU. A flag
está habilitada estaticamente para CUSA00004, independentemente da opção
DMA. A auditoria não prova quantas vezes o caminho é usado no aparelho.

O port anterior sempre pedia staging, gravava uma cópia e submetia o tick
corrente, inclusive para dados escritos em uma submissão anterior. Assim,
o guest podia esperar trabalho corrente não relacionado ao intervalo.
Esta revisão adapta o mecanismo às nossas alocações sparse; não substitui
as arenas por buffers do modelo público do Bachata.

## Caminho novo

1. `Runtime::AccessBuffer` comunica todas as escritas registradas em arenas
   sparse, com endereço guest, tamanho e tick. Um rastreador por páginas de
   4 KiB guarda o maior tick. Árvores lazy por blocos de 64 MiB permitem
   marcar uploads de gigabytes sem iterar cada página. A migração de arenas
   não perde a informação porque a chave é o endereço guest.
2. Uma submissão que contém essas escritas termina com uma barreira
   `ALL_COMMANDS/MEMORY_WRITE → HOST/HOST_READ`, antes de finalizar o command
   buffer e sinalizar o semáforo. Ocorre no máximo uma por submissão com
   escritas relevantes; o caminho desligado não solicita essa barreira.
3. `PrepareMappedReadback` verifica os intervalos realmente coletados, não
   toda a janela de prefetch. Primeiro tenta uma janela mapeada de 64 KiB,
   como a do release, para que uma escrita recente em outra parte dos 512 KiB
   não desqualifique dados antigos. Exige tick conhecido **anterior ao corrente**,
   cobertura completa de backing e mapeamento bem-sucedido. Traduz cada
   parte para o VkDeviceMemory e offset físico corretos, inclusive quando
   atravessa duas alocações. Mantém o tipo DeviceLocal escolhido anteriormente.
   Se o prefetch falhar, restaura os intervalos e tenta somente as páginas
   pedidas, como o fallback ao intervalo original no release (`0x11fe2fc`).
   A tentativa é arredondada a páginas inteiras para preservar todas as
   ilhas GPU-modificadas antes de desproteger uma página. Se também falhar,
   restaura os intervalos e executa o readback convencional de 512 KiB.
4. Um readback elegível não pede staging GPU, não grava `copyBuffer` e não
   chama `Flush`. Os followers continuam compartilhando uma única conclusão;
   o guest aguarda somente o maior tick das escritas relevantes.
5. A publicação continua no cache owner. Após a conclusão, ele revalida os
   ticks e as flags stale, invalida os átomos não coerentes e reúne os spans
   físicos num snapshot CPU. O mecanismo existente publica somente os bytes
   copiados das páginas ainda GPU-modificadas, sob os locks de região.

O snapshot CPU adicional evita alterar a lógica de publicação por grupos de
páginas, mas tem custo de alocação/cópia no host. A economia buscada é a
cópia GPU e a submissão/espera pelo tick corrente, não eliminar todo memcpy.

## Cobertura de produtores

| Escrita no backing de buffer | Ponto registrado |
| --- | --- |
| Draw e dispatch diretos/indiretos | `Rasterizer::ResetBindings` → `Runtime::AccessBuffer`, após gravar o comando |
| Cópia e upload de buffers, incluindo upload em partes | `Runtime::CopyBuffer` → `AccessBuffer` para o destino |
| Clear e inline data | `Runtime::FillBuffer` / `InlineData` → `AccessBuffer` |
| Download de imagem para buffer | `Runtime::DownloadImage` → `AccessBuffer` |
| Tiling de imagem para a arena | `TileManager::TileImage` → `AccessBuffer` |
| Shader HLE de cópia | `vk_shader_hle.cpp` → `AccessBuffer` para o destino |

`Runtime::CopyImageWithBuffer` só recebe staging do pool no seu chamador
atual; não escreve as alocações de residência. Motion/upscalers/presenter
usam imagens e buffers próprios. O processador de faults DMA escreve seus
buffers auxiliares; o acesso shader arbitrário por BDA é **excluído** deste
caminho. `CollectShaderInfoPass` zera `uses_dma` quando a opção DMA está off.
Novos produtores que escrevam arenas devem continuar notificando AccessBuffer.

## Coerência e fallback

- Só habilitado no Android Bionic, Relaxed, readback diferido ativo,
  hot pages off e DMA off. Não ativa DMA. Precise/Glibc mantêm o caminho
  anterior. O modo e DMA são rechecados antes de preparar/capturar.
- Não se força outro tipo de memória. Sem HOST_VISIBLE, o caminho permanece
  desligado. Uma falha de `vkMapMemory` preserva o readback convencional.
- Tick corrente/desconhecido, gap de residência, offset fora da alocação ou
  erro de invalidação também preservam o caminho convencional.
- Não se lê um ponteiro mapeado antes de a conclusão do tick ser confirmada.
  Uma escrita posterior na mesma página invalida o readback mapeado, inclusive
  upload/tiling. A revalidação dos ticks oferece uma segunda verificação.
- A publicação mapeada usa sempre o cache owner, inclusive se o modo mudar
  durante um pedido. Não há publicação concorrente do guest a partir da arena.
- Non-coherent só é elegível com átomo <=4 KiB. Offsets de residência têm
  alinhamento >=16 KiB; arredondar invalidação ao átomo fica dentro das
  páginas cobertas pelo rastreador de escritas. Não há escrita CPU através
  desse mapeamento que possa ser descartada por uma invalidação.
- A lista de alocações retém tamanho e ponteiro; rollback de residência
  desfaz o mapeamento antes de liberar a alocação. Readbacks mapeados não
  são devolvidos ao staging pool, pois não possuem staging GPU.
- Intervalos stale voltam a `gpu_modified_ranges`, como no caminho anterior.
  A janela de 512 KiB não é inteiramente desprotegida/publicada.

Referências de sincronização: [exemplos Khronos](https://docs.vulkan.org/guide/latest/synchronization_examples.html),
[vkMapMemory](https://docs.vulkan.org/refpages/latest/refpages/source/vkMapMemory.html) e
[vkInvalidateMappedMemoryRanges](https://docs.vulkan.org/refpages/latest/refpages/source/vkInvalidateMappedMemoryRanges.html).

## Validação e medição no aparelho

Os testes compilam os métodos reais de alocação/rollback, preparação,
captura, início/junção, publicação, recuperação e pruning com os tipos reais
de Vulkan-Hpp fixados no projeto e UBSan. Exercitam offsets físicos não
zero, duas alocações, dados esparsos, espera do tick 7 com tick corrente 10,
ausência de copy/Flush/staging nos casos elegíveis, stale na mesma página,
escrita disjunta na mesma janela, escrita corrente fora dos 64 KiB mas dentro
dos 512 KiB, escrita corrente dentro dos 64 KiB mas fora das páginas pedidas,
revalidação, memória não coerente e falhas
de mapeamento/invalidação. O hook real de AccessBuffer é exercitado com
shader/transfer/memory writes, inclusive ranges memoizados em ticks novos;
leituras e staging não solicitam a barreira host. O rastreador é confrontado
com uma referência por página em 20 mil operações aleatórias, atravessando
blocos, com ticks fora de ordem, limites e overflow. Precise, DMA on,
override off e compilação fora do Bionic são casos separados.

Para A/B, `TLG_MAPPED_READBACK=0` desliga mapeamento, rastreamento adicional,
registro de alocações e barreiras host desta política. Não é necessário
invalidar shader/pipeline cache. O boot informa host-visible, coherent,
atom e política ativa. `mapped readbacks` nos relatórios indica scheduled,
applied, capacidade dos snapshots, fallbacks current/unknown, mapping e
rejeições na captura. Scheduled não equivale a publicação nem a ganho de FPS.

A nova barreira de visibilidade, manutenção dos ticks e snapshot CPU têm
custo. O benefício depende da incidência de intervalos de submissões
anteriores. Os logs anteriores não medem essa elegibilidade, e testes host
não executam Turnip nem provam ganho de FPS. A próxima execução precisa
confirmar publicações mapeadas e comparar FPS/esperas/recorder no mesmo trecho.


## Revisão após o primeiro teste mapeado (`f0705a7`)

O log `iss-android.log(2).txt` confirma HOST_VISIBLE/coherent, DMA off e
2128 publicações mapeadas. Comparando sete janelas de gameplay (~35 s),
sem as três de boot, o FPS ponderado passou de 37,30 (log 32) a 38,84;
sem a primeira janela de transição, 37,80 a 40,38. São execuções distintas,
não uma medição controlada. As chamadas de espera caíram de 92,79/s a
50,24/s, mas o recorder aumentou de 14,88 a 16,43 ms/frame. Não há perfil
por função que atribua esse aumento ao rastreador. No final, as publicações
mapeadas por relatório caíram a 47 e 1. O contador `mapped_stale` só mede
rejeições na captura, não invalidações detectadas antes dela.

Esta revisão remove dois custos comprovados pelo fluxo do código:

- Um memo fixo de 64 entradas guarda os intervalos de páginas já registrados
  no mesmo tick. Escritas repetidas nos mesmos bindings evitam percorrer a
  árvore, adquirir `deferred_mutex` e procurar readbacks sobrepostos novamente.
  A chave inclui as páginas inicial/final e o tick; colisões apenas refazem o
  caminho completo. Uma submissão nova atualiza o rastreador novamente. É
  seguro pular a segunda invalidação porque nenhum readback mapeado pode
  ser preparado para essas páginas no tick corrente: ele exige tick anterior.
  Isso depende do contrato já existente de registrar todos os writes no
  tick corrente do scheduler, antes da submissão. A barreira host continua
  sendo solicitada mesmo em um memo hit.
- Depois da preparação mapeada, o owner consulta a conclusão sem bloquear.
  Se o tick já terminou, captura/revalida/publica imediatamente e devolve um
  objeto já aplicado, sem inseri-lo na lista de pendentes. O guest evita tanto
  a espera quanto o segundo pedido de publicação na fila. Isso fecha a janela
  entre esses dois pedidos na qual um write poderia invalidar uma leitura
  que já estava pronta. `mapped ready on owner` conta esse caso. Se a GPU
  ainda não terminou ou a captura falhar, preserva o caminho assíncrono e a
  recuperação anteriores. Não força Flush nem espera GPU no owner.

Os testes exercitam os métodos de produção com publicação imediata através
de duas alocações sparse, falha de invalidação após detecção de prontidão,
deduplicação por páginas com bytes distintos, ticks novos/fora de ordem e
comparação com a referência de páginas. O teste de fila concorrente verifica
que o guest de um objeto já aplicado faz somente um pedido ao owner e zero
esperas; um write GPU posterior mantém seu GPU-bit e não é desprotegido
pela conclusão antiga. Os casos de GPU incompleta continuam verificando
que o recorder progride enquanto o guest espera.

A revisão não torna elegíveis escritas do tick corrente e não resolve todos
os descartes. O ganho de FPS adicional exige teste no aparelho; testes host
não demonstram esse ganho nem ausência de flicker.

## Investigação do log `iss-android.log (1)(1).txt` (`eddd39a`)

O teste confirma 1918 publicações imediatas, mas não ganho adicional de FPS:
38,84 -> 37,79 nos sete intervalos iniciais de gameplay; excluídas as duas
primeiras janelas de transição, 40,73 -> 40,53. O recorder caiu de 16,43 a
15,70 ms/frame no primeiro recorte. O novo log tem 685 ocorrências de fallback
stale nos relatórios e 2317 **tentativas** current/unknown. As janelas dos
relatórios de readback não coincidem exatamente com BionicPerf. Não são
685 snapshots distintos: vários guests podem compartilhar um readback.

### Diferença encontrada no fluxo de invalidação

`ReadMemory` compartilhava o primeiro wait/publicação em `call_once`, mas,
se o snapshot fosse invalidado, cada guest ia diretamente a `flush_request`.
Esse callback usa `DownloadMemory`, que grava outra cópia e chama
`Scheduler::Finish` no owner. Mesmo quando a nova escrita já foi submetida,
o caminho não voltava a verificar se ela podia ser lida pelo mapeamento.

Agora um pedido Bionic/Relaxed pode fazer **uma única nova tentativa** por
`StartDeferredReadback`. A entrada passa por `PruneDeferredReadbacks`, que
recupera os intervalos removidos de snapshots stale antes de recolher os
bytes novos. Pedidos simultâneos podem compartilhar esse snapshot substituto,
seja ele mapeado ou staging, e a espera continua no guest. Isso também evita
esperar um tick antigo já sabidamente inútil: as flags stale/recovered são
verificadas antes e depois do wait. Após uma segunda invalidação, permanece o
download síncrono anterior para garantir progresso. Precise e outras
plataformas continuam com uma tentativa. `TLG_READBACK_RETRY=0` desliga a
nova tentativa no Bionic.

Essa mudança não reaproveita bytes da cópia invalidada e não declara o
snapshot antigo aplicado. Cada guest avalia o objeto substituto que recebeu;
suas páginas só são liberadas pela publicação validada, sob os mesmos locks.
Um novo write GPU após a publicação continua protegido pela rechecagem de
`MarkRegionAsCpuModifiedUnlessGpu`.

### Cópia staging desnecessária para escritas do tick corrente

Os dois caminhos mapeados antigos (64 KiB/páginas pedidas) exigem tick
**anterior** ao corrente. Esse critério continua igual, para que uma leitura
antiga não precise submeter trabalho atual. O release 025 também tem esse
critério em `0x11fe49c–0x11fe4a0`; a extensão abaixo é uma otimização nova
para as arenas sparse, não uma alegação de outro ramo privado encontrado.

Se ambas as tentativas antigas falharem, já existe um conjunto de copies
coletado para o fallback de 512 KiB. Quando todos os seus intervalos têm
tick conhecido <= corrente e backing mapeável completo, esse fallback pode
usar os spans físicos diretamente. Para tick corrente, faz **um Flush** e
aguarda o tick que ele sinaliza, sem alocar staging nem gravar a cópia GPU.
Assim mantém a janela de compartilhamento/prefetch do fallback anterior.
O caso antigo elegível continua sem Flush; não foi trocado por essa via.
`TLG_MAPPED_READBACK_CURRENT=0` desliga a extensão ao tick corrente.

Submeter é obrigatório: o callback do scheduler resolve os sparse binds;
`FlushHostReadBarrier` grava `ALL_COMMANDS/MEMORY_WRITE -> HOST/HOST_READ`
após o fechamento do render pass/recording/host copies e antes de terminar
o command buffer e sinalizar o semáforo. `CaptureMappedReadback` continua
exigindo conclusão confirmada e revalidação de ticks. Tick desconhecido,
falha de mapeamento, DMA on, política desligada ou modo diferente mantêm o
staging convencional. Novas escritas na mesma página invalidam a leitura,
inclusive quando o mapeamento foi preparado antes do Flush.

### Validação dos dois fluxos

Os testes dos métodos reais verificam current-tick mapeado com exatamente
um Flush, zero staging/cópias, barreira host, zero publicação antes da
conclusão, memória coerente/não coerente, conclusão imediata, escrita no tick
seguinte e recuperação. O override off e a falha de map mantêm staging.

O teste de fila roda `ReadMemory`/`FinishDeferredReadback` de produção com
16 guests: após invalidar o primeiro snapshot, todos compartilham uma nova
geração. O owner processa um comando de prova enquanto o tick substituto
ainda está incompleto; apenas os bytes novos são publicados, uma única vez.
São testados também esgotamento do retry, snapshot já stale/recovered sem
espera obsoleta, retry que não encontra mais bytes dirty, override off e os
modos/plataformas anteriores.

A linha `mapped current submitted` conta as submissões sem cópia staging.
`deferred retries` mede pedidos que repetiram e quantos terminaram com um
snapshot aplicado; `stale (downloaded)` continua contando apenas o fallback
final síncrono. Esses contadores verificam uso dos caminhos, não ganho de FPS.
A CPU e a GPU ainda precisam completar as escritas necessárias; a revisão
retira cópias e bloqueios evitáveis, sem prometer eliminar todas as esperas.
