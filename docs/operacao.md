# Operação — ChaveiroCar

## Deploy

1. Criar base Turso `turso db create chaveirocar` e gerar token de leitura/escrita.
2. Bucket S3-compatível (R2) + credenciais. Fotos ficam fora do banco.
3. SMTP transacional (Brevo/Resend/SES) + domínio remitente verificado.
4. Subir container `backend/Dockerfile` com `.env` de produção (`AMBIENTE=producao`).
5. Conferir `/saude`, `/pronto` e `npm run openapi` sem diff em `contracts/`.

## Backup e recuperação

- Banco: criar dump Turso diário (`turso db shell <base> .dump > backup.sql`). Restaurar com `.restore` em base nova e comparar `SELECT COUNT(*) FROM atendimentos` + `mudancas`.
- Storage: versionamento do bucket quando disponível + replicação entre regiões. A chave `empresa/register/fotoId.ext` + `checksum` no banco permite conferir cada arquivo.
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
| Cloudflare R2 | 10 GB storage, egress gratuito | Fotos `empresa/.../fotoId` |
| SMTP (Brevo/Resend) | ~300 e-mails/dia | Convites + recuperação |
| Hospedagem container | Faixa gratuita curta por região | 1 processo Fastify |

\* Condições mudam. Conferir página do provedor na data do deploy e registrar aqui o que foi verificado.
