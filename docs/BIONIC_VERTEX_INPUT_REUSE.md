# Reutilização da descrição de vértices no Bionic

## Evidência e limite

O log 30 executou `319901203c22e60a545e92ec298504007bbec0dc`.
Nos primeiros oito intervalos de gameplay (~40 s), mediu 37,04 FPS,
16,45 ms de CPU do recorder por frame e 18,83 ms de CPU do command thread
por frame. O log 28 mediu 37,43 FPS; a alteração de descritores ainda não
demonstrou ganho consistente de FPS. Não há tempo individual de
`vkCmdSetVertexInputEXT` nos logs: a relevância deste novo caminho é uma
hipótese, não um gargalo medido por função.

O `EmitVertexBuffers` anterior copiava as descrições de bindings/attributes
para chunks e gravava `vkCmdSetVertexInputEXT` em **cada draw**, inclusive
quando as descrições eram iguais. O Bloodborne de referência
`ducvd89/Bloodborne-Android@9271e86a14e4e8c1d63ce1e11eee854ede55cee6`
também reenvia essas descrições a cada draw em seu `EmitVertexBuffers`.
Esta alteração é uma otimização local, não uma diferença comprovada que
explique a vantagem do Bloodborne ou do Bachata.

## Alteração

`Scheduler::RecordVertexInput` mantém o último formato do command buffer
no produtor, com a mesma disciplina de propriedade de `dynamic_state`.
Com descrições idênticas, elimina a cópia para o chunk, a closure e a
chamada ao driver. A comparação considera todos os campos das estruturas,
incluindo binding, location, format, offset, stride, input rate e divisor;
não usa hash nem comparação de padding. A descrição armazenada é uma cópia
própria; as closures mantêm seus snapshots independentes.

Os endereços dos buffers, offsets e conteúdos continuam sendo resolvidos
e vinculados em cada draw. Não há cache de recursos guest, shaders,
texturas, descritores ou dados de vértices nesta alteração.

Padrão: habilitado somente no Android Bionic. Para A/B:
`TLG_VERTEX_INPUT_CACHE=0`. O log informa a política e
`BionicVertexInput: issued=... skipped=...`. Esses números medem comandos
eliminados; não substituem a medição de FPS no aparelho.

## Invalidação

- Todo início de command buffer invalida o estado, mesmo se o handle for
  reciclado pelo pool.
- Toda exposição via `Scheduler::CommandBuffer()` invalida, inclusive se o
  gravador já estiver em modo direto: chamadores raw podem trocar pipelines.
- O resample de depth/stencil em `vk_scene_resolution.cpp` invalida antes
  de enfileirar seu pipeline gráfico com vertex input estático.
- Os blits gráficos usam `CommandBuffer()`; o post-process do presenter usa
  seu command buffer separado. Os demais binds gráficos do rasterizer,
  incluindo square passes, usam a política global de vertex input dinâmico
  do `GraphicsPipeline`.
- Estruturas com `pNext` não nulo não são reutilizadas.

Um pipeline que declara `VK_DYNAMIC_STATE_VERTEX_INPUT_EXT` não perturba
essa descrição ao ser vinculado. Um pipeline com estado estático pode
invalidá-la. Referência: [Vulkan, Pipelines / Dynamic State](https://docs.vulkan.org/spec/latest/chapters/pipelines.html).
Novos helpers gráficos com estado estático precisam invalidar antes de
gravar seus comandos, ou obter o command buffer pela API raw já protegida.

## Validação

O teste compila os métodos reais `RecordVertexInput`, `CommandBuffer`,
`InvalidateVertexInput`, `AllocateWorkerCommandBuffers` e
`Rasterizer::EmitVertexBuffers` contra Vulkan-Headers fixados em
`v1.4.363` (`6802bb4733b63ed5efd3adb308a6c885ef180ea1`), com UBSan e um
driver controlado. Exercita gravação direta/diferida, troca para modo
direto, alteração de cada campo, mutação do produtor antes da execução,
buffers/offsets diferentes com formato igual, formatos vazios, chains,
invalidação por helper, acesso raw repetido e reciclagem do mesmo handle.
Também exercita cache desligado e compilação fora do Bionic.

Ganhos de FPS e preservação visual exigem teste no Redmagic; a validação
host não executa Turnip nem mede GPU. DMA, Relaxed, linear images,
sincronização de readback e afinidade não foram alterados.
