# Changelog

## Não lançado
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
