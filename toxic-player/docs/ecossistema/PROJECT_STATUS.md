# PROJECT_STATUS — Ecossistema Toxic Player
Atualizado: 2026-11-15 (agente 1, auditoria). Legenda: [E] existe no código · [P] planejado · [?] não comprovado.

## Projeto A — Toxic Player (app Android)
- Kotlin + Jetpack Compose + Media3/ExoPlayer. Pacote `com.meuapp.player`. Código ativo só em `app/src/main/java/`.
- [E] Player local, fila, favoritos, M3U, widget, DSP próprio (EQ, compressor, limitador), timer.
- [E] Toxic Cloud: login Google (escopo só leitura), Drive v3, leitura de tags por Range, sincronização completa e incremental, streaming com cache, offline, busca.
- [E] SmartPlaylists: lê o JSON `toxic-playlists` v2 da pasta "Toxic Playlists" do Drive. Regras `artista`, `pasta`, `lista`. Nunca remove faixa. Não recria playlist apagada.
- [E] Navegação estilo Spotify (Início, Buscar, Biblioteca, No aparelho, Ajustes).
- [P] Histórico de escuta, perfil de gosto, motor de recomendação: NÃO existem no código (busca por play count, pulada, histórico: nada).
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
