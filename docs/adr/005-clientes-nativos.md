# ADR 005 — Clientes nativos sobre contrato comum

Status: aceito (12/11/2026)

## Contexto

Existe um app Android maduro em Kotlin/Compose. Precisamos de Windows e iOS com a mesma API. Avaliamos Kotlin Multiplatform/Compose Multiplatform para compartilhar código.

## Decisão

- Android permanece Kotlin/Compose, evoluindo o projeto atual.
- Windows usa .NET 10 + WPF.
- iOS usa SwiftUI.
- O compartilhamento entre plataformas é o **contrato OpenAPI**, não a camada de UI.
- Regras de negócio ficam no backend; clientes cuidam de apresentação, cache e fila offline.

## Alternativas

- **Compose Multiplatform Desktop/iOS**: reutilizaria Kotlin, mas o app atual depende de APIs Android (SQLite, FileProvider, ML Kit, biometria) e o suporte iOS ainda exige toolchain Apple e ajustes de maturidade.
- **Flutter**: reescrita completa do Android, contrariando a diretriz de preservar o app publicado.
- **Web app empacotado**: não atende requisitos nativos de câmera, armazenamento seguro e experiência desktop.

## Consequências

- Três bases de UI, mas um único contrato e uma única regra de negócio.
- Testes de contrato/E2E entre plataformas são obrigatórios.
- A lógica de sincronização é implementada por plataforma, seguindo o mesmo protocolo documentado.
