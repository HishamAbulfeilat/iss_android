# Dois gravadores Vulkan, com publicação host ordenada

## Resultado do teste anterior

O log 14 confirma `5336947849d6d35f170a7ecc63194589b6e55ec8`, CUSA00004
01.00, Relaxed, 1280x720, DMA desligado e Turnip Gen8 V37 Patched.
O recorte BionicPerf 5–9, usado nos comparativos anteriores, contém 1.079
frames em 25,04 s: **43,0911 FPS**, sem ganho demonstrado frente aos logs
12/13 (42,8514 / 43,6603 FPS). A CPU do gravador foi 17,0171 ms/frame;
esse relógio inclui espera ativa e não é duração GPU nem trabalho útil isolado.
Foram 1.159 chamadas de espera Vulkan nesse recorte, com durações host que
não devem ser interpretadas como tempo GPU por frame. As 18 compilações
somaram 4,79 ms nesse intervalo, e não explicam o patamar de FPS.

Na sessão inteira, a sobrescrita nativa aplicou 90 de 11.209 candidatos
(0,8029%); houve 9.656 rejeições de cobertura e nenhuma de backing. O ponto
`0x5421fb` teve 7.435 falhas lentas. O merge HLE continuou sem aplicações.
O limite de cobertura permanece: liberar a página após apenas 16 bytes de
escrita não pode descartar outros bytes modificados pela GPU.

## Diferença estrutural adaptada

