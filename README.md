# Verde Player

Player de músicas locais (Android, Kotlin + Compose) em verde e preto, com equalizador, pré-amplificador,
compressor/limitador, importação de presets AutoEQ/Equalizer APO e atualização automática via GitHub Releases.

## 1. Abrir
Android Studio > Open > escolha esta pasta e aguarde o Sync.

## 2. Trocar seus dados
Em `app/build.gradle.kts` troque `SEU_USUARIO` e `SEU_REPOSITORIO` pelos do seu GitHub.
Se quiser outro nome de pacote, troque `com.meuapp.player` (namespace e applicationId).

## 3. Rodar no celular
Ative a depuração USB no celular, conecte e clique em Run.

## 4. Publicar versão assinada
1. Crie a chave: `keytool -genkeypair -v -keystore meuapp.jks -alias meuapp -keyalg RSA -keysize 2048 -validity 10000`
2. No GitHub: Settings > Secrets and variables > Actions, crie: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
3. `git tag v1.0.0 && git push origin v1.0.0`
4. O Actions compila, assina e publica o APK em Releases. O app instalado detecta versões novas sozinho.
