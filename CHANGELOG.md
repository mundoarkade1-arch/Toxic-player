# Changelog

## Não lançado
### Adicionado
- Toxic Cloud: conexão com Google Drive, biblioteca na nuvem com indexação local, sincronização incremental (Changes API), streaming com Range e cache LRU configurável, downloads offline opcionais, aba Cloud, tratamento de erros, testes unitários e documentação.
### Alterado
- `Track` ganhou `cloudId` e `coverStamp`; `PlaybackService` usa fonte de dados com Drive autenticado; `PlayerViewModel` unifica faixas locais e da nuvem; erro de rede em faixa da nuvem pausa e retoma em vez de pular.
- Manifesto: permissão `ACCESS_NETWORK_STATE`. Dependências: `play-services-auth`, `media3-datasource`, `media3-database`.
- Adicionado `.gitignore` (keystores, credenciais, build).
