# NEXT_AGENT_PROMPT — Agente 2 (continuação)
Atualizado: 2026-11-15 pelo agente 1. Este texto é autossuficiente. Leia também PROJECT_STATUS.md, ARCHITECTURE.md, DEVELOPMENT_LOG.md, TEST_REPORT.md, DIAGRAMS.md (mesma pasta).

## 1. Objetivo geral
Ecossistema musical integrado: (A) Toxic Player, app Android; (B) site musical (descoberta, Top 200 Deezer, duplicatas, Drive); Google Drive como biblioteca central; Media Downloader (externo, já testado pelo usuário, NÃO está nos zips) para baixar por URL. O Player deve aprender o gosto do usuário e criar playlists inteligentes. Não reconstruir nada. Não inventar integração que o código não prova. Respeitar direitos autorais. Cada agente conclui uma etapa substancial e deixa o próximo prompt.

## 2. Usuário (OBRIGATÓRIO)
- Português simples e MUITO fácil, frases curtas. Escreve por ditado de voz: palavras podem vir trocadas; pergunte se algo não fizer sentido.
- UM passo por vez, numerado. Esperar "feito" ou "verde" antes do próximo.
- ENTREGAS SEMPRE EM ZIP, com as pastas certas do repositório, para ele descompactar e arrastar para o GitHub (a pasta `toxic-player/` do zip espelha a raiz do repositório). Dar link direto do GitHub quando citar arquivo.
- Sem comandos de terminal. Nunca pedir senha, chave de assinatura nem link M3U. Sem palavras técnicas sem explicar. Sem blocos de exemplo que pareçam campos para preencher.
- Ele confirma o resultado do GitHub Actions dizendo "verde" ou "vermelho". Se vermelho, peça a mensagem de erro e corrija antes de qualquer coisa.
- Pergunte só o que for importante. Rotina reversível não precisa de autorização; TELAS novas pedem autorização (ele já autorizou a primeira).

## 3. Histórico resumido
Agente 1: auditou os dois zips (Toxic-player-main e gerenciador-de-musicas-main) e os 5 diagramas neon; escreveu os 6 documentos; implementou em 5 etapas o núcleo de recomendação do Player. O usuário subiu cada etapa no GitHub.

## 4. Estado real dos projetos
Projeto A, Toxic Player (Kotlin, Compose, Media3 1.3.1, pacote `com.meuapp.player`):
- Já existia: player, DSP/equalizador, Toxic Cloud (Drive só leitura, tags por Range, sync incremental, streaming, offline), SmartPlaylists (lê `toxic-playlists` v2 da pasta "Toxic Playlists" no Drive), navegação estilo Spotify.
- Feito pelo agente 1 (pasta `app/src/main/java/com/meuapp/player/taste/`):
  1. `ListeningHistory.kt` e `ListeningHistoryStore.kt`: histórico local (SharedPreferences `toxic_history`, até 1500 músicas, favoritas nunca saem). Ligado em `PlayerViewModel.kt` (`onPositionDiscontinuity`, `onPlaybackStateChanged`, `toggleFav`). Chave da música: `c:<idDoDrive>` ou `l:<idLocal>`.
  2. `UserTasteProfile.kt`: afinidade por artista e gênero, meia-vida 60 dias, `explain()`.
  3. `MusicRecommendationEngine.kt`: pesos 0,55 artista, 0,30 gênero, histórico, 20% descoberta, máx. 3 por artista, sem duplicata, sem mesmo artista seguido.
  4. `ContextualPlaylistGenerator.kt`: tabela de palavras (viajar, noite, relaxar, treinar, trabalhar, animadas, românticas, nostalgia, descobertas) e reconhece artista/gênero da biblioteca. `PlayerViewModel.createIntentPlaylist(texto)` só ACRESCENTA músicas, nunca remove, nunca repete; motivos em `intentReport`.
  5. Tela: `ui/PlaylistsScreen.kt`, botão "Criar por intenção (inteligente)" (Biblioteca > Listas).