O [Bloodborne Android 9271e86](https://github.com/ducvd89/Bloodborne-Android/tree/9271e86a14e4e8c1d63ce1e11eee854ede55cee6)
distribui segmentos entre gravadores com pools próprios. O Second Son
anterior executava todos os chunks contra um único `current_cmdbuf`.

Esta adaptação permite dois gravadores no Bionic com pelo menos oito CPUs
disponíveis. Cada gravador usa seu próprio command pool. Um batch contém
**no máximo dois command buffers primários**, submetidos juntos na ordem
original e protegidos pelo mesmo tick, fence e semáforos anteriores.

O corte acontece somente antes dos bindings de um draw que já precisa
iniciar outro render pass. Não suspende um passe no meio e não cria clear/load
adicionais: a terminação do passe anterior já era necessária. Descritores,
push constants, vértices, índices e estado dinâmico do próximo draw ficam
inteiros no novo segmento; os caches de estado dinâmico e de formato de
vértices são invalidados no corte. Cortar dentro de `BeginRendering` seria
tarde demais, pois parte desses bindings já teria sido emitida no segmento
anterior. Também não se corta imediatamente antes da submissão existente
por limite de draws, o que apenas acrescentaria um segundo buffer vazio.

O alvo do corte usa metade dos bytes do batch anterior, limitado a 64 KiB–2 MiB;
o primeiro alvo é 256 KiB. Não aumenta o número de vkQueueSubmit nem a janela
de oito submissões. A limitação a dois buffers importa no Adreno: o Turnip
emite limpezas no fim de command buffers, portanto dezenas de pequenos
segmentos podem trocar economia de CPU por trabalho extra de GPU.

Não é uma cópia integral do scheduler do BB: não introduz suspensão/retomada
de passes, hoisting, submit assíncrono ou twins. Captura/frame recorder mantêm
um segmento; builds com Tracy GPU e outras plataformas mantêm o gravador
anterior. `TLG_PARALLEL_RECORDING=0` desativa a adaptação para diagnóstico.

## Ordem, cópias e readback

As closures Vulkan dos dois buffers podem ser executadas simultaneamente,
pois não compartilham estado de command buffer ou command pool. Uma cópia
host ou publicação de sinal do segmento 1 só pode executar depois do
marcador final do segmento 0. Esses efeitos mantêm a ordem anterior, incluindo
a sequência de conclusão usada por WaitHostCopies. Não se libera um sinal
apenas porque o gravador posterior terminou primeiro.

Na implementação inicial, WaitPrefix bloqueava o gravador 1 ao encontrar
essa cópia/sinal. Agora AfterPrefix enfileira o efeito pendente e permite
continuar gravando os comandos Vulkan seguintes. O marcador do segmento 0
drena os efeitos pendentes em ordem; se o gravador 1 encontra novos efeitos
durante essa drenagem, acrescenta-os à mesma fila. Um único executor possui
a fila ordenada, mesmo depois de o prefixo terminar. Sync aguarda também a
fila e seu executor, além dos dois gravadores. Não acrescenta threads nem
submissões. O único produtor de efeitos dessa fila é o gravador 1; os efeitos
do gravador 0 continuam inline, antes de seu marcador. As cópias capturam
Item por valor e os sinais capturam EOP por valor, sem depender de dados de
um RecordChunk que possa ser reciclado durante a execução adiada.

O hook de propriedade é executado antes de capturar o segmento de uma cópia
ou sinal: ele pode drenar draws e mudar o segmento. Capturar o número antigo
antes do hook poderia perder a espera do prefixo.

`SyncRecording` aguarda ambos os gravadores. Acesso raw usa o último buffer
somente depois dessa espera; se for o primeiro uso do segundo segmento,
ele é iniciado pelo produtor enquanto o worker está ocioso. Pools só podem
ser usados por esse produtor ou pelo respectivo worker, nunca simultaneamente.
Um novo batch começa somente depois de ambos os gravadores terminarem.

A submissão continua aguardando as cópias host e publica a barreira
device-to-host no **último** command buffer antes de encerrar e submeter os
dois. A dependência cobre comandos anteriores da mesma fila, incluindo os do
primeiro buffer. Readbacks, snapshots, bytes CPU, ticks e os checks da
sobrescrita nativa permanecem ativos.

## Validação e observação

O fixture compila os métodos reais Record/RecordData, transferência de chunks,
corte, sincronização, cópias, sinais, acesso raw, barreira host e fechamento
dos buffers. Usa duas threads reais: bloqueia o primeiro gravador e exige que
o segundo avance inclusive DEPOIS de encontrar cópias e sinais cujo prefixo
está bloqueado; esses efeitos permanecem pendentes até sua conclusão.
Outro cenário bloqueia a própria cópia enquanto o primeiro gravador drena
a fila: novos efeitos permanecem ordenados, comandos Vulkan avançam e Sync
não retorna antes de a cópia terminar. Confere snapshots de dados mutados
após gravar, 100 reciclagens dos mesmos handles, ordem das cópias, mudança
de dono, cache de estado, batch curto e início raw do segundo segmento.
O teste de submissão exercita um/dois command buffers e mantém a janela
de semáforos, callbacks e conclusão GPU simulada. Os testes de readback
anteriores continuam sendo executados. O fixture concorrente também passou
em execução local com ThreadSanitizer.

`BionicParallelRecording: batches=... split=... command_buffers=...` identifica
quantos batches realmente usaram dois buffers. `recorder_cpu_ms` passa a somar
os relógios de CPU dos dois gravadores; não deve ser interpretado como duração
serial nem como tempo GPU. Os counters não demonstram ganho de FPS.
`deferred_host` conta cópias/sinais desviados para a fila, agregado somente
depois de Sync, sem acrescentar um atomic de estatística por cópia.

## Logs 15/16: sem ganho demonstrado, bloqueio reproduzido

Ambos confirmam bd5b101, Relaxed, 720p e dois gravadores ativos. No mesmo
recorte BionicPerf 5–9 (índices a partir de zero):

| Medida | Log 14 | Log 15 | Log 16 |
| --- | ---: | ---: | ---: |
| Frames / segundos | 1079 / 25,04 | 1088 / 25,04 | 1060 / 25,08 |
| FPS | 43,0911 | 43,4505 | 42,2648 |
| Espera host em SyncRecording, ms/frame | 2,3793 | 3,4323 | 3,6176 |
| CPU do comando, ms/frame | 18,8274 | 18,2040 | 19,0020 |
| CPU gravadores somada, ms/frame | 17,0171 | 15,6379 | 16,5136 |
| vkQueueSubmit host, ms/frame | 0,1476 | 0,1718 | 0,1731 |

Nos logs 15/16, 2343 de 5348 / 2271 de 5253 batches se dividiram: 43,81% /
43,23%, acrescentando aproximadamente 2,15 / 2,14 command buffers por frame.
Não é uma falha de ativação do segundo gravador. A duração de Sync aumentou,
mas os logs não medem isoladamente quanto desse aumento vem de WaitPrefix,
do desequilíbrio entre segmentos, da execução do driver ou do escalonamento.
As durações host podem se sobrepor e não são parcelas aditivas de tempo GPU.
As sessões não são replays controlados: não se atribui causalidade à pequena
variação de FPS nem se considera o paralelismo inicial um ganho validado.

No fixture concorrente, a fila antiga de bd5b101, com um adaptador que chama
WaitPrefix, falha na exigência de avançar o comando DEPOIS da cópia pendente;
a fila corrigida passa, mantendo cópias e sinais bloqueados. Isso comprova
a remoção desse bloqueio concreto, e não ganho de FPS no aparelho. A fila
ordenada do Bloodborne permite esse avanço; esta adaptação limitada preserva
o marcador conservador do prefixo e não importa seu submit assíncrono.

Esta é uma alteração estrutural de gravação, com validação de concorrência
e ordem. O tamanho do ganho e o custo dos command buffers adicionais ainda
precisam ser medidos no aparelho; não há promessa de superar o Bachata.
