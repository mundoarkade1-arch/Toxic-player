# DECISIONS — decisões e motivos

1. **Pacote `com.asccine.tvbox`**, para não conflitar com o app antigo (`com.ascplay.tvbox`).
2. **Telas montadas em código Java** (sem XML de layout). Motivo: o projeto não pôde ser
   compilado pelo agente; sem XML há menos ids cruzados para errar. Também é leve.
3. **Reaproveitado do app antigo** (só lógica, não o visual): `XtreamApi`, `M3uApi`, `SearchIndex`,
   `ImageLoader`, `LoginChecker`, `Updater`, `DeviceLink`, `CredentialStore`, `ContinueStore`,
   `ParentalStore`, `PlaybackProgress`, `SeriesProgress`, `PinDialog`, `SearchKeyboard`.
   Mudanças: pacote, user-agent, chave do Keystore (`asc_cine_key`), dados por perfil, nota e data
   de adição nos itens, detalhes estruturados (ano, duração, gênero, fundo), imagem em dois tamanhos.
4. **Serviço do código do aparelho:** o mesmo Supabase do app antigo (o ASC Admin continua igual).
   A chave secreta do aparelho agora é guardada **criptografada** (`Secure`); antes ficava em texto.
5. **Perfis:** cada perfil tem arquivos próprios (`asc_login_<id>`, `asc_continue_<id>`,
   `asc_lists_<id>`). Progresso de reprodução e controle parental valem para o aparelho todo.
6. **Favoritos e bloqueados** são criptografados (itens de M3U carregam o endereço do vídeo).
   O item é reconhecido por tipo + nome.
7. **Top 10 de hoje:** o servidor não tem ranking. Regra: entre os 200 itens mais recentes
   (campo `added`), os 10 de maior nota (`rating`). Em lista M3U (sem nota/data): os 10 primeiros
   da primeira categoria. Os 5 destaques do banner vêm dos Top 10, filmes e séries intercalados.
8. **Fonte:** Poppins Bold e Medium (SIL OFL) em `res/font`, por ser geométrica e estar disponível
   sem internet. Para trocar por Space Grotesk ou Outfit, substitua os dois `.ttf` mantendo os nomes.
9. **Senha do app:** 4 números (o `PinDialog` herdado é de 4 dígitos). Pedido original: 4 a 6.
10. **Cartão em foco:** escala 1,06 (120 ms), contorno limão de 3dp e "cantinho" branco deslocado.
    Sem blur. Na grade o cartão fica "selecionado" e a `TvGrid` acende só quando a grade tem o foco.
11. **Imagens:** cache LRU de 16 MB, capas em até 480 px, banner em até 1280 px, JPEG em RGB_565.
    Reuso de bitmaps (`inBitmap`) NÃO foi feito.
12. **Lint desligado no release** (`abortOnError false`): o media3 tem APIs "instáveis" que o lint
    trata como erro; o código já usa `@OptIn`.
13. **Sem minificação (R8)** no release, por segurança de compilação; pode ser ligada depois.
14. **Atualização:** só HTTPS, `version.json` em `releases/latest/download`, URL do APK precisa
    começar com o prefixo do repositório, SHA-256 conferido antes de instalar, instalação por FileProvider.

## Lote de melhorias (Início, perfis, avatares, login único)

- **Início mais rápida.** Escolhido: guardar em arquivo (HomeCache, criptografado, validade de 6 h) os 200 itens mais novos de Filmes e de Séries. Com arquivo válido, banner e Top 10 aparecem na hora, sem rede. Se o arquivo passou de 6 h, ele é mostrado na hora e atualizado por trás só para a próxima vez (a tela não muda sozinha, para não tirar o foco). Sem arquivo, Filmes e Séries são calculados ao mesmo tempo (Bg com 5 threads) e o banner aparece assim que o primeiro Top 10 chega; quando o segundo chega, entra no banner sem trocar o destaque atual. "Atualizar" apaga o guardado.
- **Banner gira sempre que visível** (a cada 8 s), pausa só quando sai da tela ou a janela vai para trás (Screen.onPause). Setas no botão trocam e reiniciam a contagem.
- **Menu lateral:** ordem avatar, Início, Buscar, Favoritos, Filmes, Séries, Explorar; embaixo Atualizar e Ajustes. Foco explícito: cima/baixo tratados por tecla e por nextFocusUp/DownId; nas pontas fica parado; direita sai para a tela.
- **Top 10:** título 40sp, rótulo limão grande, linha limão, capas 20% maiores que a grade.
- **Detalhe:** layout em duas colunas que cabe em 1080p. Série: botão Temporada, 30 primeiros episódios, "Ver mais" abre tela inteira. Sinopse em 3 linhas com "Ler mais".
- **Perfis:** primeira vez (depois da lista) = criar perfil (escrever nome + avatar, ou usar Visitante). Depois, sempre "Quem está assistindo?". Quem já tinha perfis não passa pela criação.
- **Avatares:** 12 VectorDrawable (avatar_01 a 12), 6 masculinos e 6 femininos.
- **Login único:** arquivo asc_login do aparelho, com migração do asc_login_<perfil>. Continue, favoritos, bloqueados, PlaybackProgress e SeriesProgress são por perfil (o perfil p1 mantém os nomes antigos de arquivo).

## Correções de uso (controle remoto, banner, busca, perfis)
- Foto do banner agora é recortada no tamanho do banner (HeroView com clipChildren), não invade mais o "Continue assistindo".
- "Continue assistindo" fica logo depois dos Top 10 de filmes e de séries.
- Teclas inteligentes em ShellActivity.smartKey (só para setas que a tela não tratou): esquerda sem vizinho na linha vai para o item do menu na mesma altura; direita sem vizinho trava (não desce); cima sem nada em cima não faz nada (a grade leva ao botão de categoria/busca via Screen.handleUp).
- Banner: esquerda no primeiro destaque vai para o menu; direita no último volta ao primeiro; cima não faz nada.
- Perfis: botão "Editar perfis" na tela "Quem está assistindo?" (mudar nome, avatar, excluir).
- Busca: antes de buscar, botão no meio da tela e cartões pequenos de lançamentos (filmes e séries); depois de buscar, botão no alto e a grade.
- Faixas por categoria da Início já tinham o cartão "Ver mais" no fim; foi mantido.
