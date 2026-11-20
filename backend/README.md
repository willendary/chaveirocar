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

## Consulta automática de placas (prévia gratuita e HTML)

O endpoint autenticado `GET /api/v1/empresas/:empresaId/veiculos/:placa` consulta
fontes públicas de consulta veicular e retorna somente `modelo`, `marca` e
`ano` (suporta placas antigas e Mercosul). A antiga API da Parallelum (que possuía
cota gratuita limitada a 5 consultas/mês) foi removida na v1.14.0.

Fontes consultadas em ordem de prioridade:
1. **CarroFIPE** (`POST https://www.carrofipe.com.br/api/consultar`, JSON `{ "placa": "..." }`) — prévia gratuita usada pelo próprio site
2. **PlacaIPVA** (`https://placaipva.com.br/placa/{placa}`)
3. **KePlaca** (`https://www.keplaca.com/placa/{placa}`)
4. **PlacaFIPE** (`https://placafipe.com/placa/{placa}`)

Os resultados bem-sucedidos ficam em cache no Turso por até 90 dias, na tabela
`cache_consultas_placa` (migration `004`), evitando consultas externas repetidas.
Respostas não encontradas não são armazenadas. O HTML inicial de `/consultar` não
contém o resultado: o site carrega o objeto `preview` pelo endpoint JSON acima.
A integração exige `simulated: false`, rejeita `providerDown: true` e confere a
placa quando ela é repetida na resposta. Apenas `modelo`, `marca` e `ano` são
extraídos; marca/ano ausentes permanecem vazios. Não acessa checkout ou laudos pagos.

Nas fontes HTML, o parser exige placa exata e rótulos estruturados (`th/td` ou
`dt/dd`). A consulta nunca armazena chassi ou dados de proprietário e respeita
bloqueios HTTP 403/429 e CAPTCHA sem contornos. Redirecionamentos são recusados,
as respostas são limitadas a 200 KB e há um orçamento global de 60 requisições
externas por hora/processo (incluindo alternativas). A prévia tem timeout de 12 s;
cada alternativa HTML, 5 s.

HTTP 404 só é retornado quando todas as fontes consultadas confirmam a ausência.
Página genérica, JSON inesperado, demonstração, timeout ou falha em uma das fontes
resultam em HTTP 503 se nenhuma alternativa encontrar o veículo. O Android e o
Windows existentes já exibem uma mensagem de indisponibilidade para esse status.
A resposta pública do site pode mudar; esta integração não é uma API contratada
e não garante cobertura de todas as placas nem consultas ilimitadas.

A flag `PLACAS_WEB_ATIVAS` vem habilitada por padrão (`true`) e pode ser definida
como `false` caso deseje desativar a busca externa mantendo apenas o cache e o
preenchimento manual.

## Testes

```bash
npm test
```

Suíte usa `fastify.inject` + SQLite temporário, sem rede.
