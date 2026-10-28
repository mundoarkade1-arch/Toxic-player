# Auditoria do Toxic Player (Fase A)

Base analisada: os arquivos gerados para o repositório (sem acesso direto ao GitHub nesta auditoria).

## Problemas encontrados

### Atualizações
1. A comparação usava a TAG da Release contra o versionName do app. Se o app instalado vem de um build com
   versionName diferente da tag (ex.: APK de teste do Actions = 1.0.0, ou tag criada à mão), ele acha que sempre há
   versão nova, mesmo depois de instalar. **Causa provável do aviso que não some.**
2. Builds de push na main eram "debug" com versionName fixo 1.0.0: confundiam com a versão oficial.
3. Escolhia o primeiro asset terminado em .apk, sem checar que era o do app nem a versão dele.
4. Sem validação do download (tamanho, hash), sem conferir assinatura/pacote/versionCode antes de instalar.
5. Sem cache/ETag (risco do limite de 60 consultas/hora do GitHub) e sem distinguir "sem internet" de "sem update".
6. Dava para tocar no botão várias vezes e disparar downloads repetidos.

### Workflow
1. Só publicava com tag criada manualmente; push na main gerava apenas APK de teste.
2. Sem controle de concorrência, sem garantia de tag única nem de versionCode crescente.
3. Sem validar a assinatura do APK; keystore gravada dentro da pasta do repositório.
4. Sem testes no pipeline.

### Áudio (para a Fase C)
1. EQ de 10 bandas usa DynamicsProcessing: bandas por frequência de corte, sem Q, sem shelves, sem notch, sem HP/LP.
2. Compressor multibanda aplica os mesmos parâmetros nas 3 bandas; sem knee, sem detector selecionável, sem medidor.
3. Limitador sem look-ahead; sem medidores de pico/RMS; barras do Player são decorativas.
4. Bass Boost e Virtualizer dependem do fabricante.
5. Balanço feito por ganho de entrada (aceitável, mas não é panorama).

### Outros
- Falta o gradle-wrapper.jar no repositório (o workflow usa o Gradle instalado pelo setup-gradle).
- Faixas sem permissão/arquivo removido: tratadas só com "lista vazia".

## Correções da Fase B (este pacote)
Veja o README do pacote. Fonte da verdade da versão = versionCode escrito no nome do APK
(ToxicPlayer-v1.2.7-c107.apk), conferido de novo no APK baixado.

## O que NÃO foi possível fazer aqui
- Compilar o projeto e rodar os testes (sem Android SDK/Kotlin/rede nesta sessão).
- Testar em aparelho real.
