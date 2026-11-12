# ADR 003 — Sincronização com revisão e conflito explícito

Status: aceito (12/11/2026)

## Contexto

Android e Windows precisam funcionar offline e sincronizar com iOS. Duas pessoas podem editar o mesmo atendimento sem conexão. Sobrescrever silenciosamente é inaceitável.

## Decisão

- IDs UUID gerados no cliente; o servidor não renumera.
- Cada registro tem `revisao` inteira, incrementada a cada alteração, e `excluido_em` para tombstone.
- Log `mudancas` por empresa fornece pull incremental por cursor.
- Push usa lote idempotente (`opId`) com `baseRevisao`.
- Divergência de revisão retorna conflito com o estado do servidor; o cliente mostra as duas versões e escolhe.
- Exclusões são soft delete; pulls nunca removem um tombstone antes da retenção definida.
- Fotos usam o mesmo modelo: metadados versionados; bytes enviados separadamente.

## Alternativas

- **Last-write-wins por timestamp**: simples, mas perde edição silenciosamente.
- **CRDT por campo**: melhor fusão automática, complexidade e tamanho incompatíveis com o primeiro cliente.
- **Event sourcing completo**: poderoso, mas exigiria reescrever todos os fluxos atuais.

## Consequências

- O cliente precisa manter `baseRevisao` junto ao registro e uma outbox persistente.
- Conflitos exigem interface de resolução nas três plataformas.
- A retenção do log e dos tombstones é configurável; clientes muito tempo offline fazem resync completo.
