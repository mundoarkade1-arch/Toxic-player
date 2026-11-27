# Estado do projeto

## Equalizador / processador de carro (2026-11-26) — motor testado na JVM; tela e DspProcessor NÃO compilados
Ver `docs/AUDITORIA_EQUALIZADOR.md`. Para validar: Actions verde; Mesa de áudio > aba Carro; aplicar um preset de carro e ver **● ATIVO**; tocar música e ver "Motor processando" e as barras graves/médios/agudos; "Comparar: ouvir sem efeitos"; FLAC 24 bits com efeito.

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
**Telas feitas (autorizadas):** aba Biblioteca > Listas: botões "Importar playlists (.json)" e "Sincronizar playlists do Drive"; aba Cloud: botão "Sincronizar playlists do Drive"; cartão verde em destaque (`SmartInfoCard`) com o resultado e as faixas não encontradas, nas telas Listas, Cloud e Início da Cloud.
**Já validado no aparelho (antes das telas):** leitura do Drive, 35 playlists, +590 faixas, músicas tocando.
**(Texto antigo, já feito acima) Falta (telas, só com autorização):** botão "Importar playlists (.json)" ao lado do M3U (`ui/Screens.kt`, ~linha 422), botão "Sincronizar playlists do Drive" e o retorno destacado ("3 playlists atualizadas, +27 faixas, 4 não encontradas" + lista das não encontradas, que já está em `vm.smart.lastReport`). Hoje o resultado aparece na mensagem da aba Cloud.
**Não validado:** build no Actions, leitura real do Drive, aparelho.

## Reparo de capas (2026-11-26) — NÃO compilado, NÃO testado
- Capas movidas de `cacheDir` para `filesDir/toxic_cloud/covers` (`CloudStorage.coversDir`). `CloudRepository.repairCovers()` (chamado no início de `enrichPending`) requeue: hasCover sem arquivo, e uma vez por `COVER_SCAN` as músicas DONE sem capa. `CloudManager` init chama `enrichPending` ao abrir conectado. `PlaylistCover` procura capa pelo arquivo (`CloudStorage.coverFile(id).exists()`) em IO.
- Validar: abrir app conectado, ver "Lendo informações", capas das músicas e das playlists (mosaico de 4) aparecendo; "Limpar cache" nos Ajustes e abrir de novo traz as capas de volta.

## Busca de playlists e mosaico (2026-11-26) — NÃO compilado, NÃO testado no aparelho
- `cloud/PlaylistSearch.kt` (puro): `score(query, nome, nomesPorMúsica, total)` -> BY_NAME / BY_CONTENT (>=40% das músicas) / NO_MATCH; compara sem acento, caixa e pontuação. Teste: `PlaylistSearchTest`.
- `ui/PlaylistSearchResults.kt`: `rememberPlaylistMatches` e `PlaylistSearchRow`. Usados em `CloudHome` (busca do Início) e `DriveSongs` (lista de músicas, novo parâmetro `onOpenPlaylist`).
- `PlaylistCover`: 4 imagens diferentes (fingerprint = tamanho do arquivo da capa na nuvem; álbum no local), fresta de 1,5dp, parâmetros `shape` e `coverPx`. `PlaylistDetail` usa o mesmo componente na capa grande.
- Para validar: buscar "Charlie Brown Jr" mostra a playlist/pasta no topo; playlists com 4+ capas mostram mosaico; playlist sem nenhuma capa embutida continua com o degradê.

## Capa de playlist (2026-11-22) — NÃO compilado, NÃO testado
- `PlaylistCover` (`ui/PlaylistsScreen.kt`): `remember` agora depende de `vm.cloud.playerTracks`; olha todas as músicas; `distinctBy` álbum; até 4 capas (mosaico se 4, senão a primeira). Sem nenhuma capa: degradê com letras.

