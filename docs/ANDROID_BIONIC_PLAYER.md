# Interface Android Bionic — inFAMOUS Second Son 0.1

A tela inicial reúne seleção de pasta, instalação de jogo/atualização por PKG,
vídeo e patches, BloodFrame, HUD, controles e transferência de saves.

## PKG

Selecione um PKG local e uma pasta vazia para o jogo base. A extração ocorre
diretamente nessa pasta, sem duplicar o PKG. Arquivos em nuvem precisam ser
baixados primeiro. A atualização usa a pasta selecionada e exige o mesmo TITLE_ID;
os arquivos correspondentes são substituídos. Em caso de falha, os arquivos já
extraídos permanecem na pasta para inspeção. O extrator não instala firmware nem
fornece chaves para pacotes protegidos. Saves continuam na área de dados do app.

## Menu dentro do jogo

Toque na pequena barra da borda esquerda, deslize dessa borda para a direita ou
pressione Voltar/Guide no controle. O menu oferece retomar, reiniciar, vídeo,
BloodFrame, HUD, controles, edição/restauração do layout e sair. O jogo continua
executando enquanto o menu está aberto; a entrada de controle é liberada.
No controle físico, direções navegam, A confirma e B fecha.

## BloodFrame / Lossless Scaling

Importe a **Lossless.dll da sua própria instalação do Lossless Scaling**.
Selecione desligado, 2×, 3× ou 4×, escala de fluxo óptico, modo desempenho e FP16.
As alterações são lidas entre quadros; a engine aquece o histórico antes de gerar.
A API mantém o driver Vulkan escolhido pelo jogo. Adreno 830 com Turnip Gen8 usa
o mesmo caminho de sincronização por host corrigido no port GoW3.
Em caso de falha de inicialização a apresentação continua sem geração; consulte o log.
Não se incluem Lossless.dll nem seus shaders no APK.

## HUD e controle

O HUD tem FPS em verde e GPU/CPU em magenta/ciano, em duas linhas. Escolha
esquerda/centro/direita e topo/centro/base ou arraste o HUD para uma dessas posições.
Com geração ativa mostra taxas medidas de origem e apresentação. Contadores de
utilização indisponíveis no sistema aparecem como N/A.

Os controles usam o tema carvão/vermelho/laranja do inFAMOUS. Em **Automático**,
o runtime lê o contador real de menus do jogo: D-pad e X mantêm suas funções de
navegação, e o grafite fica suspenso enquanto o menu está aberto. Ao fechar o menu
por qualquer opção, o mapeamento de gameplay volta sem depender de outro Start.
Essa leitura é habilitada apenas se duas assinaturas de código e a região de
memória coincidirem com o executável analisado. Em outra versão, Start/Options
serve de alternativa; a detecção automática precisa ser confirmada em aparelho.

O botão **AUTO / MENU / JOGO** alterna o modo manualmente. No controle físico,
segure **L1 + R1** e aperte **Start** para alternar sem enviar Start ao jogo.
Apertar Start normalmente volta ao Automático. O modo do jogo é independente do
menu de configurações do port (Voltar/Guide).

Em gameplay, o D-pad físico e virtual desliza o touchpad progressivamente enquanto
pressionado; soltar levanta o dedo. A opção pode ser desligada para enviar direções
normais. **Swipepad na tela**, desligado por padrão, permite arrastar um dedo em
qualquer direção, com coordenadas absolutas e sem transformar o gesto em clique.
TOUCH continua pressionando as duas pontas para levantar o carro. O editor permite
mover/redimensionar também o swipepad e o seletor de modo; o layout persiste.

## Idioma, FPS e drivers

Inglês é o padrão; português pode ser selecionado em Configurações → Idioma.
As opções de vídeo oferecem metas de 30/60/120/240 FPS e VSync. Metas acima de 60
precisam de tela/driver compatíveis e desempenho suficiente para a cena.

Seleção automática: Turnip Gen8 V38 Patched em Adreno 8xx e Turnip v27.0.0-R1
em Adreno 6xx/7xx. É possível importar um driver ZIP nas opções de vídeo.

## Atualização para 0.1

O APK usa código interno 8 e mantém pacote/certificado das builds Bionic anteriores.
Atualize sem limpar os dados para manter saves, drivers importados e configurações.
A versão glibc permanece separada. O cache de shaders muda automaticamente quando
necessário; a primeira execução pode recompilar shaders.

## Fullscreen e áudio nativo

Fullscreen fica disponível diretamente na tela inicial e começa desligado. Desligado,
a imagem fica em 16:9 e as barras do sistema permanecem visíveis. Ligado, oculta as
barras e usa o ajuste de imagem selecionado nas opções de vídeo.

O aplicativo é classificado como jogo no manifest Android, como o GoW3. A ativação
da interface de jogos do fabricante usa essa classificação.

AudioOut e Audio3d enviam seus fluxos ao misturador PCM nativo. A saída para o
AudioBridge é sempre s16 estéreo a 48 kHz, independentemente do formato de cada
porta do jogo. O log registra a abertura do FIFO e a primeira saída não silenciosa.


### Mobile controls and regional language selection

The default Mobile layout groups actions around the right thumb instead of a row of shoulder buttons. Move with the left stick and drag unused space on the right to look; the optional Classic layout retains its original saved positions. Both layouts preserve the same PadBridge mappings, swipepad, physical controller and native automatic menu detection. Each layout has independent move/resize/reset preferences.

Settings → Language defaults to Automatic. Supported UI languages: English, Portuguese, Spanish, French, German, Italian, Russian, Japanese, Korean and simplified Chinese. Unsupported system languages resolve to English; explicit choices override the system. Technical diagnostics without a translation retain English. On next boot, the launcher scans only metadata under art/ for the executable's lang_<language>_text.xpp and lang_<language>_audio.xpp names. A locale requires both packages; missing resources select PS4 English (1). ISS_LANGUAGE and TLG_LANGUAGE are set together so an inherited environment cannot override the UI choice.

The existing mask catalog now lists CUSA00004, CUSA00223 and CUSA00263. The supplied eboot SHA-256 `2d1ca79630d7bbe6fa29575f59aa7996d43071d74ac8e4f7d40137967d9039ab` permits the regional APP_VER mismatch; other mismatched builds remain rejected. Every selected signature still must match exactly once in executable PT_LOAD ranges. The game binary is never modified or distributed.
