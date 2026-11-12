# ADR 001 — Backend em TypeScript + Fastify

Status: aceito (12/11/2026)

## Contexto

O app Android concentra hoje regra de negócio, SQLite e backup no Google Drive. Precisamos de uma API única para Android, iOS e Windows, com validação forte e documentação OpenAPI.

## Decisão

- Node.js 24 + TypeScript estrito.
- Fastify 5 com schemas TypeBox: a mesma definição valida a requisição e gera o OpenAPI.
- `@libsql/client` como único acesso ao Turso.
- Testes com Vitest e `fastify.inject` (sem porta de rede).

## Alternativas

- **NestJS**: estrutura pronta, porém mais peso e mais conceitos para um domínio pequeno.
- **Go/Rust**: ótimos em desempenho, mas custo de desenvolvimento e contratação maior para o time atual.
- **Supabase/Firebase**: reduziria backend próprio, mas acoplaria autenticação/storage e dificultaria o modelo offline com revisões explícitas.

## Consequências

- Contrato versionado em `contracts/openapi.json`, regenerado por script.
- Regra de negócio em serviços testáveis, rotas apenas orquestram.
- Deploy simples em container (Render/Fly/Railway) com um único processo.