## Visual líquido, Início do Drive, Biblioteca offline (2026-11-22) — código escrito, NÃO compilado, NÃO testado no aparelho
- Abas: 0 Início (`ui/CloudScreen.kt` > `DriveHub`: página 0 `CloudHome`, 1 todas as músicas com filtros (`DriveSongs`), 2 grade de playlists (`PlaylistsSection`), e `PlaylistDetail` por cima), 1 Biblioteca (`ui/OfflineLibrary.kt`: baixadas + músicas do celular), 2 Ajustes. Buscar e `LibraryHub` foram removidos; `CloudMode` também.
- `goPlayer` agora é real (`showPlayer = true` no `App`): tocar música abre o Player; o voltar minimiza.
- `UpdatePrompt` (em `Screens.kt`): janela ao abrir se `vm.update != null`; "Depois" vale até fechar o app.
- `Theme.kt`: `glass()` com brilho especular/reflexo/filete. `SearchField` com cores de vidro. `SongRow` (em `PlaylistDetail.kt`) é a linha de música compartilhada.
- `FolderPlaylists.MIN_TRACKS = 20`. `runDailyDiscoveries` não cria mais nada; apaga "Descobertas do dia" com menos de 20. `cleanupOldSmart` também apaga playlists de pasta antigas que ficaram abaixo do mínimo (guarda os nomes em `made`).
- Ícone: `res/drawable/ic_launcher_{background,foreground,monochrome}.xml` (disco de vidro + barras de equalizador).
- Para validar: Actions verde; Início com busca e playlists sem texto cortado; tocar música abre o Player e o voltar minimiza; Biblioteca só com baixadas e músicas do celular; nenhuma playlist com menos de 20 músicas; janela de atualização ao abrir quando há versão nova; ícone novo.

## Só pastas viram playlist (2026-11-22) — código escrito, NÃO compilado, NÃO testado no aparelho
- `SmartPlaylistManager` não lê mais o JSON do site/motor no Drive: `syncFromDrive` só refaz as playlists das pastas (`FolderPlaylists`). `withFolders` devolve só o arquivo das pastas. `importText` ficou desligado (aviso).
- `FolderPlaylists.MIN_TRACKS = 5`: pasta com menos músicas não vira playlist.
- `cleanupOldSmart` (uma vez, só com as pastas já carregadas): apaga playlists criadas antes pelo arquivo do site/motor, se tudo nelas foi posto pelo app (`added()`) e o nome não é de uma pasta; depois apaga o arquivo guardado. Usa o novo parâmetro `remove` do manager (ligado no `PlayerViewModel`).
- Para validar: Início só com Curtidas, playlists suas e as pastas (40–50 músicas); sumiram as de 1–2 músicas; tocar numa abre a tela da playlist.

## Tela de playlist estilo Spotify e progresso (2026-11-22) — código escrito, NÃO compilado, NÃO testado no aparelho
- `ui/PlaylistDetail.kt` (novo): `PlaylistDetail` (capa grande da 1ª música com capa, ou degradê com iniciais; nome por cima; artistas mais frequentes; origem; tempo total; Baixar/Aleatório/Tocar; lista de músicas). `LibraryHub` (`ui/PlaylistsScreen.kt`) mostra esta tela em tela cheia quando há playlist aberta (sem cabeçalho nem abas); o voltar fecha a playlist. O antigo `TrackList` continua para pastas, artistas e álbuns.
- Baixar usa `cloud.downloadAll` com confirmação; mostra ícone de progresso enquanto baixa e "baixada" quando tudo está salvo.
- `AddToListDialog(vm, track)` em `ui/Screens.kt` virou `internal` para a nova tela usar.
- Progresso: `SyncStatus` (agora `internal`, com porcentagem) e `SyncBanner` (`ui/CloudScreen.kt`) aparecem no Início e na Biblioteca quando `cloud.syncing` ou `cloud.progress` não é Idle, depois de 1 s.
- Para validar: abrir playlist (capa, nome, botões); Tocar/Pausar; Aleatório; Baixar; menu das músicas; apagar pelo menu da capa; barra de progresso aparecendo numa atualização longa.

## Início abre a playlist (2026-11-22) — código escrito, NÃO compilado, NÃO testado no aparelho
- Tocar numa playlist no Início chama `onOpenPlaylist` (`ui/CloudHome.kt`): `CloudScreen` guarda o nome em `vm.openPlaylistRequest`, limpa o grupo aberto e troca para a aba Biblioteca. `LibraryHub` (`ui/PlaylistsScreen.kt`) lê o pedido uma vez e abre direto na playlist (tela `TrackList`: Tocar tudo, Aleatório, músicas).
- Os atalhos de playlist (até 3) também abrem a playlist. "Músicas curtidas", "Adicionadas recentemente" e "Aleatório" continuam tocando direto.
- O bloco "Continuar ouvindo" saiu do Início (o item 8 do plano acima não vale mais nesse ponto). O mini player continua guardando a última música.
- O botão voltar do celular, dentro da playlist, volta para a lista de playlists da Biblioteca (não para o Início).
- Para validar: tocar numa playlist no Início abre a lista; "Tocar tudo" toca; escolher uma música toca ela; o Início não mostra "Continuar ouvindo".

