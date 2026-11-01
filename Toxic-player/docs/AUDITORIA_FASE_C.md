# Fases C e D — o que foi feito e o que NÃO foi verificado

## Corrigido
- App não carregava a última música: a fila não era salva e a tela não sincronizava com o player.
  Agora salva fila, faixa, posição, aleatório/repetir e velocidade/tom; ao abrir restaura PAUSADO.
  Se o serviço ainda estiver vivo, só sincroniza a tela. Arquivos apagados são descartados.

## Motor de DSP próprio (DspCore, Kotlin puro) no pipeline do ExoPlayer
- EQ gráfico 10 bandas (peaking, Q 1,41) + EQ paramétrico de até 16 filtros:
  Peak, Low/High Shelf, passa-altas, passa-baixas, Notch (frequência, ganho, Q). Fórmulas RBJ.
- Pré-amp -12..+12 dB, atenuação automática opcional, balanço.
- Compressor (RMS/pico, threshold, ratio, joelho, attack, release, makeup) com medidor de redução.
- Limitador com look-ahead 5 ms + proteção final no teto. NÃO é true-peak (pico de amostra).
- Largura estéreo por Mid/Side (centro intacto) e crossfeed para fones.
- Medidores reais: pico, RMS, clipping, headroom, redução do compressor/limitador.
- Espectro real (FFT 2048) no Player, sem permissão de microfone.
- Importação AutoEQ/APO como filtros paramétricos; exportar/importar JSON; backup; duplicar, renomear, apagar; A/B.

## Mantido do sistema (dependem do aparelho)
Loudness, Bass Boost, Surround e compressor multibanda (DynamicsProcessing).

## Limitações conhecidas
- Latência fixa de ~5 ms (look-ahead). Só processa PCM 16 bits mono/estéreo; outros formatos passam sem efeito.
- Sem sidechain, sem true-peak, sem multibanda próprio, sem presets de fones embutidos (não inventamos medições).
- Matemática dos filtros, FFT e compressor conferida em Python; o código Kotlin NÃO foi compilado nem testado em aparelho.
