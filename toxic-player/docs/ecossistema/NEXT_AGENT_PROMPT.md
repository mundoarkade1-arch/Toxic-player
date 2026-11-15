# NEXT_AGENT_PROMPT — Agente 2
Você continua o ecossistema Toxic Player (app Android Kotlin + site JS puro + Google Drive + Media Downloader externo). Releia o PROJETO MASTER original se o usuário colar. Não reconstrua nada.

## Usuário (importante)
Português simples, frases curtas. UM passo por vez, numerado; esperar "feito" ou "verde". Ele escreve por ditado de voz (palavras podem vir trocadas; pergunte se não fizer sentido). Não pedir terminal, zip, senhas, chave de assinatura. Link direto do GitHub por arquivo. Prefere copiar e colar arquivo inteiro. Sem blocos de exemplo que pareçam campos.

## Estado (veja PROJECT_STATUS.md e ARCHITECTURE.md)
- App: Cloud, SmartPlaylists (lê JSON v2 do Drive), UI estilo Spotify existem. Histórico, perfil e recomendação NÃO existem. Build não confirmado.
- Site: Top 200 Deezer, duplicatas, upload ao Drive, JSON de playlists, YouTube existem. Exportar pendentes e ligação com Media Downloader NÃO existem.
- Diagramas neon são conceituais e têm incompatibilidades (lista em PROJECT_STATUS.md).

## Decisões já tomadas
Sem backend. Drive é a ponte. Contrato JSON real `toxic-playlists` v2 (ARCHITECTURE.md). Ordem: Toxic Player primeiro, site depois.

## Próxima tarefa
1. Pedir ao usuário o resultado do build no GitHub Actions do app (verde ou vermelho). Se vermelho, corrigir erros primeiro.
2. FEITO (não compilado): histórico de escuta em `taste/`. Confirmar com o usuário que o Actions ficou verde (testes `ListeningHistoryTest`). Se vermelho, corrigir antes de seguir. Falta só uma tela/botão de privacidade para limpar (`history.clear()`).
3. Agora: `UserTasteProfile` (pesos por artista, pasta e gênero das tags; pular só reduz peso leve).
4. Só então `MusicRecommendationEngine` e playlist "Descobertas do dia" via SmartPlaylistManager, sem remover faixa manual e sem duplicata.
5. Atualizar os 6 documentos. Gerar novo NEXT_AGENT_PROMPT.

## Cuidados
Não tocar na pasta repetida `Toxic-player-main/Toxic-player-main/` sem avisar. Não adicionar escopo Drive maior (AGENTS.md). Nunca token no disco. Não declarar testado sem rodar. Diferenciar escrito, compilado, instalado, testado, validado.
## Perguntas abertas ao usuário
- Onde o Media Downloader recebe a lista (formato aceito)?
- Estrutura real das pastas no Drive por artista?
