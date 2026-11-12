# ChaveiroCar multiplataforma — arquitetura

Status: em desenvolvimento (branch `feat/multiplataforma-api`). Nenhuma versão publicada.

## Visão geral

```text
Android (Kotlin/Compose) ─┐
iOS (SwiftUI)             ├── HTTPS/JSON ──> API ChaveiroCar (Fastify) ──> Turso (libSQL)
Windows (WPF/.NET)       ─┘                        │
                                                   ├── Object storage (R2/S3) para fotos
                                                   └── SMTP transacional para convites/reset
```

- Uma única API versionada em `/api/v1` atende os três clientes.
- Somente o backend conhece o token do Turso e as credenciais do storage.
- Cada cliente mantém cache local e uma fila de operações (outbox) para uso offline.
- O Google Drive deixa de ser infraestrutura de dados. A remoção do código só acontece depois da migração validada (#40).

## Componentes

| Componente | Tecnologia | Motivo |
|---|---|---|
| Backend | Node.js 24 + TypeScript + Fastify 5 | Tipagem, validação por schema, baixo consumo, ecossistema maduro, OpenAPI nativo |
| Banco | Turso (libSQL/SQLite) | SQL familiar, réplica embarcada, plano gratuito adequado ao volume inicial |
| Contratos | OpenAPI 3.1 gerado das rotas | Fonte única para Android, iOS, Windows e testes |
| Fotos | S3-compatível (Cloudflare R2) | Não inflar o banco; egress gratuito no R2; interface local para desenvolvimento |
| E-mail | SMTP transacional (Brevo/Resend/SES) | Convites e recuperação de senha sem acoplar provedor |
| Android | Kotlin + Jetpack Compose (app atual) | Preservar identidade, telas e dados já publicados |
| Windows | .NET 10 + WPF | Nativo no Windows, build reproduzível, sem runtime web adicional |
| iOS | SwiftUI | Interface nativa, Keychain e Photo Picker; projeto Xcode separado |

Decisões detalhadas em `docs/adr/`.

## Multiempresa (tenant)

- `empresas` é o workspace. Todo dado de domínio carrega `empresa_id`.
- `membros` liga usuário a empresa com papel `proprietario`, `administrador`, `colaborador` ou `leitor`.
- O backend valida a membership em **toda** rota com escopo de empresa; nunca confia em filtro do cliente.
- Consultas sempre incluem `empresa_id = ?`. Testes de acesso cruzado fazem parte da suíte.
- Um usuário pode ter várias empresas; o cliente escolhe o workspace ativo.

## Identidade e sessão

- Senha com `scrypt` (salt aleatório, parâmetros versionados) via `node:crypto`.
- Access token opaco de curta duração (15 min) e refresh token rotativo (60 dias), ambos armazenados apenas como hash SHA-256.
- Reuso de refresh revoga a sessão inteira. Logout por dispositivo ou global.
- Verificação de e-mail e redefinição de senha usam tokens de uso único com hash e validade.
- Rate limit reforçado nas rotas de autenticação; respostas de recuperação não revelam se a conta existe.
- Clientes guardam tokens no Keystore/Keychain/Credential Manager. PIN/biometria continuam sendo proteção local opcional.

## Sincronização offline

- IDs são UUID gerados no cliente. O servidor aceita o ID na criação, evitando colisão e permitindo operação offline.
- Cada registro tem `revisao` monotônica e `atualizado_em` do servidor.
- Toda alteração grava um evento em `mudancas` (log incremental por empresa).
- `GET /sync?cursor=N` devolve o estado atual dos registros alterados após N, incluindo tombstones (`excluido_em`).
- `POST /sync` recebe um lote idempotente de operações com `opId` e `baseRevisao`.
  - `baseRevisao` igual à revisão atual: aplica.
  - divergente: responde `409`/`conflito` com o registro do servidor. O cliente decide explicitamente (manter local ou aceitar servidor).
  - `opId` repetido devolve o resultado anterior, sem duplicar.
- Exclusões são soft delete com tombstone. Registros excluídos não ressuscitam em pulls atrasados.
- Fotos têm metadados no banco e bytes no storage. O cliente só remove a cópia local após confirmação do upload.

## Segurança

- HTTPS obrigatório em produção; HSTS via Helmet.
- CORS restrito por allowlist de origens.
- Limite de corpo, tamanho de arquivo e quantidade de fotos.
- Uploads validam MIME real por assinatura, extensão, tamanho e checksum SHA-256.
- Download de foto passa pela API e exige sessão + membership da empresa do registro.
- Logs estruturados sem senha, token, PIN ou conteúdo sensível; `requestId` em todas as respostas.
- Auditoria de login, convites, papéis, revogação e alterações de domínio.
- Segredos apenas em variáveis de ambiente; `.env` é ignorado pelo Git.

## Limites e custos (verificar antes do deploy)

- Turso Free (anunciado em 12/11/2026): 5 GB de banco, 500 mi leituras/mês, 10 mi escritas/mês. É banco, não hospedagem de API nem storage de fotos.
- R2 Free: 10 GB de armazenamento e egress gratuito (verificar condições atuais).
- API: Render/Fly/Railway têm faixa gratuita ou de baixo custo; o plano de produção deve incluir HTTPS, variáveis secretas e backup.
- SMTP: Brevo/Resend têm franquia gratuita limitada; validar entrega e domínio antes de produção.

Números e condições mudam; `docs/operacao.md` mantém a tabela de custos e o procedimento de verificação.

## Organização do repositório

```text
app/                    Android existente (preservado)
backend/                API, domínio, migrations e testes
contracts/openapi.json  Contrato gerado (versionado)
docs/                   Arquitetura, ADRs, operação e migração
ios/                    Projeto SwiftUI
windows/                Projeto WPF/.NET
scripts/                Automação local
.github/workflows/      CI (sem gatilho de release nesta branch)
```

O diretório `app/` não é movido para `android/` para não quebrar builds, workflows e histórico.

## Fora de escopo desta etapa

- Publicação de APK/IPA/EXE, tags, releases, deploy de produção e merge na `main`.
- Remoção do Google Drive antes da migração comprovada.
- Faturamento, planos comerciais e App Store/Play Store.
