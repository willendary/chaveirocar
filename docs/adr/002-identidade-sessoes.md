# ADR 002 — Identidade própria com sessões opacas

Status: aceito (12/11/2026)

## Contexto

O cliente exige login individual, convites por e-mail, recuperação de senha, sessões em vários dispositivos e revogação imediata. A integração atual com Google Drive não é identidade e será removida.

## Decisão

- Cadastro e login por e-mail + senha.
- Senha derivada com `scrypt` do `node:crypto`, salt por usuário e parâmetros registrados no hash.
- Access token opaco (256 bits) com validade de 15 minutos; refresh token opaco (384 bits) com validade de 60 dias e rotação a cada uso.
- Banco guarda somente SHA-256 dos tokens.
- Reuso de refresh revoga a sessão; logout global revoga todas.
- Tokens de verificação e redefinição são de uso único, com hash e prazo.
- E-mail transacional via SMTP configurável. Sem SMTP em desenvolvimento, o link é gravado no log e exposto apenas em ambiente não-produção.

## Alternativas

- **JWT**: dispensa consulta ao banco, mas revogação imediata e rotação de refresh ficam mais complexas.
- **Auth0/Clerk/Firebase Auth**: reduz código, mas adiciona custo e dependência externa para um cenário simples.
- **Login Google**: não atende e-mail/senha nem o requisito de remover o ecossistema Google do produto.

## Consequências

- Toda requisição autenticada consulta a sessão no banco (uma leitura indexada).
- Clientes precisam renovar o access token e tratar 401 com refresh transparente.
- Em múltiplas instâncias, o rate limit por e-mail em memória deve migrar para Redis; documentado em `docs/operacao.md`.