## Pastas do Drive viram playlists — código escrito, NÃO compilado, NÃO testado no aparelho (2026-11-21)
- `cloud/FolderPlaylists.kt` (novo, lógica pura): cada pasta com música direto dentro vira uma `SmartPlaylist` com origem "Pasta" (nome = nome da pasta; nome repetido em outro lugar = caminho "A / B"; raiz do Drive e músicas soltas ignoradas; subpasta não junta na pasta de cima).
- `SmartPlaylistManager`: `withFolders` junta (`SmartMerge`) o arquivo do site/motor com as pastas, antes de aplicar. Roda ao abrir o app e depois de cada sincronização; vale mesmo sem arquivo no Drive. `origins()` e `forget()` também conhecem as playlists de pasta (para o Início marcar como pasta e para playlist apagada não voltar). A assinatura que pula a conta agora inclui os caminhos das pastas (renomear pasta refaz a conta).
- Tela: nenhuma mudança. O Início já lista todas as playlists em "Suas playlists" (`ui/CloudHome.kt`), as de pasta aparecem com a marca de pasta.
- Regras mantidas: nunca remove faixa; faixa tirada pelo usuário não volta; playlist apagada não é recriada. Limites conhecidos: pasta renomeada gera playlist nova (a antiga fica); música movida de pasta continua na playlist antiga.
- Testes: `app/src/test/.../cloud/FolderPlaylistsTest.kt` (7 testes, ainda não rodaram).
- Para validar: Actions verde; Início mostrando as pastas; pasta nova e música nova aparecendo depois de sincronizar; apagar uma playlist de pasta e ela não voltar.

## Nova navegação estilo Spotify (etapa 1) — código escrito, ainda não compilado pelo Actions
Abas: **Início** (playlists, mixes, descubra), **Buscar** (procura no Drive; menu da música guarda em playlist), **Biblioteca** (Playlists com capas genéricas + Músicas do Drive), **No aparelho** (músicas do celular; permissão só aqui), **Ajustes** (Conta e sincronização, Áudio e equalizador, Atualização do app). O player fica recolhido (mini player) e só abre ao tocar nele; a fila é restaurada na última música.
Arquivos: `ui/Screens.kt` (App/NavBar), `ui/CloudScreen.kt` (Início/Buscar/DriveSongs/CloudSettings), `ui/PlaylistsScreen.kt` (LibraryHub, capas), `ui/SettingsScreen.kt`, `ui/CloudHome.kt` (seção "Suas playlists").
**(Antigo — substituído pelo item 9 abaixo)** Abas antigas: Início, Buscar, Biblioteca, No aparelho, Ajustes. Agora são 4 (Início, Buscar, Biblioteca, Ajustes).

## Velocidade (etapa de desempenho)
- Playlists do Drive: `Regex` agora é compilada uma vez (antes recompilava milhares de vezes a cada abertura, na thread principal); palavras de artista e pastas calculadas uma vez; a conta roda em segundo plano (Dispatchers.Default) e é PULADA se biblioteca e arquivo não mudaram (assinatura guardada em `toxic_smart/sig`). `CloudLibrary.normalize` também usa Regex pré-compilada (ajuda busca e filtros).
- Player: `DefaultLoadControl` (começa a tocar com 1 s de áudio, guarda até 2 min à frente).
- Capas de playlist: mosaico de 4 capas / capa da primeira música; sem capa, degradê com símbolo.
- Pré-carregamento FEITO: `CloudPlayback.prefetch` guarda 1 MB do começo das 2 próximas músicas (thread de prioridade mínima, espera 20 s de buffer à frente). Ideias futuras: cache de miniaturas em disco; Baseline Profile.

## Plano "mais parecido com o Spotify" (9 itens, um por vez)
1. Notificação de conta caída — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
2. Botões de manutenção para Ajustes > Avançado — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
3. Mensagens de sincronização só em caso de erro — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
4. Biblioteca limpa (Playlists e Músicas, busca em cima, filtros atrás de botão) — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
5. Linhas de música simples — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
6. Primeira conexão mais curta — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
7. "No aparelho" dentro da Biblioteca/Ajustes; barra com 4 abas — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
8. Início com "continuar ouvindo" e playlists — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
9. Funcionar sem internet — **FEITO (código escrito, ainda não compilado pelo Actions nem testado no aparelho)**
Regras: não mudar cores nem o estilo de vidro; não mudar o formato do `toxic-playlists` v2.

