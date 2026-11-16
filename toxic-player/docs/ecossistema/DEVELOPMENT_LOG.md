# DEVELOPMENT_LOG
## 2026-11-15 — Agente 1 (auditoria)
- Lidos: PROJECT_STATE.md, AGENTS.md, README, ROADMAP, SmartPlaylists.kt (trechos), CloudConfig/CloudAuth, app.js (trechos), atualizar.mjs, workflow do site, 5 diagramas.
- Criados: PROJECT_STATUS.md, ARCHITECTURE.md, DIAGRAMS.md, DEVELOPMENT_LOG.md, TEST_REPORT.md, NEXT_AGENT_PROMPT.md.
- Nenhum código dos projetos foi alterado.

## 2026-11-15 — Agente 1 (etapa de implementação 1: histórico de escuta)
- Criados: `app/src/main/java/com/meuapp/player/taste/ListeningHistory.kt`, `taste/ListeningHistoryStore.kt`, teste `app/src/test/java/com/meuapp/player/taste/ListeningHistoryTest.kt` (14 testes).
- Modificado: `PlayerViewModel.kt` (import, `val history`, `onPositionDiscontinuity`, `onPlaybackStateChanged`, `toggleFav`).
- Regras: terminou ou 90% = completa; menos de 10 s = pulo cedo; menos de 50% = pulo; resto = parcial. Pulo pesa pouco (-0,5 / -0,25). Favoritas nunca são apagadas ao limpar excesso (limite 1500 músicas).
- Limites conhecidos: escolher outra música na lista conta como pulo se ouviu pouco; troca de fila inteira não conta (reason REMOVE); duração 0 em música da nuvem sem tags faz só `PARTIAL`.

## 2026-11-15 — Agente 1 (etapa 2: perfil de gosto)
- Criados: `taste/UserTasteProfile.kt`, teste `taste/UserTasteProfileTest.kt`.
- Modificado: `taste/ListeningHistoryStore.kt` (função `profile`). Nada ligado a tela ainda.
- Regras: pulo-só não cria gosto; artista desconhecido não conta; nomes comparados sem acento e sem maiúscula; afinidade 0 a 1 pelo maior.

## 2026-11-15 — Agente 1 (etapa 3: motor de recomendação)
- Criados: `taste/MusicRecommendationEngine.kt`, teste `taste/MusicRecommendationEngineTest.kt`.
- Modificado: `taste/ListeningHistoryStore.kt` (função `entriesByKey`).
- Pesos: 0,55 artista + 0,30 gênero + contexto; tocada há menos de 6 h -0,5, menos de 24 h -0,25; 3+ pulos e nenhuma completa -0,3; favorita +0,15; nunca tocada +0,05; chegou há menos de 14 dias +0,1.
- Descoberta: artista que o usuário nunca ouviu, no gênero que ele gosta. Reserva 20% da lista. Perfil vazio = sem descoberta.
- Limite: se a biblioteca tiver poucos artistas, a lista pode vir menor que o pedido (limite por artista).

## 2026-11-15 — Agente 1 (etapa 4: gerador por intenção)
- Criados: `taste/ContextualPlaylistGenerator.kt`, teste `taste/ContextualPlaylistGeneratorTest.kt`.
- Modificado: `PlayerViewModel.kt` (imports, `generator`, `intentReport`, `createIntentPlaylist`).
- Boosts: gênero da intenção +0,5; artista citado +0,6 (sem limite de 3 por artista); nostalgia +0,4 para completas ouvidas há mais de 30 dias; descoberta sobe a parte de artistas novos para 60%.
- Limite: músicas sem gênero nas tags não recebem boost de estilo. Músicas locais não têm gênero.
