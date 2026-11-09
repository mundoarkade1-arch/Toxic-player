# Arquitetura

```
ui/CloudScreen ──► PlayerViewModel.cloud (CloudManager: estado Compose, ações)
                      ├─ CloudAuth        (OAuth, token em memória)
                      ├─ CloudRepository  (índice, sync completa/incremental, enriquecimento)
                      │    ├─ DriveApi / DriveJson   (REST, retry, Range, download)
                      │    ├─ SyncPlanner            (diff puro)
                      │    ├─ tags/TagReader         (ID3, FLAC, Ogg, MP4, WAV via RangeSource)
                      │    └─ CloudStore             (index.json atômico)
                      └─ CloudStorage     (SimpleCache LRU, capas, offline)
PlaybackService ──► CloudPlayback (ResolvingDataSource + CacheDataSource) ──► Drive
```
- Faixas locais e da nuvem compartilham fila, favoritos, playlists, DSP e restauração de sessão (`Track.cloudId`, id negativo para nuvem).
- Persistência: `filesDir/toxic_cloud` (índice, offline), `cacheDir/toxic_cloud` (capas, áudio em cache), prefs `toxic_cloud` (conectado, e-mail, token de mudanças, última sync).
- Sem backend. Dependências novas: `play-services-auth`, `media3-datasource`, `media3-database`.
