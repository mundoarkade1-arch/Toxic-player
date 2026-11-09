# Testes

Executar: `gradle testDebugUnitTest` (JDK 17, Android SDK 34). Compilar: `gradle assembleDebug`.

## Escritos (ainda NÃO executados no ambiente de desenvolvimento, que não tinha SDK/Gradle)
- `CloudLogicTest`: formatos, JSON do Drive (arquivos, changes, about, erros), planejamento de sincronização (novos/alterados/removidos, incremental, não-áudio), busca/filtro/ordenação, ids estáveis e ida e volta do índice, classificação de erros HTTP/rede/espaço, backoff, mensagens sem segredos, API sem sessão.
- `TagReaderTest`: ID3v2 com capa, duração CBR/Xing, ID3v1, FLAC, M4A (moov no fim), WAV, Opus, arquivo corrompido, leitura parcial (poucas requisições).

## Sem teste automatizado (exigem aparelho/conta Google)
Login OAuth e desconexão com revogação, listagem real no Drive, Range real (use o botão "Testar streaming" na aba Cloud), reprodução em segundo plano, Bluetooth, tela de bloqueio, expiração de token durante a música, queda de rede, downloads offline.

## Roteiro manual
1. Conectar conta; confirmar nome/e-mail e contagem. 2. Tocar FLAC e MP3, fazer seek. 3. Modo avião durante a música: mensagem e retomada ao voltar a rede. 4. Baixar uma faixa, ativar modo avião, tocar. 5. Remover arquivo no Drive e sincronizar. 6. Desconectar e verificar que cache/downloads sumiram.
