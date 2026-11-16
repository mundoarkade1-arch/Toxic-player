# PROJECT_STATUS — Ecossistema Toxic Player
Atualizado: 2026-11-15 (agente 1, auditoria). Legenda: [E] existe no código · [P] planejado · [?] não comprovado.

## Projeto A — Toxic Player (app Android)
- Kotlin + Jetpack Compose + Media3/ExoPlayer. Pacote `com.meuapp.player`. Código ativo só em `app/src/main/java/`.
- [E] Player local, fila, favoritos, M3U, widget, DSP próprio (EQ, compressor, limitador), timer.
- [E] Toxic Cloud: login Google (escopo só leitura), Drive v3, leitura de tags por Range, sincronização completa e incremental, streaming com cache, offline, busca.
- [E] SmartPlaylists: lê o JSON `toxic-playlists` v2 da pasta "Toxic Playlists" do Drive. Regras `artista`, `pasta`, `lista`. Nunca remove faixa. Não recria playlist apagada.
- [E] Navegação estilo Spotify (Início, Buscar, Biblioteca, No aparelho, Ajustes).
- [E, não compilado] Histórico de escuta (etapa 2 do agente 2): `taste/ListeningHistory.kt` (lógica pura), `taste/ListeningHistoryStore.kt` (grava no aparelho), ligado em `PlayerViewModel.kt` (troca de faixa, fim da fila, favoritar). Sem tela. Dados só no aparelho.
- [E, não compilado] Perfil de gosto: `taste/UserTasteProfile.kt` (afinidade por artista e gênero, escuta recente pesa mais, meia-vida 60 dias, `explain()` em português simples). `ListeningHistoryStore.profile(genreOf)` entrega o perfil. Sem tela ainda.
- [E, não compilado] Motor de recomendação: `taste/MusicRecommendationEngine.kt` (gosto por artista e gênero, histórico recente/favorita/pulos, novidade, boost de contexto, 20% de descoberta, limite por artista, sem duplicata, sem mesmo artista seguido). Cada sugestão traz o motivo em português simples. Sem popularidade (não há dado). Sem tela.
- [E, não compilado] Gerador por intenção: `taste/ContextualPlaylistGenerator.kt`. Entende palavras como viajar, noite, relaxar, treinar, trabalhar, animadas, românticas, nostalgia, descobertas, e também o nome de um artista ou gênero da biblioteca. Tabela de palavras é palpite simples; só ajuda músicas com gênero nas tags.
- [E, não compilado] `PlayerViewModel.createIntentPlaylist(intent)`: cria ou completa a playlist. Só acrescenta, nunca remove, nunca repete. Motivos em `intentReport`. Motivos em `intentReport` (limpo a cada chamada).
- [E, não compilado] Tela: aba Biblioteca > Listas, botão "Criar por intenção (inteligente)" em `ui/PlaylistsScreen.kt`. Abre uma janela com um campo, monta a playlist e mostra o resultado com o motivo de cada música. NÃO testada no aparelho.
- Gerador por intenção: Actions VERDE (usuário confirmou).
- [P] Playlist "Descobertas do dia" automática; ligação com playlists do site (SmartPlaylistManager).
- Motor de recomendação: Actions VERDE (usuário confirmou).
- Perfil de gosto: Actions VERDE (usuário confirmou).
- Build do histórico de escuta: Actions VERDE (usuário confirmou).
- Estado de build: PROJECT_STATE.md diz que nada foi compilado. Só leitura do Drive (35 playlists, +590 faixas) foi validada no aparelho. [?] build atual no Actions.
- Problema de organização: pasta antiga repetida `Toxic-player-main/Toxic-player-main/` e arquivos `.kt` soltos na raiz (README manda não ter). Não mexi.

## Projeto B — Site (gerenciador-de-musicas)
- HTML/CSS/JS puro, roda no navegador (Chrome/Edge). Sem servidor.
- [E] Top 200 por gênero via Deezer (API pública). GitHub Action diária grava `data/charts.js`.
- [E] Lê a pasta de músicas do PC (só leitura), acha repetidas, marca o que já existe.
- [E] Login Google (drive.readonly + drive.file): sobe o que falta para a pasta "Músicas do Site", manda repetida para a lixeira (com confirmação), grava `playlists-inteligentes.json` em "Toxic Playlists".
- [E] Playlist privada no YouTube pelo Top 200 (cota 10.000/dia).
- [E] Baixa JSON de playlists por lista, artista, pasta, nome.
- [?] Exportar lista de URLs pendentes: NÃO encontrei no código.
- [?] Ligação com o Media Downloader: NÃO existe nos dois zips.

## Incompatibilidades dos diagramas com o código
1. Diagramas mostram Site → JSON com URLs → Media Downloader → Drive automático. No código o site não gera URLs de download. Quem sobe ao Drive é o próprio site, a partir da pasta do PC.
2. Diagramas mostram "APIs REST / backend / banco JSON". Não existe backend. AGENTS.md do app proíbe backend e serviço pago.
3. JSON dos diagramas (`tr_001`, `ar_001`, `drive_id`, `play_count`) não é o formato real. O real é `{formato:"toxic-playlists", v:2, playlists:[{nome, origem, regra, faixas:[{id?, fn, p, t, a}]}]}`.
4. Diagrama de dados mostra pasta "Toxic Player Music/Artistas/Álbuns". O site usa "Músicas do Site" e "Toxic Playlists". Estrutura real por artista: [?] confirmar.
5. "Catálogo JSON" central e "disponibilidade" voltando ao site: não existem. O app descobre disponibilidade lendo o Drive.
6. Os 5 diagramas recebidos estão com numeração diferente (1 a 5) dos 7 pedidos no prompt.

## Ordem recomendada
1. Toxic Player primeiro: confirmar build; depois histórico + perfil + recomendação local. Motivo: o site já gera o JSON que o app já lê. A peça que falta é no app.
2. Site depois: exportar lista de pendentes (artista, título, link) para o Media Downloader.
3. Integração e teste completo.

## Estado de verificação (fim da sessão do agente 1)
- Actions verde confirmado pelo usuário: histórico, perfil, motor, gerador.
- Etapa 5 (tela + ViewModel): entregue, Actions NÃO confirmado.
- Nada testado no aparelho.

## Projeto C — Toxic Music Engine (motor) — 2026-11-16
- [P] Não existe código. Só o contrato de dados proposto em `CONTRATO_MOTOR.md`.
- [P] Roda no PC do usuário (Windows), sem servidor. Fala com site e app pelo Google Drive.
- Próximas etapas: arrumar a casa (rodar o workflow "Limpar" nos dois repositórios), núcleo do motor com testes, ligação ao Drive, aba no site, leitura no app, análise do Media Downloader.
- Observação: `limpar.yml` do app já apaga os `.kt` soltos e a pasta `Toxic-player-main/` repetida. Depois de rodá-lo, o "Problema de organização" do Projeto A deixa de existir.
