# Guia para agentes e colaboradores

- Código ativo: `app/src/main/java/com/meuapp/player/`. Testes: `app/src/test/java/`. Não crie cópias soltas na raiz.
- Toxic Cloud vive em `cloud/` (lógica) e `ui/CloudScreen.kt` (tela). O estado é um `CloudManager` dentro do `PlayerViewModel` (`vm.cloud`).
- Faixas da nuvem usam `Track.cloudId != null`, id numérico negativo (`CloudTrack.numericId`) e URI interna `toxiccloud://drive/<fileId>`. Nunca coloque link do Drive nem token em MediaItem, log ou mensagem de erro.
- Token OAuth só em memória (`CloudAuth`). Nunca grave token em disco/prefs nem adicione Client Secret ou keystore ao repositório.
- Escopo do Drive fica só em `CloudConfig.DRIVE_SCOPE`. Qualquer escopo maior exige justificativa em `docs/TOXIC_CLOUD.md`.
- Sem backend, Firebase ou serviço pago. Sem sincronização em segundo plano periódica.
- Não declare algo como testado sem ter executado (`gradle testDebugUnitTest assembleDebug`). Atualize `PROJECT_STATE.md` e `CHANGELOG.md` a cada mudança.
