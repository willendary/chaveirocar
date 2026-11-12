# ChaveiroCar API

Backend único dos aplicativos Android, iOS e Windows. Regra de negócio, autenticação, sincronização e storage só aqui. Clientes não acessam Turso nem storage direto.

## Rodar local

```bash
cd backend
npm install --ignore-scripts
cp .env.example .env   # ajuste se quiser
npm run migrar         # aplica migrations em var/chaveirocar.db (SQLite local)
npm run dev            # http://localhost:8787 — docs em /docs
```

Sem Turso, `TURSO_URL=file:./var/chaveirocar.db` usa SQLite local. Em produção, `TURSO_URL` aponta para o Turso + `TURSO_TOKEN`.

## Configuração

Ver `.env.example`. Segredos só em variável de ambiente. Nunca commite `.env`.

| Variável | Obrigação |
|---|---|
| `TURSO_URL`, `TURSO_TOKEN` | Produção exige Turso real |
| `STORAGE_TIPO=local\|s3` | `local` dev; `s3` = R2/S3 com `S3_*` |
| `SMTP_URL` | Obrigatório em produção; sem ele, links vão para o log apenas em dev/teste |
| `EXPOR_TOKEN_DEV` | Nunca `true` em produção |

Migrações em `migrations/`, aplicadas por `npm run migrar` e na subida do serviço. Contrato em `../contracts/openapi.json` (`npm run openapi` regenera).

## Deploy no Render

1. New → Blueprint → repositório `willendary/chaveirocar` (lê `render.yaml`).
2. Preencher no painel: `TURSO_URL`, `TURSO_TOKEN`, `URL_PUBLICA`, `S3_*` (R2/B2), `SMTP_URL` e `EMAIL_REMETENTE`.
3. Storage `local` não serve no Render (disco efêmero); use `STORAGE_TIPO=s3`.
4. Health check em `/pronto`. Migrações rodam no boot.

Detalhes e custos em `../docs/operacao.md`.

## Testes

```bash
npm test
```

Suíte usa `fastify.inject` + SQLite temporário, sem rede.
