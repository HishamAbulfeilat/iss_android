# Barreiras de buffers e publicação para a CPU

## Problema concreto

`Runtime::FlushBarriers` reúne somente os acessos do tracker de buffers na
`MemoryBarrier2`; os estados de imagens são dependências separadas.
Mesmo assim, o destino era `ALL_COMMANDS / MEMORY_READ | MEMORY_WRITE`.
No mapper de acessos do Turnip isso inclui profundidade, render targets,
input attachments, descritores e RTU, além dos consumidores de buffers.

O readback mapeado também marcava qualquer escrita da arena com uma barreira
de publicação `ALL_COMMANDS / MEMORY_WRITE -> HOST / HOST_READ` por submissão.
Assim, uma escrita de shader era apresentada como se pudesse incluir escritas
de profundidade, cor, CP e sysmem. O limite não era só trabalho de CPU: essas
categorias alimentam a emissão de operações de cache do driver.

## Correção

* Bionic usa como destino do tracker todos os consumidores de buffers
  implementados: indirect, index, vertex, uniform, shader read/write e transfer
  read/write. O estágio continua ALL_COMMANDS. Não se limita ao próximo draw:
  o flush limpa o tracker inteiro e precisa cobrir os usos posteriores também.
* Produtores genéricos ou não modelados mantêm o destino conservador anterior.
  Outros sistemas mantêm o comportamento anterior.
* A publicação para a CPU acumula os estágios e acessos das escritas realmente
  notificadas, incluindo todos os intervalos exatos dos lotes de CopyBuffer.
  A submissão consome e zera esse acumulador. A chamada sem argumentos continua
  conservadora para um produtor desconhecido.
* O tracker agora reconhece indirect command read. Os caminhos de draw/dispatch
  já o notificavam, mas READ_MASK não o registrava como leitura; isso podia
  perder uma dependência de execução para um escritor posterior.

Não se alteram estados/layouts de imagens, wait de conclusão, coerência de
memória, invalidação noncoherent, snapshots parciais ou preservação de escritas
da CPU. Imagens usam alocações próprias; aliases de memória guest são
sincronizados por cópias e transições, não por alias físico com a arena.

## Verificação no driver

O binário Gen8 V37 Patched fixado em driver.json tem SHA256
`31b4f16fed6bc0c60d80715067845d1ae34761fcbc1fc37e41e80d1de29a8b4e` e
identifica Mesa `26.3.0-devel git-aa35464ee5`. O release também contém patches
downstream; a referência abaixo verifica os algoritmos da base upstream,
sem afirmar que representa todo o fonte do binário.

O teste offline compila `vk2tu_access`, seus filtros e `tu_flush_for_access`
do commit [aa35464ee50c1311eb6cebb9882d02738642b692](https://github.com/chaotic-cx/mesa-mirror/tree/aa35464ee50c1311eb6cebb9882d02738642b692/src/freedreno/vulkan),
com os enums originais, Vulkan-Headers e a implementação real das nossas
barreiras. O trecho MIT está preservado no fixture, com atribuição.

| Caso, cache sem sujeira anterior | Antes | Depois |
| --- | --- | --- |
| Buffer shader/copy, sysmem, destino global | Invalidação de profundidade solicitada | Nenhuma operação de profundidade solicitada |
| Shader da arena -> host | Clean UCHE, clean cor/profundidade em sysmem, wait de escritas CP | Clean UCHE |
| Copy da arena -> host, sysmem | Categorias genéricas | Clean cor preservado, pois Turnip usa CCU na cópia |
| Shader + copy -> host, sysmem | Categorias genéricas | Clean UCHE e cor preservados |
| Sujeira real anterior de cor/profundidade -> host | Cleans necessários | Cleans necessários preservados |

No caminho de emissão, a invalidação de profundidade inclui uma limpeza
anterior; evitá-la elimina essa sequência causada artificialmente pelo buffer.
Isso não remove as limpezas obrigatórias do fim do command buffer nem as que
outras dependências de imagens realmente exigem.

Os testes cobrem sysmem/GMEM, sparse aliasing ligado/desligado, shaders,
cópias e misturas. Para 12.000 estados aleatórios de cache, verificam que a
barreira nova cobre as operações necessárias de cada um dos oito consumidores
de buffers: 96.000 comparações. Também verificam snapshots da fila deferred,
preservação exata da barreira de imagem, reset do tracker e da época, acumulação
e reset entre submissões e fallback conservador. O teste de write hook executa
AccessBuffer/CopyBuffer reais e o teste de readback conserva todos os cenários
de visibilidade e publicação anteriores. A CI repete com os headers fixados.

Esses resultados demonstram operações de cache desnecessárias solicitadas
pelo código anterior e sua remoção no algoritmo do driver. Não medem FPS,
frequência dessas barreiras no trecho do jogo nem garantem o tamanho do ganho
no aparelho. Nenhum ganho de host fixture é apresentado como FPS.

Validação local: suíte completa, 91 testes aprovados em 83,760 s, com UBSan
nos fixtures C++. Compilação Android completa é validada pela workflow Bionic.