### Item 1 — notificação de conta caída
- `cloud/CloudAuthNotifier.kt` (novo): cria e mostra a notificação "Reconecte sua conta do Google" (canal `toxic_account`), sem repetir enquanto a anterior não for resolvida; `clear` apaga e libera a próxima. Pede permissão só no Android 13+ e só uma vez.
- `CloudManager`: `notifyReconnectNeeded()` (chamado quando a sincronização falha com ACCESS_REVOKED ou TOKEN_EXPIRED), `askNotifPermission`/`notifPermissionHandled`, `reconnectFromNotification()`. O aviso some ao reconectar, ao sincronizar com sucesso ou ao desconectar de propósito.
- `PlayerViewModel`: avisa também quando a música para porque a sessão do Google expirou e a renovação silenciosa falhou.
- `MainActivity`: `onNewIntent`/intent inicial com `toxic_reconnect` chama a reconexão; diálogo curto "Avisar se a conta cair?" antes de pedir `POST_NOTIFICATIONS`.
- `AndroidManifest.xml`: `MainActivity` com `launchMode="singleTop"`.
- A tela de consentimento do Google continua em `CloudAuthEffect` (Screens.kt), que já vale em todas as abas.
- **Não validado:** compilação (sem Android SDK aqui), toque na notificação num aparelho real, Android 13+ pedindo permissão.

### Itens 2 a 9 — o que mudou (tudo em código escrito, NÃO compilado, NÃO testado no aparelho)
- **Barra de baixo com 4 abas** (Início, Buscar, Biblioteca, Ajustes). "No aparelho" virou Ajustes > "Músicas do aparelho" (página 5 de `SettingsScreen`; a permissão de música só é pedida ali). O botão "No aparelho" do aviso de "sem internet" abre direto essa página (`settingsStart = 5`).
- **Ajustes** (`ui/SettingsScreen.kt`): Conta do Google, Áudio e equalizador, Músicas do aparelho, Atualização do app, Privacidade e a seção **AVANÇADO** > "Sincronização e armazenamento" (`CloudSettings(vm, advanced = true)`): Sincronizar, Atualizar biblioteca, Sincronizar playlists do Drive, Importar playlists (.json), cartão de resultado das playlists, cache, downloads e teste de streaming. A página "Conta do Google" ficou só com o cartão da conta, Trocar conta e Desconectar.
- **Mensagens** (`CloudManager.doSync`): sucesso não gera mais mensagem ("+3 novas", "Biblioteca já está em dia" somem). Só erros. Erro de "sem internet" durante a sincronização não vira mensagem (o aviso leve de rede cobre). `SmartInfoCard` (resultado das playlists do Drive) só aparece em Avançado. O Início não tem mais "Sincronizando sua biblioteca..." nem botão "Biblioteca".
- **Biblioteca** (`ui/PlaylistsScreen.kt`, `ui/CloudScreen.kt`): só "Playlists" e "Músicas"; busca no topo (playlists: filtra por nome; músicas: `cloud.query`); botão pequeno (ícone Tune, acende quando há filtro) abre `FiltersDialog` (Ver por, Ordem, Mostrar, Formato). Playlists: um botão "Nova playlist" com menu (vazia / por intenção). Sem os botões Sincronizar, Importar .json.
- **Linha de música** (`CloudTrackRow`): capa, título, artista, coração, menu. Sem formato, tamanho, "nuvem/cache/streaming". Só aparece o anel de progresso enquanto baixa.
- **Primeira conexão** (`ConnectCard`): uma frase, botão "Entrar com o Google" e uma linha pequena "Só leitura. Você sai quando quiser."
- **Início** (`ui/CloudHome.kt`): saudação, "Continuar ouvindo" (música atual retoma de onde parou + últimas ouvidas pelo histórico `vm.history`), "Suas playlists", depois atalhos, mixes, recentes, artistas, gêneros.
- **Sem internet** (`CloudManager.online`, `ConnectivityManager.registerDefaultNetworkCallback`): faixa leve "Sem internet. Mostrando o que está salvo no aparelho." com atalho "No aparelho"; Início, Buscar e Biblioteca > Músicas mostram só músicas baixadas; a sincronização automática não roda sem rede e roda quando a rede volta (se passaram 6 h). Músicas de playlists que não foram baixadas ainda tentam tocar e mostram o erro de rede de sempre.
- **Não mudou:** cores, estilo de vidro, formato do `toxic-playlists` v2, testes automáticos existentes (nenhum deles toca a interface).
- **Para validar:** build do Actions; login/reconexão; Ajustes > Avançado; filtros; modo avião (faixa de "sem internet" e lista só com baixadas); "Continuar ouvindo" retomando a posição.
