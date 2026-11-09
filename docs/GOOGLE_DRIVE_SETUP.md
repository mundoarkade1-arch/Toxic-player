# Configuração do Google Cloud para o Toxic Cloud

O app não contém Client ID nem segredo: o Google identifica o app pelo **nome do pacote + SHA-1 do certificado**. Não há `.env` a preencher.

1. Acesse console.cloud.google.com e crie um projeto.
2. **APIs e serviços > Biblioteca**: ative **Google Drive API**.
3. **Tela de consentimento OAuth**: tipo Externo; preencha nome e e-mail; em Escopos adicione `.../auth/drive.readonly`; em Usuários de teste adicione sua conta Google.
4. **Credenciais > Criar credenciais > ID do cliente OAuth > Android**. Crie um para cada variante:
   - Debug: pacote `com.meuapp.player.debug` + SHA-1 do keystore de debug (`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`).
   - Release: pacote `com.meuapp.player` + SHA-1 do keystore de assinatura (o mesmo usado pelo GitHub Actions: `keytool -list -v -keystore release.jks`).
5. Instale o APK, abra a aba **Cloud** e toque em **Conectar conta Google**.

## Atenção
- `drive.readonly` é escopo restrito. Em modo "Teste" funciona para até 100 usuários de teste, mas o Google pode expirar a autorização a cada 7 dias (basta reconectar). Publicar o app para todos exige verificação do Google. Para uso pessoal, colocar o app em "Produção" sem verificação evita a expiração de 7 dias (aparece aviso de app não verificado).
- Erro "configuração ausente" ao conectar = pacote/SHA-1 não batem com o cliente OAuth criado.
- Nunca envie ao GitHub keystores, `client_secret*.json` ou `google-services.json` (já estão no `.gitignore`).

## Gerar o APK
`gradle assembleDebug` (APK em `app/build/outputs/apk/debug/`) ou o workflow do GitHub Actions.

## Erros comuns
- "Sessão expirou": toque em Conectar de novo.
- "Limite de requisições": espere alguns minutos; a sincronização repete sozinha com espera crescente.
- Lista vazia: o Drive precisa ter arquivos de áudio fora da lixeira; toque em Atualizar biblioteca.
- Seek lento/streaming ruim: use "Testar streaming"; deve responder HTTP 206.
