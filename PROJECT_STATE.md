# Estado do projeto

## Toxic Cloud — implementado (código escrito)
Autorização Google (AuthorizationClient), cliente Drive v3 (list, changes, Range, download), leitores de tags por Range (ID3v1/v2, FLAC, Ogg Vorbis/Opus, MP4/M4A, WAV), índice JSON local, sincronização completa e incremental (Changes), streaming com cache LRU em disco, downloads offline com verificação de tamanho e MD5, aba Cloud (conta, sincronizar, filtros, busca, ordenação, indicadores, cache, offline), tratamento de erros em português.

## Verificação — IMPORTANTE
O ambiente onde o código foi escrito não tinha Android SDK, Gradle nem internet. **Nada foi compilado nem executado.** Testes unitários foram escritos (ver TESTING.md) mas ainda não rodaram. Primeiro passo obrigatório: `gradle testDebugUnitTest assembleDebug` (ou o workflow do GitHub) e corrigir eventuais erros de compilação.

## Pendente do usuário
Configurar Google Cloud (docs/GOOGLE_DRIVE_SETUP.md): projeto, Drive API, tela de consentimento, clientes OAuth Android (debug e release, com SHA-1) e usuário de teste.

## Não validado em aparelho
Login real, Range no Drive, streaming em segundo plano/Bluetooth/tela de bloqueio, retomada após queda de rede, downloads offline.

## Playlists do site (JSON no Drive) — lógica pronta, SEM TELA (etapas 1 e 2 do Prompt 2)
**Feito (código escrito; testes só rodaram numa JVM de teste, o app ainda não foi compilado pelo Actions):**
- `cloud/SmartPlaylists.kt`: leitor tolerante do JSON `toxic-playlists` (v2), casamento de faixas (1º `id`, 2º `fn` sem diferenciar maiúsculas, 3º título + artista normalizados), regras `artista`/`pasta`/`lista`, mesclagem que **nunca remove** faixa, contagem de faixas novas e de não encontradas, textos em português simples.
- `cloud/SmartPlaylistManager.kt`: guarda o JSON e as regras em SharedPreferences (`toxic_smart`), reaplica depois de CADA sincronização do Drive e ao abrir o app, lê o JSON direto do Drive (pasta "Toxic Playlists", só leitura), só baixa de novo se o `modifiedTime` mudou.
- `DriveApi`: `findPlaylistsFile()` e `readTextFile()`. `DriveJson.parseNamedFiles`. `CloudConfig.PLAYLISTS_FOLDER/FILE`.
- `CloudManager`: `onSynced` (chamado após sincronizar) e `appendMessage`. `PlayerViewModel`: `smart`, `importSmartPlaylistsJson(texto)`, `syncSmartPlaylistsNow()`; apagar playlist avisa o `smart` para não recriá-la.
- Testes: `app/src/test/.../cloud/SmartPlaylistsTest.kt` (28 testes).
- Regras de comportamento: faixa que o usuário tirou de uma playlist não volta; playlist apagada pelo usuário não é recriada (importar o arquivo à mão libera de novo); sem internet no modo automático = silêncio.
**Falta (telas, só com autorização):** botão "Importar playlists (.json)" ao lado do M3U (`ui/Screens.kt`, ~linha 422), botão "Sincronizar playlists do Drive" e o retorno destacado ("3 playlists atualizadas, +27 faixas, 4 não encontradas" + lista das não encontradas, que já está em `vm.smart.lastReport`). Hoje o resultado aparece na mensagem da aba Cloud.
**Não validado:** build no Actions, leitura real do Drive, aparelho.
