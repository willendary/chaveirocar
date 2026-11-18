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
5. Render Free é somente para homologação e bloqueia SMTP nas portas 25, 465 e 587; Gmail SMTP não envia por essas portas. Não liberar produção neste plano.

Detalhes e custos em `../docs/operacao.md`.

## Consulta automática de placas (sem API Key)

O endpoint autenticado `GET /api/v1/empresas/:empresaId/veiculos/:placa` usa
a API Parallelum (`/api/v2/plates/{plate}`), sem token, e retorna somente
`modelo`, `marca` e `ano`. Placas antigas e Mercosul são suportadas.

**Limite do fornecedor sem token:** 5 consultas por mês (endpoint em Alpha).
Os resultados bem-sucedidos ficam no Turso por até 90 dias, na tabela
`cache_consultas_placa` (migration `004`), para poupar a cota.
Respostas não encontradas não são armazenadas. A autenticação, o módulo
automotivo e os limites de requisições da rota são preservados.

Quando o limite mensal é atingido, o backend retorna HTTP 429. O cadastro
manual do modelo continua disponível, inclusive offline. O provedor pode
alterar disponibilidade e limites; não é uma fonte garantida para grande escala.

A antiga integração Falcon Data Hub e a variável `FALCON_DATA_HUB_API_KEY`
foram removidas; nenhuma chave é necessária para a Parallelum.

## Testes

```bash
npm test
```

Suíte usa `fastify.inject` + SQLite temporário, sem rede.
