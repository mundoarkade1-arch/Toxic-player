# Toxic Cloud

## Como funciona
1. **Conexão**: `CloudAuth` usa o `AuthorizationClient` do Google Play Services. O app nunca vê senha nem refresh token; o access token fica só em memória (validade curta) e é renovado em silêncio. Desconectar revoga o acesso (endpoint oficial de revogação) e apaga índice, cache e downloads locais.
2. **Biblioteca**: `files.list` filtra áudio (MIME `audio/*`, `application/ogg` e `octet-stream`, depois filtro por extensão). O Drive não expõe tags de áudio, então título/artista/álbum/duração/capa vêm de **leituras parciais (HTTP Range)** do início/fim do arquivo (`tags/`), nunca do arquivo inteiro. Os registros aparecem imediatamente com dados deduzidos do nome e são enriquecidos em segundo plano (3 em paralelo).
3. **Sincronização**: completa na primeira vez e em "Atualizar biblioteca"; depois incremental pela **Changes API** (token obtido antes da varredura). Detecta novos, alterados (md5/data/tamanho) e removidos (inclui lixeira). Arquivo alterado ou removido tem cache e download descartados. Automática só ao abrir o app se passaram 6 h; nunca em loop.
4. **Streaming**: `MediaItem` usa `toxiccloud://drive/<id>`. A cada abertura o resolver troca pela URL oficial `files/<id>?alt=media` com cabeçalho `Authorization`; o ExoPlayer faz Range/seek. Cache em disco (LRU, 128 MB–2 GB) fica acima do resolver, indexado pela URI interna (sem token). Falha de rede/sessão pausa e retoma sozinha quando a rede volta.
5. **Offline**: opcional, por música/álbum; download completo em `filesDir` com verificação de tamanho e MD5; tem prioridade sobre streaming.

## Limitação importante: escopo `drive.file`
`drive.file` só enxerga arquivos criados pelo app ou escolhidos num seletor do Google. O Google Picker é web-only (não há equivalente Android oficial) e o seletor de documentos do Android entrega URIs sem id de Drive. Logo `drive.file` **não permite ler uma biblioteca já existente**. O menor escopo que permite isso é `drive.readonly` (somente leitura), usado aqui e centralizado em `CloudConfig.DRIVE_SCOPE`. Consequências em GOOGLE_DRIVE_SETUP.md.

## Outras limitações
- Só "Meu Drive" (sem Drives compartilhados), sem filtro por pasta ainda.
- Downloads offline não sobrevivem ao encerramento do app durante o download.
- Troca de conta = desconectar e reconectar.
- Tags raras (ID3 criptografado, Ogg FLAC, capas > 3 MB) são ignoradas; nome do arquivo é usado.
- A DSP/equalizador atua normalmente nas faixas da nuvem (mesmo pipeline).
- Nada disto foi validado em aparelho nem compilado (veja PROJECT_STATE.md).

## Segurança (revisão)
Sem tokens em disco, logs ou mensagens; sem links públicos; `Authorization` só em cabeçalho; redirecionamentos da API não seguidos com token; ids validados por regex; respostas JSON lidas de forma defensiva; erros traduzidos sem corpo da resposta; `allowBackup=false` já existente; tráfego só HTTPS; sem Client Secret no código; `.gitignore` bloqueia credenciais. Observação: o `DefaultHttpDataSource` do player segue redirecionamentos HTTPS e reenvia o cabeçalho; o endpoint de download da API pertence ao Google.
