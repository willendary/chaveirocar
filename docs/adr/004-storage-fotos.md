# ADR 004 — Fotos em object storage, metadados no Turso

Status: aceito (12/11/2026)

## Contexto

Fotos de veículos e procedimentos são o maior volume do produto. O plano do Turso mede banco de dados, não arquivos. Guardar JPEG no SQLite/Turso inflaria backup, leitura e custo.

## Decisão

- Bytes em storage S3-compatível (Backblaze B2 no ambiente ChaveiroCar; filesystem local em desenvolvimento/teste).
- Turso guarda metadados: empresa, vínculo, categoria, ordem, legenda, MIME, tamanho, checksum e `storage_key`.
- Upload e download passam pela API autenticada. Isso evita bucket público e URL permanente.
- Validação por assinatura do arquivo, limite de 15 MB e 20 fotos por registro.
- Exclusão é soft delete; limpeza física é tarefa assíncrona com período de retenção.
- Para produção, o adaptador S3 pode emitir URL assinada de curta duração; o cliente Android atual usa download autenticado direto.

## Alternativas

- **Base64 no banco**: simples, mas multiplica tamanho e custo.
- **Bucket público**: inseguro e incompatível com isolamento por empresa.
- **Cloudflare R2 / Supabase Storage**: alternativas compatíveis; Backblaze B2 foi escolhido para o bucket de homologação já criado.

## Consequências

- Backup precisa cobrir banco e storage de forma coordenada.
- O adaptador local existe apenas para desenvolvimento e testes; não é usado em produção.
