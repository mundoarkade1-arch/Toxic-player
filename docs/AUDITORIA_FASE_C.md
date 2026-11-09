# Auditoria de áudio e importação — fase C/D

## Correções incluídas nesta revisão

- Perfis JSON agora exportam e restauram também o estado mestre `enabled`.
- Importação JSON valida se o arquivo contém campos reconhecidos do Toxic Player e rejeita JSON genérico em vez de aplicar um perfil vazio.
- Importação diferencia arquivo vazio, arquivo ilegível, cancelamento, JSON inválido e formato APO não reconhecido.
- O seletor de arquivos informa quando está sendo aberto e trata falhas ao iniciar.
- Exportação só confirma sucesso quando o Android realmente fornece um fluxo de gravação.
- Testes unitários adicionados para restauração do estado mestre, JSON sem parâmetros e arquivo vazio.
- O workflow do GitHub agora executa testes e compila um APK debug em push/PR. A publicação assinada fica separada e é manual, para que a falta de secrets de assinatura não quebre a validação normal.
- Cópias Kotlin antigas da raiz foram removidas. O código oficial está em `app/src/main/java/` e os testes em `app/src/test/java/`.

## Arquitetura DSP encontrada

- EQ gráfico de 10 bandas e paramétrico de até 16 filtros.
- Pré-amplificador, headroom automático e balanço.
- Compressor de banda completa e compressor multibanda de três bandas no DSP próprio.
- Limitador look-ahead com proteção final e detecção entre amostras aproximada (não é medidor/certificação profissional de true peak).
- Largura estéreo Mid/Side, crossfeed, nivelador, dither e medidores.
- Bass Boost, Loudness Enhancer e Virtualizer são efeitos opcionais do Android e variam conforme o fabricante.

## Limitações ainda existentes

- O processador próprio aceita PCM de 16 bits, mono ou estéreo. Outros formatos são encaminhados sem o DSP.
- Efeitos Android opcionais podem não existir ou ser ignorados pelo fabricante.
- Os testes unitários adicionados ainda precisam ser executados no Gradle/GitHub Actions. A compilação Android completa e o teste auditivo em aparelho não foram executados neste ambiente.
- O ZIP fornecido não continha Gradle Wrapper completo. O workflow instala Gradle 8.7 no GitHub Actions.

## Como validar depois do push

1. Abra a aba **Actions** do GitHub.
2. Aguarde **Testes e APK de depuração** concluir sem falhas.
3. Baixe o artefato `toxic-player-debug`.
4. Instale o APK em um aparelho de teste e valide importação/exportação de JSON e reprodução.
5. Só depois use `workflow_dispatch` para tentar publicar uma versão assinada, com os quatro secrets de assinatura configurados.
