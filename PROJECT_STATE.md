# Estado do projeto

## Toxic Cloud — implementado (código escrito)
Autorização Google (AuthorizationClient), cliente Drive v3 (list, changes, Range, download), leitores de tags por Range (ID3v1/v2, FLAC, Ogg Vorbis/Opus, MP4/M4A, WAV), índice JSON local, sincronização completa e incremental (Changes), streaming com cache LRU em disco, downloads offline com verificação de tamanho e MD5, aba Cloud (conta, sincronizar, filtros, busca, ordenação, indicadores, cache, offline), tratamento de erros em português.

## Verificação — IMPORTANTE
O ambiente onde o código foi escrito não tinha Android SDK, Gradle nem internet. **Nada foi compilado nem executado.** Testes unitários foram escritos (ver TESTING.md) mas ainda não rodaram. Primeiro passo obrigatório: `gradle testDebugUnitTest assembleDebug` (ou o workflow do GitHub) e corrigir eventuais erros de compilação.

## Pendente do usuário
Configurar Google Cloud (docs/GOOGLE_DRIVE_SETUP.md): projeto, Drive API, tela de consentimento, clientes OAuth Android (debug e release, com SHA-1) e usuário de teste.

## Não validado em aparelho
Login real, Range no Drive, streaming em segundo plano/Bluetooth/tela de bloqueio, retomada após queda de rede, downloads offline.
