# Operação — ChaveiroCar

## Deploy

1. Criar base Turso `turso db create chaveirocar` e gerar token de leitura/escrita.
2. Bucket S3-compatível (R2) + credenciais. Fotos ficam fora do banco.
3. SMTP transacional (Brevo/Resend/SES) + domínio remitente verificado.
4. Subir container `backend/Dockerfile` com `.env` de produção (`AMBIENTE=producao`).
5. Conferir `/saude`, `/pronto` e `npm run openapi` sem diff em `contracts/`.

### Render

Blueprint `render.yaml` (raiz): serviço Docker com contexto `backend/`, health check `/pronto`.
Migrações rodam na subida (`servidor.ts`), então o primeiro boot já cria o esquema no Turso.

Obrigatório no painel (Environment):

| Variável | Valor |
|---|---|
| `TURSO_URL` | `libsql://<base>.turso.io` |
| `TURSO_TOKEN` | token de leitura/escrita |
| `URL_PUBLICA` | `https://<serviço>.onrender.com` (sem barra final) |
| `STORAGE_TIPO` | `s3` — disco do Render é efêmero |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` | Backblaze B2 / S3 |
| `SMTP_URL`, `EMAIL_REMETENTE` | SMTP obrigatório em produção para enviar confirmação, convites e recuperação de senha |
| `ORIGENS_PERMITIDAS` | origens web, se houver; apps nativos não enviam Origin |

`PORT` é injetada pelo Render e lida automaticamente. Plano Free serve apenas para homologação: dorme após 15 minutos sem tráfego, o cold start pode levar cerca de um minuto, não tem disco persistente e o Render bloqueia saída SMTP nas portas 25, 465 e 587. Gmail SMTP nessas portas não entrega e-mail. Não usar Free como hospedagem oficial.

Para Backblaze B2: endpoint `https://s3.us-east-005.backblazeb2.com`, região `us-east-005`, bucket `chaveirocarfotos`. Gere Application Key limitada a esse bucket, com leitura, escrita e exclusão de arquivos.

Render Free não pode usar Gmail SMTP (465/587). Para produção, use provedor transacional com domínio remetente verificado; Resend também oferece SMTPS na porta 2465, não bloqueada pela lista publicada do Render. Testar entrega antes de liberar cadastro e convites.

## Estado da produção (13/11/2026)

- API `1.6.1` em `https://chaveirocar-api.onrender.com` (Render Free, região Oregon). `/pronto` responde `{"status":"ok","banco":"ok"}`.
- Banco Turso `libsql://chaveirocar-willendary.aws-sa-east-1.turso.io`; token somente nas variáveis do Render.
- Fotos no Backblaze B2, bucket `chaveirocarfotos`, endpoint `https://s3.us-east-005.backblazeb2.com`.
- **Pendência de storage:** upload responde `502 INDISPONIVEL` com `motivo: InvalidAccessKeyId`. O código já está correto (checksum S3 desativado); a chave do B2 no Render não confere. Recolar `S3_ACCESS_KEY_ID` (o *keyID* da Application Key, não o nome) e `S3_SECRET_ACCESS_KEY` em Environment e redeployar.
- **E-mail transacional pendente:** Render Free bloqueia SMTP nas portas 25/465/587. Cadastro e login funcionam sem verificação; recuperação de senha e convite por e-mail dependem de um provedor com porta alternativa (Brevo/Resend) ou domínio remetente verificado.
- **Branch do Render:** o serviço ainda acompanha `feat/multiplataforma-api`. O `render.yaml` pede `main`, mas a troca precisa ser confirmada no painel (Settings → Build & Deploy → Branch). Enquanto isso, todo commit de produção deve ser enviado para os dois branches (`main` e `feat/multiplataforma-api`).
- Releases `v1.6.0` e `v1.6.1` publicadas com APK assinado e ZIP Windows autocontido.

## Diagnóstico de storage

| Sintoma | Causa provável | Ação |
|---|---|---|
| `InvalidAccessKeyId` | keyID errado, colado com espaço ou de outra conta | gerar/recolar Application Key no painel do Render |
| `SignatureDoesNotMatch` | secret errado | recolocar `S3_SECRET_ACCESS_KEY` |
| `NoSuchBucket` | bucket ou região divergentes | conferir `S3_BUCKET` e `S3_REGIAO` |
| `AccessDenied` | chave sem permissão de leitura/escrita/exclusão | recriar a Application Key com as três permissões no bucket |

O endpoint de upload devolve o motivo real em `erro.detalhes.motivo`; não é necessário ler log do Render para identificar a causa.

## Backup e recuperação

- Banco: criar dump Turso diário (`turso db shell <base> .dump > backup.sql`). Restaurar com `.restore` em base nova e comparar `SELECT COUNT(*) FROM atendimentos` + `mudancas`.
- Storage: versionamento do bucket quando disponível + replicação entre regiões. A chave inclui o identificador e checksum da foto para permitir reenvio idempotente e conferência do arquivo.
- Banco e storage são consistentes para leitura ordenada. Se um arquivo sumir, o registro continua com `checksum` e `storage_key` para reimportar do backup Android.
- Retenção: `RETENCAO_MUDANCAS_DIAS` (padrão 180). Clientes offline por mais tempo fazem resync completo.
- Restauração testada antes de remover o Google Drive (#40): importar ZIP Android em ambiente isolado e comparar contagens.

## Segurança

- HTTPS + HSTS (Helmet). CORS por allowlist `ORIGENS_PERMITIDAS`. App nativo não manda Origin.
- Senhas `scrypt`, tokens opacos com hash SHA-256, refresh com rotação/revogação de família.
- Rate limit global + reforçado em auth/convites. Listar `auditoria` mostra login, convites, papéis e importações.
- Múltiplas instâncias: rate limit em memória vale por instância; migrar para Redis quando houver mais de uma réplica.
- Logs têm `requestId` e redact em `Authorization`. Senha/token nunca entram em log.

## Custos (verificar antes do deploy)

| Item | Franquia anunciada* | Uso do ChaveiroCar |
|---|---|---|
| Turso Free | 5 GB banco, 500 mi leituras/mês, 10 mi escritas/mês | Banco só de metadados; fotos fora |
| Backblaze B2 | Conferir franquia vigente | Fotos privadas `empresa/.../fotoId-checksum` |
| SMTP (Brevo/Resend) | ~300 e-mails/dia | Convites + recuperação |
| Hospedagem container | Faixa gratuita curta por região | 1 processo Fastify |

\* Condições mudam. Conferir página do provedor na data do deploy e registrar aqui o que foi verificado.
