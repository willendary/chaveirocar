# Windows

WPF .NET 10. Consome a API (contratos em `../contracts/openapi.json`).

- `LoginWindow` — entrada por e-mail/senha + endereço da API.
- `MainWindow` — lista do cache, novo/editar/excluir/sincronizar, empresas, status (cursor/fila/conflitos).
- `Services/ArmazenamentoLocal` — cache + outbox em `%LocalAppData%\ChaveiroCar\cache.json`.
- `Services/MotorSincronia` — push idempotente + pull por cursor; divergência vira conflito visível.

Build: `dotnet build -c Debug`. Publicação futura: `dotnet publish -c Release -r win-x64 --self-contained` (MSIX com ícone entra depois).
