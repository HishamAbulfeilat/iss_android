# Comparação aprofundada após o log 24

Referências fixadas em 18/10/2026:

- inFAMOUS Bionic: `41113e92484d4ba6547e331e9497a4c60cdba580` (log 24).
- [Bloodborne Android 0.11.1](https://github.com/ducvd89/Bloodborne-Android/tree/9271e86a14e4e8c1d63ce1e11eee854ede55cee6),
  commit `9271e86`; o fork zfitness consultado aponta para o mesmo commit.
- [GoW3 experimental-bionic](https://github.com/erickmendes22/gow3_android_daedalus_fork/tree/f8a7748872631015d9d3389ee12ebaf03726f35a),
  commit `f8a7748`.
- [shadps4-arm64](https://github.com/zenithblue-oss/shadps4-arm64/tree/be6bc2e9c60799e071dd2fafa6216e8d80ec619c),
  commit `be6bc2e`: referência de código, **não prova de identidade com o core025
  empacotado** no Bachata. O comparativo de execução usa o diagnóstico
  `BS4-20261018-81874ACC`, do APK 0.2.6 com executável `shadps4-arm64-025`.

## O que os logs permitem concluir

O readback diferido de `ed1c09c` levou o inFAMOUS de ~28 para ~37 FPS nesses
testes. Compartilhar a conclusão das cópias em `41113e9` diminuiu as chamadas
de espera no driver de ~258/s para ~82/s, mas não trouxe ganho claro de FPS.
Isso é consistente com o baixo custo das consultas ao driver: somam poucos
milissegundos por janela de cinco segundos, não todo o tempo de espera da GPU.

A média completa de gameplay foi 36,67 FPS no log 23 e 36,61 no 24. Nos
primeiros ~40 segundos foi 36,67 contra 35,99. Percurso, duração e condições
térmicas não são controlados, portanto não demonstram ganho nem regressão
causados pela última revisão. O fallback cresceu de ~1,28% para ~2,86%; também
não há evidência suficiente para atribuí-lo à conclusão compartilhada.

No log 24, `submission_host_ms` fica frequentemente entre 450 e 590 ms por
cinco segundos, enquanto `queue_submit_ms` fica em ~31–40 ms. O primeiro
inclui espera pelo gravador e pelas cópias host, callback de submissão e
alocação do próximo command buffer. Não é tempo de execução da GPU. Os
novos contadores separam espera por cópias de sincronização do gravador;
essas parcelas podem se sobrepor e não devem ser somadas ao total.

## Diferenças encontradas e aplicabilidade

| Caminho | Bloodborne | GoW3 Bionic | inFAMOUS Bionic antes desta revisão |
| --- | --- | --- | --- |
| Gravação Vulkan | Segmentos, dois gravadores por padrão em 8 CPUs; pools próprios | Um gravador | Um gravador |
| Submissão | Assíncrona pelos gravadores, batches com ordem e conclusão próprias | Produtor espera gravação/cópias e submete | Produtor espera gravação/cópias e submete |
| Cópias dentro de passes | Hoisting/prepass e suspensão/retomada por segmento | Sem integração equivalente do BB | Sem integração equivalente do BB |
| Escritas `WRITE_DATA` | Espera por fontes host que sobrepõem o destino; fences continuam ordenados | Espera global por cópias | Espera global por cópias |
| Pequena cópia em modo direto | Snapshot imediato | Entra na fila de cópias | Entra na fila de cópias |
| Escrita CPU em bytes GPU | Twins habilitados no launcher Relaxed, limite configurado 64 KiB | Download síncrono | Download diferido, com fallback síncrono |
| Fila de readback separada | Condicional a uma família compute sem graphics; último tick de escrita por intervalo | Sem o caminho do BB | Sem o caminho do BB |
| Memória stream | Política própria do BB; modelo in-place desligado no launcher Android | Device fora de NVIDIA | Cached; override existente |

### Implementado nesta revisão

Adaptar a espera por intervalo das cópias host, de forma independente do
gravador paralelo do BB. As fontes são registradas em todos os caminhos
assíncronos do buffer cache: pequenos snapshots, dados de texturas e grupos
de uploads maiores. Antes de `WRITE_DATA`, a fila de draws é drenada para
que fontes registradas pelo consumidor estejam visíveis. Uma escrita
disjunta não precisa antecipar todas as cópias de outros buffers.

Os fences e a submissão continuam usando a espera global completa. Os
caminhos DMA que realmente escrevem pela CPU também aguardam fontes
sobrepostas e sinais anteriores. A cópia pequena em modo direto é feita
imediatamente: deixar o snapshot num batch executado só na próxima submissão
poderia copiar o valor depois de um comando ter reescrito sua origem.

O rastreamento usa granularidade de 64 KiB e slots limitados. Colisões
viram wildcard; intervalos grandes ou com overflow exigem espera completa.
A conclusão de uma espera só aposenta fontes vistas antes de seu início:
fontes novas continuam pendentes. Falsos positivos apenas esperam mais;
não é permitido perder uma sobreposição.

O padrão está limitado ao Bionic nativo. `TLG_HOST_COPY_RANGES=0` recupera
a política anterior para A/B. `BionicHostCopies` informa skips, esperas
necessárias, tempo das esperas completas e sincronização com o gravador.
Não se presume que um número elevado de skips implique ganho de FPS.

### Diferenças maiores que não podem ser habilitadas por uma variável

- **Gravadores paralelos e submit assíncrono:** o BB associa closures a batches
  e segmentos, usa command pools por worker e acompanha ticks já submetidos.
  Aqui `RecorderThread` executa cada chunk contra `current_cmdbuf`, que só muda
  após `SyncRecording`. Retirar essa espera ou mover a submissão sem alterar
  a propriedade dos command buffers pode gravar no buffer errado ou usar o
  mesmo command pool concorrentemente. É uma integração estrutural; o tempo
  de espera pelo gravador indicará sua relevância para este jogo.
- **Twins:** o BB compara bytes guest com um snapshot anterior e preserva
  apenas os bytes diferentes durante upload/download. O inFAMOUS ainda espera
  pelo resultado GPU antes de liberar a escrita CPU. Essa mudança tem grande
  potencial, mas exige conciliação de intervalos também nos readbacks diferidos,
  uploads, unmaps e escritas fora do page tracking. Comparar valores não detecta
  uma escrita CPU que repõe o mesmo valor antigo; portanto não é uma substituição
  universalmente equivalente da sincronização. Não foi ativada por semelhança.
- **Fila separada de readback:** além de uma fila compatível, o BB rastreia o
  último tick de escrita de cada região e cria buffers compartilháveis entre
  famílias. O log atual não informa se o driver do aparelho oferece a família
  exigida. A existência do código não comprova que esse ganho esteja ativo
  no Bloodborne rodando no mesmo Adreno.
- **Stream Device/Cached:** a diferença real de tipos de memória selecionados
  no Adreno precisa ser verificada; a política por vendor do GoW3 inclui decisões
  para GPUs desktop. Trocar o rótulo não demonstra mudança física nem ganho.

## Bachata: fatos confirmados e limite da comparação

O diagnóstico executa core025, Relaxed, linear images off, copy GPU buffers on,
direct memory access off e os mesmos 12 patches de resolução/efeitos. Seu
driver é Turnip whitebelyash 26.2.99; o port mantém o V37 Patched solicitado.
O log registra `GPU buffer publication mode=disabled; using synchronous
publication`; isso não descreve todas as formas de readback, mas impede
assumir que qualquer mecanismo assíncrono do código público estava ativo.

No código de referência, o buffer cache usa buffers por intervalos e faz
`DownloadBufferMemory<false>` nas falhas CPU, com download ring e `Finish`.
O port usa arenas sparse, rastreamento de páginas e snapshots diferidos.
Os modelos de memória são diferentes: copiar isoladamente a política de GC
ou o tamanho do ring não torna os caminhos equivalentes. A publicação
assíncrona de cópias antigas também não substitui a proteção por versões
usada pelos readbacks diferidos atuais.

O GoW3 não contém uma otimização universal ausente que explique por si só
este patamar: preparação, orçamento móvel de shaders e execução direta já
foram adaptados. Seus shaders, quantidade de draws, passes e padrões de
memória são de outro jogo. Superar o Bachata nele não estabelece um ganho
percentual transferível para Second Son.

## Validação desta revisão

Testes compilam o rastreador real, os métodos reais de espera e cópia, e
o handler `RunWriteData`. Exercitam fronteira de granularidade, colisão,
wildcard, overflow, fontes novas durante uma espera, preservação do snapshot
antes de sobrescrever a origem, destino disjunto, manutenção dos fences,
override de A/B e política original fora do Bionic. Continuam necessários
gameplay e inspeção visual no aparelho; testes host não medem GPU Adreno.
