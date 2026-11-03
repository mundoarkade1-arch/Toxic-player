'# Toxic Player

Player Android para músicas guardadas no aparelho, com busca local, biblioteca por pastas/artistas/álbuns, playlists pessoais e favoritas, fila de reprodução e controles avançados de áudio.

## Recursos

- Lê músicas locais autorizadas pelo usuário e reproduz em segundo plano.
- Busca por título, artista, álbum e pasta.
- Organiza a biblioteca por pastas, artistas e álbuns; cria playlists e favoritas.
- Player com fila, reprodução aleatória, repetição, velocidade, tom e timer.
- Mesa de áudio com equalizador, filtros, dinâmica, efeitos, presets e medidores.
- A permissão para acessar músicas é solicitada quando a Biblioteca é aberta.

## Abrir e executar

Abra esta pasta no Android Studio e aguarde a sincronização do Gradle. O app exige Android 9 ou posterior.

O pacote exportado não inclui um Gradle Wrapper completo. Para compilar, use o Gradle integrado/instalado pelo Android Studio ou adicione o wrapper pelo Android Studio antes de executar o projeto fora dele.

## Áudio

O DSP próprio processa PCM de 16 bits mono/estéreo no pipeline Media3. Efeitos do sistema como Bass Boost e Virtualizer variam conforme o aparelho. A Mesa de áudio controla o processamento das músicas locais tocadas pelo app.

## Publicação'

O workflow em `.github/workflows/build.yml` compila, testa e publica APKs. Para releases assinadas, configure os secrets de assinatura do workflow; não coloque o keystore ou suas senhas no repositório.

