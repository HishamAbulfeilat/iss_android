# Resultado dos logs 12/13 e armazenamento de especializações

## Resultado no aparelho

Os logs completos 12 (477 linhas) e 13 (484 linhas) executam `4c452c4`,
1280x720, Relaxed, DMA desligado, Adreno 830 e Turnip Gen8 V37 Patched.
O reuso de descritores estava ligado. Somadas todas as janelas, houve
13.562.368 misses, zero hits e zero bypasses por command buffer bruto.
A alteração anterior não poupou nenhuma chamada nesses testes.

Mesmo recorte relativo dos relatórios BionicPerf, índices 5–9:

| Log | Build | FPS ponderado | CPU do recorder/frame | CPU de comandos/frame |
| --- | --- | ---: | ---: | ---: |
| 10 | 4667972 | 41,9265 | 17,5044 ms | 20,6594 ms |
| 11 | 4667972 | 43,5008 | 16,9639 ms | 18,8275 ms |
| 12 | 4c452c4 | 42,8514 | 16,4790 ms | 18,5656 ms |
| 13 | 4c452c4 | 43,6603 | 16,5548 ms | 18,1379 ms |

Percurso e temperatura não são uma repetição controlada. Os resultados não
demonstram ganho causal de FPS. Os tempos de CPU são relógios de threads;
esperas host de threads diferentes se sobrepõem e não medem a duração GPU.

O cache completo de descritores de `4c452c4` foi retirado, incluindo snapshots,
comparações, chaves, contadores e invalidações auxiliares. Os templates compactos
anteriores, push constants por chamada, reuso de vertex input, readbacks e saves
foram preservados. Os testes específicos do mecanismo retirado foram revertidos.

## Flatbuf: constatação e limite

`Rasterizer::BindBuffers` copia `stage.FlatUserData()` com `StreamBuffer::Copy`
sempre que encontra o buffer especial Flatbuf. Mesmo dados iguais recebem outro
offset, salvo wrap; offsets diferentes são descritores diferentes. O flatbuf
contém também os registradores e tabelas que `Info::RefreshFlatBuf` atualiza.
Isso é um obstáculo real ao reuso integral; os logs não separam mudanças de
payload de mudanças de lifetime do template e não provam a causa de cada miss.

Não se implementou um cache por endereço nem se ignorou o offset. Um cache de
conteúdo precisaria provar repetição suficiente e invalidar no wrap, submissão
ou reuso da região; sem isso acrescentaria outra comparação sem benefício
demonstrado. O upload continua contendo todos os bytes anteriores.

## Trabalho removido na seleção de shaders

O cache de fetch shaders já revalidava os bytes do código, mas retornava
`FetchShaderData` por valor. Seu vetor de atributos era copiado para uma
`StageSpecialization` temporária em cada consulta. No caminho quente de
`PipelineCache::GetProgram`, esse objeto era construído e destruído a cada
estágio de cada draw, independentemente de haver compilação nova.

Agora a seleção Bionic mantém um scratch separado por estágio. `Refresh`
recalcula todos os campos anteriores, mas retém a capacidade dos containers.
O parser ganhou uma sobrecarga que atribui o resultado no optional existente;
um hit pode reutilizar a capacidade do vetor, em vez de alocar outro.
A API por valor continua disponível aos outros consumidores.

O scratch pertence a `PipelineSelection`, não é compartilhado com o recorder
ou compiladores. Um miss pode movê-lo para uma compilação/permutação; a próxima
consulta reconstrói normalmente seu estado movido. Os caminhos de preparação
com recuperação de faults continuam usando seu objeto temporário anterior.
Outras plataformas continuam construindo especializações locais em GetProgram.

Nenhum resultado de especialização é presumido válido só por endereço ou pelo
draw anterior: são atualizados Info, RuntimeInfo, bindings, bitset, atributos,
buffers, imagens, FMASKs, samplers e constantes de tessellation. A validação do
bytecode e os snapshots dos compiladores continuam ativos.

Além disso, `RefreshGraphicsStages` move o optional local para a seleção,
retirando uma segunda cópia de seu vetor. A cópia de retorno do GetProgram
continua existindo; não se afirma que todo o pipeline ficou sem alocações.

## Validação e critério para outro APK

O teste compila os corpos reais de Refresh, parser e construtor com UBSan.
Uma referência congelada do construtor de 4667972 compara 10.000 entradas
variáveis: estágios, tamanhos, validade de recursos, formatos, divisores,
bindings, tessellation, mudanças de bytecode e recuperação de objeto movido.
Também exercita thread separada, snapshot de compilador, parser desabilitado,
redução/aumento do código e ausência/retorno do fetch shader.

Nos 100.000 hits aquecidos de parser com oito atributos, a API anterior aloca
100.000 vetores; a sobrecarga com armazenamento retido aloca zero depois do
aquecimento. Restabelecer um vetor temporário a cada hit faz esse teste falhar.
O decodificador e os recursos são simulados: o teste valida preservação dos
resultados e alocações do parser, não custo real de shaders do jogo ou FPS.
Os small_vectors dos metadados são modelados por vetores no teste sem Boost;
as contagens anunciadas medem só o std::vector real do fetch shader.

Essa correção elimina trabalho identificável de CPU e remove a experiência
que teve zero cobertura útil. Ainda não comprova que a perda de FPS para o
Bachata esteja resolvida. Não se gera outro APK apenas com essa prova host:
fica preparada para uma revisão que justifique novo teste no aparelho.
