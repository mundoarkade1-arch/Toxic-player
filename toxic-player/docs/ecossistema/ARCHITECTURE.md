# ARCHITECTURE — arquitetura compartilhada (proposta)
Princípio: sem backend. O Google Drive é a ponte. Dados em JSON simples.

## Diagrama 1 — Arquitetura (sólido = existe, tracejado = planejado)
```mermaid
flowchart LR
  DZ[Deezer API pública] --> GA[GitHub Action diária]
  GA --> CH[data/charts.js]
  CH --> SITE[Site Musical - navegador]
  YT[YouTube Data API] <--> SITE
  PC[Pasta de músicas do PC] --> SITE
  SITE -->|sobe arquivos| DR[(Google Drive)]
  SITE -->|playlists-inteligentes.json| DR
  SITE -.->|lista de pendentes| MD[Media Downloader]
  MD -.->|arquivos baixados| PC
  DR -->|leitura + tags por Range| APP[Toxic Player]
  APP -.->|histórico local| HIS[(Histórico no aparelho)]
```

## Diagrama 3 — Caminho de uma música
```mermaid
flowchart TD
  A[Descoberta: Top 200 Deezer - AUTOMÁTICO] --> B[Casa com a pasta do PC - AUTOMÁTICO]
  B --> C{Já tem?}
  C -->|sim| H[Entra na playlist JSON]
  C -->|não| D[Lista de pendentes - PLANEJADO]
  D --> E[Media Downloader - MANUAL]
  E --> F[Pasta do PC]
  F --> G[Site sobe ao Drive - semiautomático, usuário clica]
  G --> H
  H --> I[App sincroniza o Drive - usuário abre/sincroniza]
  I --> J[Playlist atualizada no Toxic Player]
```

## Diagrama 5 — Playlists inteligentes (PLANEJADO, nada disso existe ainda)
```mermaid
flowchart LR
  P[Player: tocou, terminou, pulou, repetiu, favoritou] --> L[ListeningHistoryManager]
  L --> T[UserTasteProfile]
  T --> R[MusicRecommendationEngine]
  J[JSON do site + tags do Drive] --> R
  R --> G[ContextualPlaylistGenerator]
  G --> S[SmartPlaylistManager existente]
  S --> U[Usuário]
  U -->|feedback| P
```
Regras: pular não é rejeição definitiva; nunca remover música manual; sem duplicata; histórico só no aparelho; explicar o motivo da sugestão.

## Contrato de dados (REAL, usar este)
```json
{ "formato": "toxic-playlists", "v": 2, "gerado": "ISO-8601",
  "playlists": [ { "nome": "…", "origem": "Deezer",
    "regra": { "tipo": "lista", "itens": [ { "t": "título", "a": "artista" } ] },
    "faixas": [ { "id": "opcional", "fn": "arquivo.mp3", "p": "pasta", "t": "título", "a": "artista" } ] } ] }
```
Casamento no app: 1º `id`, 2º `fn`, 3º título+artista normalizados.
Mudanças futuras devem manter `v:2` legível (campos novos opcionais).

## Diagramas 2, 4, 6, 7
Adiados: dependem de confirmar build e de decisões abaixo. Ver NEXT_AGENT_PROMPT.md.
