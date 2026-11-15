# Changelog

## Não lançado
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
