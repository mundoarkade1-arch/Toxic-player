# CONTRATO_MOTOR — como o Toxic Music Engine conversa com o resto

Estado: PROPOSTA, atualizada em 2026-11-16 (etapa 3). O motor já escreve estes arquivos em código, mas só testado com Drive de mentira. Legenda: [E] existe hoje · [P] planejado.

## Regras de ouro
1. Sem servidor. O Google Drive é a ponte entre motor, site e app. [E para site e app, P para o motor]
2. O contrato `toxic-playlists` versão 2 NÃO muda. Campos novos só podem ser opcionais. [E]
3. Cada arquivo tem UM dono (quem escreve). Os outros só leem. Assim ninguém sobrescreve o trabalho do outro.
4. Nenhum arquivo guarda senha, token, chave ou link de download.
5. O motor nunca apaga nada no Drive. Só cria e atualiza os próprios arquivos.
6. Todo arquivo traz `formato`, `v` (versão) e `gerado` (data ISO-8601). Quem lê ignora campos que não conhece.

## Pastas no Drive
| Pasta | Dono | Situação |
|---|---|---|
| `Músicas do Site` (nome definido em `PASTA_RAIZ` no app.js do site) | site | [E] |
| `Toxic Playlists` | site (`playlists-inteligentes.json`) | [E] |
| `Toxic Engine` (nova, na raiz do Drive) | motor (status, fila, pendentes E `playlists-motor.json`) | [P] código escrito |

O app lê hoje SÓ o arquivo `playlists-inteligentes.json` (`CloudConfig.PLAYLISTS_FILE`). O motor NÃO escreve esse nome, porque o site já escreve nele e um sobrescreveria o outro. O motor escreve `playlists-motor.json`, no mesmo formato, na pasta `Toxic Engine` (e não em `Toxic Playlists`: com o escopo `drive.file` o motor só consegue escrever em pastas que ele mesmo criou). O app precisará procurar os dois nomes, em qualquer pasta do Drive (etapa 5, mexe em `CloudConfig`, `DriveApi.findPlaylistsFile` e `SmartPlaylistManager`).

## Arquivos da pasta `Toxic Engine` (dono: motor)

### engine-status.json
Resumo para o site mostrar. Pequeno, sempre sobrescrito.
```json
{ "formato": "toxic-engine-status", "v": 1, "gerado": "2026-11-16T12:00:00Z",
  "motor": { "versao": "0.1.0", "estado": "ocioso" },
  "ultimaExecucao": { "inicio": "…", "fim": "…", "resultado": "ok" },
  "contagem": { "naFila": 12, "pendentes": 40, "comErro": 2, "duplicadas": 7 },
  "avisos": [ "texto curto em português simples" ] }
```
`estado`: `ocioso`, `trabalhando`, `pausado`, `erro`.

### fila.json
Tarefas do motor, com progresso para continuar depois de fechar o programa.
```json
{ "formato": "toxic-engine-fila", "v": 1, "gerado": "…",
  "tarefas": [ { "id": "t-0001", "tipo": "comparar-lista", "alvo": "brasil",
    "estado": "pendente", "tentativas": 0, "ultimoErro": null, "criada": "…", "atualizada": "…" } ] }
```
`tipo` (primeiros): `comparar-lista` (Top 200 contra a biblioteca), `achar-duplicadas`, `buscar-metadados`, `gerar-playlists`.
`estado`: `pendente`, `rodando`, `feita`, `erro`, `cancelada`. Erro tem no máximo 5 tentativas, com espera crescente.

### pendentes.json
Músicas que o usuário ainda NÃO tem. O motor só lista. Não baixa nada.
```json
{ "formato": "toxic-engine-pendentes", "v": 1, "gerado": "…",
  "itens": [ { "chave": "henrique e juliano|recairei", "t": "Recairei", "a": "Henrique & Juliano",
    "origem": "Top 200 Brasil", "deezerId": 123456, "posicao": 12, "vistaEm": "…" } ] }
```
`chave` = artista e título normalizados (tudo minúsculo, sem acento, sem pontuação, sem "feat"/"with", separados por `|`). É a mesma ideia do 3º critério de casamento do app, para os dois concordarem.

## Arquivo de playlists (dono: motor, também na pasta `Toxic Engine`)

### playlists-motor.json
Formato idêntico a `toxic-playlists` v2 (ver ARCHITECTURE.md). Playlists por artista, discografia, gênero e época. Só entram faixas que o motor confirmou que existem na biblioteca. Campo extra opcional `origem: "Motor"`.

## Quem lê o quê
| Arquivo | Escreve | Lê |
|---|---|---|
| engine-status.json, fila.json, pendentes.json | motor | site (aba nova, etapa 4) |
| playlists-motor.json | motor | app (etapa 5) |
| playlists-inteligentes.json | site | app [E] |
| data/charts.js (Top 200) | GitHub Action do site | site [E] e motor (lê a cópia pública do repositório) |

## Como o motor sabe o que a biblioteca tem
Lê o Drive pela API (nomes, pastas e tags), do mesmo jeito que o app. Não cria um segundo índice dentro da biblioteca. Guarda uma cópia local no PC para comparar rápido.

## Segurança
- Credenciais do Google ficam só no PC do usuário, fora do repositório (`.gitignore`).
- O usuário configura o acesso do motor ao Google uma vez, com ajuda passo a passo (etapa 3). Isso é um cliente OAuth tipo "Aplicativo para computador", no MESMO projeto Google do site. Gratuito. O token de renovação fica em arquivo só no PC do usuário (fora do GitHub). Com a tela de consentimento em modo "Teste", o Google costuma cancelá-lo em 7 dias e o usuário entra de novo.
- O motor respeita as cotas das APIs (Deezer, Drive) e para quando o limite chega.

## Decisões em aberto
1. Site e motor não se falam direto: a regra de segurança do site (`index.html`, `connect-src`) só permite os servidores do Google. Por isso tudo passa pelo Drive. Se um dia quisermos o site chamando o PC, essa regra terá de mudar e ficará registrada aqui.
2. Onde ficam os arquivos de música baixados pelo usuário e como o motor sobe ao Drive: depende de analisar o Media Downloader (etapa 6, ainda sem arquivos).
3. Tecnologia do motor: Node.js 20 (a mesma do `scripts/atualizar.mjs`). Falta o usuário confirmar que aceita instalar. O motor tem atalhos `.bat` para ele não usar terminal.
4. Escopos do Google: `drive.readonly` + `drive.file`. Mesmos do site.
