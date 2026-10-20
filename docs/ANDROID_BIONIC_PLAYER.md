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

O D-pad físico e virtual desliza o touchpad progressivamente enquanto pressionado;
soltar levanta o dedo. A opção pode ser desligada para voltar a enviar direções
normais. O botão TOUCH continua sendo o clique do touchpad. O editor permite
arrastar cada controle e usar dois dedos para mudar seu tamanho; tudo persiste.

## Idioma, FPS e drivers

Inglês é o padrão; português pode ser selecionado em Configurações → Idioma.
As opções de vídeo oferecem metas de 30/60/120/240 FPS e VSync. Metas acima de 60
precisam de tela/driver compatíveis e desempenho suficiente para a cena.

Seleção automática: Turnip Gen8 V38 Patched em Adreno 8xx e Turnip v27.0.0-R1
em Adreno 6xx/7xx. É possível importar um driver ZIP nas opções de vídeo.

## Atualização para 0.1

O APK usa código interno 7 e mantém pacote/certificado das builds Bionic anteriores.
Atualize sem limpar os dados para manter saves, drivers importados e configurações.
A versão glibc permanece separada. O cache de shaders muda automaticamente quando
necessário; a primeira execução pode recompilar shaders.
