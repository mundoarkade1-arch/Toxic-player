# Changelog

## Não lançado
### Corrigido (playlist sem capa)
- A capa da playlist agora é refeita quando as capas da nuvem chegam (antes ficava presa no desenho genérico até a playlist mudar) e procura capa na playlist inteira, não só nas 60 primeiras músicas. O mosaico prefere álbuns diferentes. Se nenhuma música tem imagem embutida, continua o desenho genérico (degradê com as letras do nome). Arquivo: `ui/PlaylistsScreen.kt`. Ainda não compilado nem testado no aparelho.
### Alterado (visual líquido, Início do Drive, Biblioteca offline, player, atualização, ícone)
- Visual "vidro líquido": o `glass` ganhou brilho especular curvo, reflexo verde, filete de luz na borda e borda que acende nos cantos; barra de busca em vidro com borda verde ao digitar. Valem em todas as telas.
- Início reorganizado: saudação, busca no topo (acha músicas e playlists), 3 atalhos (Curtidas, Recentes, Aleatório), "Suas playlists" em cartões de vidro com o nome em até 2 linhas e a quantidade de músicas sempre visível (antes cortava), "Suas músicas" (Todas, Artistas, Álbuns, Gêneros, Pastas, Recentes) e os mixes. "Ver todas" abre a grade de playlists; "Todas as músicas" abre a lista com filtros. TUDO do Drive fica no Início.
- A aba Buscar saiu (a busca está no Início). Abas agora: Início, Biblioteca, Ajustes.
- Biblioteca (novo `ui/OfflineLibrary.kt`): só o que está salvo no aparelho: "Baixadas" (do Drive) e "No celular", com busca, Tocar tudo e Aleatório. Nada do Drive que não esteja baixado aparece aí. Saiu "Músicas do aparelho" de Ajustes e os botões de importar/sincronizar playlists (.json) do Avançado.
- Tocar numa música (em playlist, busca, lista ou atalhos) agora abre o Player. O voltar do Player só minimiza.
- Playlists só de pastas com NO MÍNIMO 20 músicas (`FolderPlaylists.MIN_TRACKS`). As "Descobertas do dia" automáticas foram desligadas (e a que existir com menos de 20 é apagada). Limpeza automática apaga playlists antigas criadas pelo app que não valem mais (só se tudo nelas foi posto pelo app).
- Atualização: ao abrir o app, se houver versão nova, aparece uma janela "Nova versão disponível" com "Atualizar agora" ou "Depois" (não fecha tocando fora). O pontinho em Ajustes continua.
- Ícone novo: disco de vidro com borda em degradê e ondas de som (equalizador) nas cores do app; versão monocromática atualizada.
- Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Alterado (só as pastas viram playlist)
- As playlists do app agora vêm SÓ das pastas do Drive (nome da pasta = nome da playlist). Os arquivos de playlists do site e do motor não são mais lidos, porque criavam playlists de 1 ou 2 músicas. Pasta com menos de 5 músicas não vira playlist (`FolderPlaylists.MIN_TRACKS`).
- Limpeza única: as playlists que esses arquivos já tinham criado são apagadas do app se só tiverem músicas postas pelo app (se você pôs uma música à mão, a playlist fica) e se não forem de uma pasta. Nada é apagado no Drive.
- Importar arquivo de playlists ficou desligado (mostra um aviso). Arquivos: `cloud/SmartPlaylistManager.kt`, `cloud/FolderPlaylists.kt`, `PlayerViewModel.kt`, teste `FolderPlaylistsTest`. Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Alterado (tela de playlist no estilo Spotify + progresso da atualização)
- Playlist aberta agora ocupa a tela toda: capa grande com o nome por cima, artistas, origem (pasta do Drive, sua playlist...), total de músicas e tempo ("3h 10min"), botões Baixar (só músicas da nuvem, com confirmação), Aleatório e Tocar/Pausar (verde, grande) e a lista de músicas com capa, título, artista e menu de três pontinhos (curtir, tocar a seguir, fila, adicionar à lista, remover). Apagar a playlist ficou no menu do canto da capa, com confirmação. Não tem botão de compartilhar de propósito (o app nunca expõe link do Drive). Arquivo novo: `ui/PlaylistDetail.kt`.
- Faixa de progresso da atualização automática no Início e na Biblioteca ("Atualizando suas músicas", porcentagem e "Lendo as informações: X de Y"). Só aparece se a atualização passar de 1 segundo, para não piscar. Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Alterado (Início: abrir a playlist em vez de tocar)
- Tocar numa playlist no Início (cartões de "Suas playlists" e os 3 atalhos de playlist) agora abre a playlist na Biblioteca, com "Tocar tudo", "Aleatório" e a lista de músicas para escolher. Antes ela saía tocando.
- Removido o bloco "Continuar ouvindo" do Início: o mini player já guarda a última música. Arquivos: `ui/CloudHome.kt`, `ui/CloudScreen.kt`, `ui/PlaylistsScreen.kt`, `PlayerViewModel.kt` (`openPlaylistRequest`). Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Adicionado (pastas do Drive viram playlists)
- Cada pasta do Drive que tem música direto dentro vira uma playlist com o nome da pasta, e aparece em "Suas playlists" no Início (marcada como pasta). Pasta nova, música nova na pasta e pasta renomeada entram sozinhas depois de cada sincronização. Vale também sem o arquivo de playlists do site. Duas pastas com o mesmo nome em lugares diferentes ficam com o caminho no nome ("Música / Rock"). Música em subpasta fica só na playlist da subpasta. Nunca remove faixa, respeita o que o usuário tirou e não recria playlist apagada (mesma regra das playlists do site). Pasta renomeada cria uma playlist nova; a antiga continua até o usuário apagar. Código novo em `cloud/FolderPlaylists.kt`, ligação em `SmartPlaylistManager`; testes em `FolderPlaylistsTest`. Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Alterado (app mais parecido com o Spotify, itens 2 a 9)
- Barra de baixo com 4 abas: Início, Buscar, Biblioteca, Ajustes. "No aparelho" foi para Ajustes > Músicas do aparelho.
- Botões de bastidor (sincronizar, atualizar biblioteca, importar playlists .json, sincronizar playlists do Drive, cache, downloads) foram para Ajustes > Avançado. O app continua sincronizando sozinho.
- Mensagens de sincronização só aparecem em caso de erro; o Início não mostra mais "sincronizando" nem o botão Biblioteca.
- Biblioteca limpa: só Playlists e Músicas, com busca no topo; filtros, visões e ordem atrás de um botão pequeno.
- Linhas de música simples: capa, título, artista, coração e menu.
- Primeira conexão com texto curto e botão "Entrar com o Google".
- Início com "Continuar ouvindo" e "Suas playlists" no topo.
- Sem internet: faixa leve de aviso e só as músicas salvas no aparelho; sincronização automática pausa e volta com a rede. Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Adicionado (aviso de conta caída)
- Notificação "Reconecte sua conta do Google" quando a conta do Drive cai ou o acesso é revogado (na sincronização ou durante a música). Tocar nela abre o app e a reconexão. A permissão de notificação só é pedida nesse momento, com explicação curta, uma vez. Código escrito; ainda não compilado pelo Actions nem testado no aparelho.
### Adicionado (playlists do motor)
- O app lê também o `playlists-motor.json` (gerado pelo Toxic Music Engine, procurado em qualquer pasta do Drive) e junta com o `playlists-inteligentes.json` do site (`SmartMerge`). Nunca remove faixas escolhidas à mão e não recria playlist apagada pelo usuário. Testes em `SmartMergeTest`. Código escrito e testes unitários criados; ainda não validado no celular.
### Adicionado (velocidade)
- Pré-carregamento: com a música atual já bem à frente no buffer, o app guarda no cache o começo (1 MB) das 2 próximas músicas da nuvem, em segundo plano e com prioridade mínima; funciona em 3G sem atrapalhar a música tocando. Cache de capas proporcional à memória do aparelho.
### Alterado (navegação)
- App com abas Início, Buscar, Biblioteca, No aparelho e Ajustes; player recolhido (mini player) que abre ao tocar; sincronização, equalizador e atualização foram para Ajustes; playlists com capa genérica colorida.
### Adicionado (playlists do site)
- Leitura do arquivo `playlists-inteligentes.json` (pasta "Toxic Playlists" do Drive): cria/atualiza playlists sozinhas depois de cada sincronização, sem remover faixas escolhidas à mão. Testes em `SmartPlaylistsTest`.
- Telas: botões "Importar playlists (.json)" e "Sincronizar playlists do Drive" e cartão de resultado em destaque.
### Redesign visual (UX/UI)
- Novo visual "vidro" (glass): fundo aurora que acompanha a cor da capa, cartões de vidro com borda de brilho, botões com degradê verde → limão, diálogos e menus no mesmo estilo.
- Novo ícone do app (trevo de radiação com botão de play, com versão monocromática para ícones temáticos do Android 13+), tela de abertura e widget redesenhados.
- Mini player em todas as telas; Player com capa grande, fundo desfocado (Android 12+), botão de favoritar, indicador de posição na fila e layout horizontal.
- Biblioteca: busca em pílula, abas segmentadas, botão "Aleatório", música atual destacada (por id, não por título) com barrinhas animadas, estados vazios.
- Equalizador: seções recolhíveis (compressor, limitador, multibanda, efeitos, presets), curva de resposta com preenchimento, medidores em degradê.
- Cloud: tela de conexão, cartão da conta e listas com o novo visual. Nenhuma mudança de lógica, login ou armazenamento.
### Adicionado
- Toxic Cloud: conexão com Google Drive, biblioteca na nuvem com indexação local, sincronização incremental (Changes API), streaming com Range e cache LRU configurável, downloads offline opcionais, aba Cloud, tratamento de erros, testes unitários e documentação.
### Alterado
- `Track` ganhou `cloudId` e `coverStamp`; `PlaybackService` usa fonte de dados com Drive autenticado; `PlayerViewModel` unifica faixas locais e da nuvem; erro de rede em faixa da nuvem pausa e retoma em vez de pular.
- Manifesto: permissão `ACCESS_NETWORK_STATE`. Dependências: `play-services-auth`, `media3-datasource`, `media3-database`.
- Adicionado `.gitignore` (keystores, credenciais, build).

### Adicionado (gêneros e recentes)
- Aba Cloud: visões **Gêneros** (listas automáticas por gênero das tags, ex.: Funk, Rock) e **Recentes**; essas listas mostram sempre as músicas mais novas do Drive no topo. Menu da música ganhou "Adicionar à lista...".
- Índices antigos são migrados sozinhos (varredura completa + releitura de tags uma vez).
- Aba Cloud: visão **Pastas** (pastas do Drive com subpastas; tocar uma pasta toca também as subpastas). A busca também encontra pelo nome da pasta. Migração automática do índice (schema 3).
- Aba Cloud agora abre numa **tela inicial** estilo plataforma de streaming: saudação, atalhos (curtidas, recentes, aleatório, suas playlists), "Feito para você" (Mix do dia, Mix por gênero e por artista), carrossel de recentes, seus artistas, gêneros e "Redescubra". O botão "Biblioteca" leva à navegação completa.
