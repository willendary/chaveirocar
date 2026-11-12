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
| `SMTP_URL` | Sem ele, convites/links vão para o log (fora de produção) |
| `EXPOR_TOKEN_DEV` | Nunca `true` em produção |

Migrações em `migrations/`, aplicadas por `npm run migrar` e na subida do serviço. Contrato em `../contracts/openapi.json` (`npm run openapi` regenera).

## Testes

```bash
npm test
```

Suíte usa `fastify.inject` + SQLite temporário, sem rede.