- Não existe: popularidade, "Descobertas do dia" automática, tela para limpar histórico (existe `history.clear()`), artistas semelhantes, aprendizado com playlists usadas.
Projeto B, site (HTML/CSS/JS puro, navegador): Top 200 via Deezer (GitHub Action diária em `data/charts.js`), lê pasta do PC, acha repetidas, sobe ao Drive ("Músicas do Site"), grava `playlists-inteligentes.json` em "Toxic Playlists", cria playlist privada no YouTube. NÃO exporta lista de URLs pendentes e NÃO fala com o Media Downloader.

## 5. Decisões técnicas
Sem backend (AGENTS.md do app proíbe). Drive é a ponte. Contrato real: JSON `toxic-playlists` v2 (ver ARCHITECTURE.md); campos novos só opcionais. Dados de escuta ficam só no aparelho. Escopo do Drive no app continua só leitura. Pulo pesa pouco e nunca bane música. Descoberta = artista novo no gênero que o usuário já gosta.

## 6. Testes (honestidade)
Testes unitários escritos: ListeningHistoryTest (14), UserTasteProfileTest (9), MusicRecommendationEngineTest (10), ContextualPlaylistGeneratorTest (10). O usuário informou "verde" no Actions até a etapa do gerador (o build roda `gradle testDebugUnitTest`). A etapa 5 (tela + ViewModel) foi entregue, mas o usuário AINDA NÃO confirmou o Actions dela. Nada foi testado no aparelho. O agente 1 não tinha Kotlin/Gradle: tudo escrito sem compilar localmente.

## 7. Problemas e riscos conhecidos
- Escolher outra música na lista conta como pulo se ouviu pouco; troca de fila inteira não conta.
- Música da nuvem com duração 0 vira "parcial", nunca "completa" ao pular.
- Gênero só vem das tags do Drive; músicas locais não têm gênero; sem gênero, a intenção não ajuda.
- A tabela de palavras da intenção é palpite simples.
- Pasta repetida antiga `Toxic-player-main/Toxic-player-main/` e arquivos `.kt` soltos na raiz do repositório (README manda não ter). NÃO mexer sem perguntar.
- Diagramas neon NÃO provam integração (incompatibilidades em PROJECT_STATUS.md).

## 8. Próximas tarefas (nesta ordem)
1. Pedir o resultado do Actions da etapa 5. Vermelho: corrigir primeiro. Verde: pedir que teste no celular (histórico vazio; "Treinar"; "Treinar" de novo; nome de artista) e relate.
2. "Descobertas do dia": só acrescentar, uma vez por dia, respeitando playlist apagada (`smart.forget`). Pode reaproveitar `createIntentPlaylist("Descobertas do dia")`.
3. Opção de privacidade para limpar o histórico (pedir autorização da tela; olhar `ui/SettingsScreen.kt`).
4. Site: exportar lista de pendentes (artista, título, link) para o Media Downloader. ANTES perguntar ao usuário em que formato o Media Downloader recebe a lista, e como ficam as pastas no Drive por artista. Pedir os arquivos inteiros que precisar mexer.
5. Popularidade: avaliar usar a posição nas listas Top do site (`data/charts.js`) como sinal no motor.
6. Diagramas 2, 4, 6 e 7 em Mermaid (ver DIAGRAMS.md), diferenciando existente e planejado.
7. Atualizar os 6 documentos e gerar o prompt seguinte.

## 9. Cuidados
Leia o código real antes de mexer; peça arquivos inteiros por link, não peça para procurar trechos. Nunca token do Google no disco; nunca escopo maior do Drive sem justificar em docs/TOXIC_CLOUD.md. Não declarar "testado" sem rodar. Diferenciar: escrito, compilado, instalado, testado, validado no uso real. Não duplicar documentos.

## 10. O que o usuário deve enviar na próxima conversa
Este arquivo (colado), o zip atual do repositório do Toxic Player baixado do GitHub, o zip do site, o prompt master e os 5 diagramas.
