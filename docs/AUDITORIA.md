# Estado do projeto

O diretório Android ativo é `app/` na raiz. A cópia antiga em `Toxic-player/` foi removida desta revisão após autorização do proprietário. Arquivos Kotlin soltos e uma cópia avulsa do workflow permanecem na raiz como snapshots antigos; não fazem parte da compilação. Os presets, testes, documentação e workflows ativos ficam em seus diretórios normais na raiz.

## YouTube

- A busca é a tela inicial e oferece pesquisa pública de vídeos de música e playlists.
- Os links de resultados abrem no aplicativo oficial do YouTube, com fallback para navegador. O login e o Premium usam a conta conectada nesse aplicativo.
- O botão de playlists abre a biblioteca pessoal no aplicativo oficial. A pesquisa integrada não acessa playlists privadas; ela usa dados públicos da YouTube Data API v3.
- A busca requer uma chave própria da API, restringida no Google Cloud ao app/certificado e ao serviço YouTube Data API v3. A chave é salva localmente e não deve ser reutilizada como chave de servidor.
- A reprodução passa pelo YouTube oficial. O DSP do Toxic Player continua atuando somente na reprodução local.

## Áudio local

O player local usa Media3/ExoPlayer e o DSP próprio. O processador sinaliza inatividade para formatos diferentes de PCM 16 bits mono/estéreo; efeitos de hardware dependem do fabricante do telefone.

## Compilação

O workflow de GitHub Actions instala Gradle 8.7 e roda os testes unitários e `assembleRelease`. Este ZIP contém as propriedades do Gradle Wrapper, mas não os scripts `gradlew` nem `gradle-wrapper.jar`; o ambiente desta revisão também não tem Gradle instalado. Por isso não foi possível compilar o pacote localmente.

