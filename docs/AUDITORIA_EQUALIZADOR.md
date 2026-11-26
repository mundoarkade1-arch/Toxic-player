# Auditoria do equalizador / processador de carro (2026-11-26)

## O que foi encontrado
1. **Recursos de carro soltos.** `audio/CarDsp.kt` (cortes, grave virtual, sub-harmônico, limitador de graves, atraso, medidor), `audio/CarPresets.kt`, a pasta `presets/` e os testes `CarAudioTest`/`CarPresetTest` já existiam, mas `DspCore`, `AudioFx` e a tela **não usavam nada disso**. O que o usuário mexia era só o EQ comum. `CarDsp.kt` ainda chamava `BiquadCoef.firstOrder`, que não existia (o projeto não compilaria).
2. **Presets de carro fracos.** Medidos no motor, o preset principal subia só ~3 dB aos 60 Hz (quase igual aos médios). Com alto-falante de porta, isso não se percebe.
3. **Nenhum indicador** de preset em uso, de efeitos ligados ou do quanto o som mudou.
4. **Formato de áudio.** O processador só tratava PCM 16 bits; faixa em 24 bits/float (FLAC alta resolução, por exemplo) passava **sem efeito algum e sem aviso**.
5. Trocar de faixa (nova sessão de áudio) chamava `apply()` e apagava a marcação do preset.

## O que foi feito
- `DspCore`: cortes HPF/LPF (Butterworth/Linkwitz-Riley, 6 a 48 dB/oitava), grave virtual, sub-harmônico, limitador de graves, contorno de loudness, foco na voz (só no centro), "ar", graves em mono, atraso/ganho por lado e polaridade, e medidor **entrada x saída** (graves 40–160 Hz, médios, agudos) com contador de quadros processados.
- `AudioFx`: todos os controles novos, salvos/importados em JSON (com limites), `applyCarPreset`, `isActive`, `resetCar`; preset marcado continua marcado ao trocar de faixa e ao reabrir o app; preset de EQ simples não herda o corte do preset de carro.
- `DspProcessor` + `PcmCodec`: PCM 16/24/32 bits e float; `DspEngine.inChain/formatInfo` dizem se o motor está mesmo recebendo o áudio.
- Tela (Mesa de áudio): aba **Carro**, cartão de estado no topo (efeitos ligados/desligados, preset ativo, "motor processando", barras de quanto graves/médios/agudos mudaram, botão "ouvir sem efeitos"), presets de carro com selo **● ATIVO**.
- Presets de carro regenerados e medidos (`presets/*.json` = `CarPresets.kt`).

## Medições (tom de 0,1 de amplitude; dB em relação a 1 kHz)
| Preset | 40 Hz | 60 Hz | 80 Hz | 250 Hz | 3 kHz |
|---|---|---|---|---|---|
| Grave Forte + Voz | +2,6 | +7,8 | +6,7 | -2,6 | +3,2 |
| Vibração Máxima | +8 | +11 | +8 | -2,9 | +3,4 |
Abaixo de ~30 Hz todos cortam (proteção do alto-falante de porta).

## O que foi verificado e o que NÃO foi
- **Verificado (JVM, sem Android):** motor, `AudioFx` (com stubs de Android/Compose/org.json), presets e codec PCM: 56 testes passando.
- **NÃO verificado:** compilação com Gradle/Android (Compose da tela, `DspProcessor` com Media3), build no Actions e som no aparelho. Rodar `gradle testDebugUnitTest assembleDebug` antes de confiar.

## Limites reais
O app entrega 2 canais (E/D). Divisão em vias (kit 2 vias com cruzamento ativo, subwoofer em canal próprio) precisa do amplificador/rádio; o app prepara o sinal (cortes, realces, limitador).
