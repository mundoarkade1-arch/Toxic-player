# Toxic Player

Aplicativo Android de música com busca principal no YouTube e player local com controles e efeitos de áudio.

> No GitHub, a pasta `app/` da raiz é a versão ativa. Os arquivos Kotlin soltos na raiz são snapshots antigos e não fazem parte da compilação.

## YouTube e YouTube Premium

- A primeira tela permite buscar vídeos de música e playlists públicas.
- Ao tocar num resultado, o Toxic Player abre o link no aplicativo oficial do YouTube quando ele está instalado. Para usar sua assinatura Premium, entre na sua conta nesse aplicativo.
- **Minhas playlists no YouTube** abre as playlists da sua conta no aplicativo oficial.
- A pesquisa do catálogo público usa a YouTube Data API v3 e precisa de uma chave própria. A tela inicial explica como criar e restringir a chave. Restrinja-a ao pacote e certificado que você usa e à YouTube Data API v3.
- Cada busca de vídeos ou playlists consome uma chamada da cota de pesquisa. O padrão atual é 100 chamadas de `search.list` por dia no projeto Google Cloud; confira a cota no Console.
- A chave é guardada neste aparelho para não precisar digitá-la de novo. Chaves de API em aplicativos Android podem ser inspecionadas; restrinja-a no Google Cloud e não reutilize uma chave de servidor.
- Resultados de pesquisa e playlists públicas são do catálogo do YouTube. Playlists particulares da conta são abertas pelo YouTube oficial, que já tem o login e as permissões da conta.

O player do YouTube fica no aplicativo oficial. Assim, a reprodução, o login Premium e os controles pertencem ao YouTube. O Toxic Player não extrai o áudio do vídeo nem aplica seus efeitos ao som do YouTube. O player e a Mesa de áudio do Toxic Player continuam destinados a músicas locais.

## Abrir e executar

Abra esta pasta no Android Studio e aguarde a sincronização do Gradle. O app exige Android 9 ou posterior. A permissão para ler músicas locais só aparece quando você abre a Biblioteca; a busca do YouTube não precisa dela.

O pacote exportado não inclui um Gradle Wrapper completo. Para compilar, use o Gradle integrado/instalado pelo Android Studio ou adicione o wrapper pelo Android Studio antes de executar o projeto fora dele.

## Busca do YouTube

1. No [Google Cloud Console](https://console.cloud.google.com/apis/library/youtube.googleapis.com), crie um projeto e ative **YouTube Data API v3**.
2. Crie uma chave de API. Em **Restrições de aplicativo**, escolha Android e informe o pacote e a impressão digital SHA-1 do certificado. Para o APK debug, o pacote inclui o sufixo `.debug` (`com.meuapp.player.debug`); para release, use `com.meuapp.player`. Em **Restrições de API**, permita somente YouTube Data API v3.
3. Abra Toxic Player, escolha **Abrir configuração da API**, crie a chave, volte ao app, cole-a e toque em **Salvar chave**.
4. Escolha **Músicas e vídeos** ou **Playlists**, pesquise e toque no resultado. O YouTube oficial abrirá o conteúdo.

O SHA-1 de debug pode ser encontrado no Android Studio em **Gradle > Tasks > android > signingReport**. Se publicar uma versão assinada, inclua também a impressão digital do certificado de release nas restrições da chave. Consulte [a cota da API](https://developers.google.com/youtube/data/determine_quota_cost) antes de distribuir o app.

## Áudio local

A biblioteca local, as listas, o player, a fila, o timer, o DSP e os presets ficam disponíveis nas outras abas. O DSP próprio processa PCM de 16 bits mono/estéreo no pipeline Media3. Efeitos do sistema como Bass Boost e Virtualizer variam conforme o aparelho.

## Publicação

O workflow em `.github/workflows/build.yml` compila, testa e publica APKs. Para releases assinadas, configure os secrets de assinatura do workflow; não coloque o keystore ou suas senhas no repositório.

