# Continuação após os logs 27 e 28

## Resultado observado

Mesmo recorte usado anteriormente: excluir as primeiras três janelas de
boot/menu/transição e usar as oito seguintes, aproximadamente 40 segundos.
Médias ponderadas pela duração das janelas:

| Log | FPS | CPU do recorder por frame apresentado | Esperas de GPU por frame apresentado |
| --- | --- | --- | --- |
| 26 | 37,655 | 16,372 ms | 14,040 ms |
| 27 | 36,286 | 16,660 ms | 14,570 ms |
| 28 | 37,428 | 17,235 ms | 13,667 ms |

CPU é a diferença de relógios de CPU da thread; esperas são somas de durações
host, podem vir de threads diferentes e não são o tempo de execução GPU de
um frame. Não devem ser somadas para calcular frame time. Sem percurso e
condições térmicas controlados, essas amostras não isolam uma regressão nem
demonstram ganho da política de contração dos shaders.

O log 28 confirma contração habilitada, Relaxed, saída 1280x720 e os mesmos
12 writes de patches. Nenhuma linha `BionicSleep`: não confirma uso do
ajuste de sleeps. O warm-up foi 0,53 s, contra 0,94 s no 27; o trabalho
acumulado dos builders caiu de 1,611 para 0,034 thread-s. Isso indica cache
de pipelines mais quente, sem ganho equivalente sustentado no gameplay.
Os descartes de readbacks foram 114 para 10678 aplicações (1,056% do total
aplicado+descartado); não explicam sozinhos o patamar de desempenho.

Também foi conferido `WorkSinceSubmit`: o contador é zerado na submissão;
não foi encontrada ali uma ausência de reset que justificasse uma correção.

## Hipótese de UBO descartada

A referência pública fixada `zenithblue-oss/shadps4-arm64`,
`be6bc2e9c60799e071dd2fafa6216e8d80ec619c`, contém infraestrutura de uniform
buffers. Porém `BufferResource::IsStorage` em `src/shader_recompiler/resource.h`
retorna `true` incondicionalmente; o uso de `profile.max_ubo_size` está comentado.
Portanto a existência desses campos não demonstra uma otimização ativa
faltando no port. Não foi feita uma conversão indiscriminada para UBO.

## Nova otimização do envio de descritores

O caminho anterior de `Pipeline::BindResources` copiava o array completo de
`vk::WriteDescriptorSet`, depois cada array de buffer/image/texel infos e
ajustava os ponteiros. O recorder entregava essas estruturas para o driver
em todos os draws/dispatches. Os push constants eram outra closure.

No novo caminho Bionic, cada pipeline com push descriptors constrói uma vez
um `VkDescriptorUpdateTemplate`, usando seu layout, bind point e set 0 reais.
A cada chamada só os payloads dos descritores são empacotados: buffer infos,
image infos e texel views, incluindo arrays e bindings com lacunas. A cópia
no chunk usa palavras de 64 bits para manter alinhamento. Uma única closure
envia os mesmos push constants e os descritores com
`vkCmdPushDescriptorSetWithTemplateKHR`, na mesma ordem anterior.

Isso reduz os bytes copiados para o recorder, retira a cópia e o ajuste de
ponteiros dos WriteDescriptorSet e permite ao driver reutilizar o formato
do update. É uma otimização nova do port, não foi identificada como recurso
ativo do release 025. Não altera shaders, imagens, barreiras, readback mode,
esperas, afinidade ou o driver. Não há medição de ganho de FPS desta mudança.

### Fallback e vida útil

- `TLG_DESCRIPTOR_TEMPLATES=0` preserva o caminho anterior para A/B.
- Fora do Bionic a política continua desabilitada.
- Sem entry points, sem push descriptors ou sem bindings, não cria template.
- Tipo não suportado, limite de payload ou falha de criação mantêm a emissão
  anterior; não removem o draw nem interrompem o jogo.
- O pack valida binding, tipo, tamanho de array, array element, `pNext` e
  ponteiros. Uma diferença usa as escritas originais, sem emitir payload parcial.
- O template pertence ao pipeline e é destruído antes de seu layout. As
  closures têm snapshots dos payloads; não dependem das estruturas temporárias
  do produtor. A troca para gravação direta durante `RecordData` também mantém
  os dados vivos até o consumo imediato pelo driver.
- A criação ocorre depois do pipeline layout, incluindo o warm-up. Não muda
  SPIR-V ou formatos de cache; não requer limpar saves ou caches.

`GPU: descriptor templates policy=on available=1` informa capacidade; não
prova uso. `BionicDescriptors: template_calls=... legacy_calls=... payload_bytes=...`
informa a cobertura efetiva por janela. Verificar esses números antes de
atribuir qualquer diferença de FPS ao caminho novo.

O build script agora injeta `TLG_BUILD_COMMIT` no probe, para substituir
`Build: unknown` por um SHA verificável nos próximos logs.

## Validação

O teste de empacotamento compila o helper de produção com UBSan e verifica
payloads mistos, arrays, null handles, whole size, fontes alteradas depois do
snapshot, fronteiras, overflow e rejeição de formatos incompatíveis.

O teste de integração compila os métodos reais `BuildDescriptorTemplate` e
`BindResources` contra os mesmos Vulkan-Headers v1.4.363 do CI
(commit `6802bb4733b63ed5efd3adb308a6c885ef180ea1`), com driver e scheduler simulados.
Confere envio direto/diferido, graphics/compute, arrays, tamanho/alinhamento,
ordem de push constants, snapshot após reuso do produtor, mudança para modo
direto, templates desabilitados/indisponíveis, erro de criação, descritores
não suportados, formato diferente, zero bindings e fallback para descriptor sets.
Não executa comandos na GPU Adreno. O workflow repete esse teste após instalar
os headers fixados, antes da compilação nativa completa.

Referências de API:

- https://docs.vulkan.org/refpages/latest/refpages/source/vkCmdPushDescriptorSetWithTemplate.html
- https://docs.vulkan.org/refpages/latest/refpages/source/VkDescriptorUpdateTemplateCreateInfo.html
- https://docs.vulkan.org/refpages/latest/refpages/source/VkDescriptorUpdateTemplateEntry.html

Validação final: 73 testes host passaram em 55,264 s, sem skips, incluindo
a integração com os headers fixados. `git diff --check` e `bash -n` do build
nativo também passaram. Compilação do APK e teste no aparelho são separados
dessa validação host.
