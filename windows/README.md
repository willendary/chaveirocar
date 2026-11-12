# Windows

WPF .NET 10. Consome a API (contratos em `../contracts/openapi.json`).

- `LoginWindow` — entrada por e-mail/senha + endereço da API.
- `MainWindow` — atendimentos e fichas, busca, edição, fotos, equipe, status (cursor/fila/conflitos) e exportação via impressora PDF do Windows.
- `Services/ArmazenamentoLocal` — cache + outbox em `%LocalAppData%\ChaveiroCar\cache.json`.
- `Services/MotorSincronia` — push idempotente + pull por cursor; divergência sinalizada e fotos mantidas localmente até confirmação.
- `Services/VerificadorAtualizacao` — consulta GitHub Releases ao iniciar e abre download do pacote Windows quando há versão mais nova.

Build: `dotnet build -c Debug`. Pacote: `dotnet publish -c Release -r win-x64 --self-contained true`.
Distribuição inicial é ZIP portátil; o aviso baixa o ZIP e a pessoa substitui a pasta do aplicativo. Pacote ainda sem assinatura digital, então Windows pode exibir aviso do SmartScreen.
