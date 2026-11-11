# Changelog

## Não lançado
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
