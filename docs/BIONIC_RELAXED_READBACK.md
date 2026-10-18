# Readback Relaxed sem espera na thread de comandos

## Evidência do log 22

O teste de `281421d` confirmou execução direta de draws, zero workers/scanner
de preparação e orçamento de shaders 2+1. Nas últimas seis janelas de cinco
segundos, a média de FPS foi 28,55, contra 27,64 no log 21. São execuções
separadas, sem garantia de percurso e condições térmicas idênticas.

O diagnóstico identifica `BufferCache::DownloadMemory` como a origem das
esperas no gameplay: 154–388 chamadas por janela, consumindo aproximadamente
1,05–1,30 segundo por janela nas seis últimas amostras. Esse método grava a
cópia e chama `Scheduler::Finish`, bloqueando a thread de comandos enquanto
a GPU a executa. O limite de oito submissões não aparece como origem dessas
esperas. Aumentá-lo não corrige a serialização observada.

Relaxed continua preservando dados da GPU numa falha de escrita da CPU:
`MemoryTracker::InvalidateRegion` chama o readback se a página tem dados
modificados pela GPU. A escrita deve preservar os bytes que não sobrescreve.
Desativar esse download perderia esses bytes.

O mecanismo `TLG_DEFERRED_READBACK` existente, introduzido em `63a2197`, já
deslocava a espera para a thread guest em Precise. Apesar de a variável estar
ativada no launcher, o teste de modo o desativava completamente em Relaxed.

## Alteração

Habilitar readbacks diferidos em Relaxed exclusivamente no Bionic nativo,
respeitando `TLG_DEFERRED_READBACK=0` e o toggle de comparação já existente.
Precise e os padrões dos outros sistemas continuam com suas políticas.

1. A thread de comandos registra e submete a cópia dos intervalos modificados.
2. A thread guest espera pelo tick dessa cópia. A thread de comandos pode
   continuar processando outros comandos durante a espera.
3. Em Relaxed, a publicação da cópia é enviada novamente à thread de comandos.
   Ela revalida a cópia e faz a transição de estado CPU/GPU em ordem com os
   outros usos do cache. Não publica o snapshot diretamente pela thread guest.
4. Se uma nova escrita tornou a cópia obsoleta, o caminho existente recupera
   os intervalos e usa o download síncrono. O snapshot antigo não é publicado.

A aplicação mantém as verificações por páginas efetivamente copiadas, os
locks das regiões, a invalidação do staging e a vida útil do staging diferido.
Falhas dentro da própria thread de comandos continuam síncronas. O modo
`TLG_HOT_PAGES=1` mantém o caminho síncrono em Relaxed: ele pode deixar páginas
graváveis sem falhas de proteção, condição incompatível com essa publicação
posterior. Eager readback e write merge permanecem restritos a Precise.

## Validação

O teste `test_bionic_relaxed_deferred.py` compila os métodos reais
`ReadMemory`, `FinishDeferredReadback` e `DeferredReadbackEnabled`, usando uma
fila com thread própria e uma conclusão de GPU controlada. Confere que a
fila continua progredindo antes da conclusão da GPU, publicação pelo owner,
preservação de bytes, duas threads compartilhando a cópia, snapshot obsoleto
antes e depois da conclusão da GPU, falha inline, janela sem dados, variável
desativada, readbacks Disabled, hot pages e manutenção do caminho Precise e
da restrição dos outros sistemas.

Esses testes validam o comportamento de software com mocks. O ganho de FPS,
a frequência de fallback e o resultado gráfico em Adreno precisam ser
verificados no aparelho. O launcher habilita os contadores de readback junto
com `ISS_COMPARE_STATS`, mantendo `TLG_READBACK_STATS=0` como override. Os
contadores distinguem cópias aplicadas de fallbacks por obsolescência, para
não atribuir ganho a uma variável que permaneça inoperante.
