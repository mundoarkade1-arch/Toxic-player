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
