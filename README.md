# Toxic Player

Player Android para músicas guardadas no aparelho, com busca local, biblioteca por pastas/artistas/álbuns, playlists pessoais, favoritas, fila de reprodução e controles de áudio.

## Recursos

- Reprodução local em segundo plano com Android Media3/ExoPlayer.
- Busca por título, artista, álbum e pasta.
- Biblioteca organizada por pastas, artistas e álbuns.
- Playlists M3U/M3U8, fila, favoritos, aleatório e repetição.
- Velocidade, tom, timer e transição entre músicas.
- Equalizador gráfico e paramétrico, compressor, compressor multibanda, limitador, medidores e perfis.
- Importação/exportação de presets JSON e importação de Equalizer APO/AutoEQ.

## Estrutura oficial do código

O código Android ativo fica exclusivamente em `app/src/main/java/`. Os testes ficam em `app/src/test/java/`. Não mantenha cópias Kotlin soltas na raiz: elas não são compiladas pelo Gradle.

## Abrir e compilar

Abra a pasta raiz no Android Studio e aguarde a sincronização do Gradle. O projeto usa Gradle 8.7, Java 17 e Android SDK 34. O APK de depuração pode ser compilado com:

```bash
gradle testDebugUnitTest assembleDebug
```

O projeto ainda não inclui os scripts e o JAR do Gradle Wrapper. O GitHub Actions instala Gradle 8.7 automaticamente, executa os testes e publica o APK de depuração como artefato.

## Áudio e limitações

O DSP próprio é conectado ao pipeline Media3. O processador atual aceita PCM de 16 bits mono/estéreo; formatos incompatíveis passam sem processamento. Bass Boost, Loudness Enhancer e Virtualizer são efeitos Android opcionais e variam por fabricante/aparelho. O limitador usa detecção de pico entre amostras aproximada; não deve ser tratado como equipamento de medição certificado.

## GitHub Actions

- Push e Pull Request: executa testes, compila APK debug e disponibiliza o arquivo como artefato.
- Execução manual (`workflow_dispatch`): também tenta publicar uma versão assinada. Para isso, configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD` nos secrets do repositório.

Nunca envie keystore, senhas ou tokens para o repositório.

## Toxic Cloud (Google Drive)

A aba **Cloud** conecta sua conta Google e toca pela internet as músicas que já estão no seu Drive (MP3, FLAC, WAV, M4A, AAC, OGG, OPUS), sem baixar a biblioteca inteira e sem servidor próprio. Há índice local, sincronização incremental, cache de streaming com limite e downloads offline opcionais.

Antes do primeiro uso é preciso configurar o Google Cloud Console (uma vez): veja [docs/GOOGLE_DRIVE_SETUP.md](docs/GOOGLE_DRIVE_SETUP.md). Funcionamento e limitações: [docs/TOXIC_CLOUD.md](docs/TOXIC_CLOUD.md). Arquitetura: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md). Testes: [TESTING.md](TESTING.md).
