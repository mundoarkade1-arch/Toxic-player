# Estado do projeto

O diretório Android ativo é `app/` na raiz. Arquivos Kotlin soltos e uma cópia avulsa do workflow que existiam na raiz eram snapshots antigos e não faziam parte da compilação (o workflow **Limpar** os remove).

## Áudio local

O player local usa Media3/ExoPlayer e o DSP próprio. O processador sinaliza inatividade para formatos diferentes de PCM 16 bits mono/estéreo; efeitos de hardware dependem do fabricante do telefone.

## Compilação

O workflow de GitHub Actions instala Gradle 8.7, roda os testes unitários (job `test`, sem acesso aos secrets) e, se passarem, compila e publica o APK assinado (job `release`, ambiente `release`).

## Segurança (resumo das correções da auditoria)

- Presets/backups importados: todos os valores são limitados às mesmas faixas dos controles da tela; NaN/Infinito são descartados; no máximo 64 perfis, nomes de até 60 caracteres.
- Arquivos importados (M3U, presets) têm limite de 1 MB.
- `PlaybackService` não é mais exportado e só aceita controladores do próprio app ou confiáveis do sistema.
- Atualizador: só baixa por https de servidores do GitHub e do repositório deste app, com limite de tamanho, hash em blocos e verificação de assinatura antes de instalar.
- `allowBackup` desativado; release com R8 ativado; lint de release ligado.
- CI: testes sem secrets, keystore só gravada na hora de assinar, ambiente `release` com aprovação, Dependabot para Actions e bibliotecas.

### Configurações do GitHub que NÃO podem ser feitas por arquivo (faça no site)

1. Settings > Branches: proteger `main` (exigir Pull Request e revisão, bloquear push direto).
2. Settings > Environments > `release`: marcar "Required reviewers" e restringir à branch `main`.
3. Mover os secrets de assinatura (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) para esse ambiente.
