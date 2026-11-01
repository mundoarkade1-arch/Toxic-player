# Toxic Player

Player de músicas locais para Android (Kotlin + Jetpack Compose), com motor de áudio próprio
(EQ gráfico e paramétrico, compressor, limitador, estéreo, medidores, espectro real),
capas dos álbuns, biblioteca por pastas/artistas/álbuns e atualização automática pelo GitHub.

Cada push na branch `main` compila, testa, assina e publica uma Release (`.github/workflows/build.yml`).
Secrets necessários: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.

Presets prontos para importar em Mesa > Presets: pasta `presets/`.
