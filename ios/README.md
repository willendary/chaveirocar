# ChaveiroCar iOS (`iPhone` e `iPad`)

Aplicativo iOS em **Swift / SwiftUI** conectado à mesma API (`/api/v1`), banco Turso e storage de fotos do Android e do Windows.

## Arquitetura e módulos (`Package.swift`)

- **`ChaveiroCarCore`** (`Sources/ChaveiroCarCore/`):
  - `Modelos.swift`: modelos compartilhados (`Atendimento`, `SegmentoAtendimento` Automotivo/Residencial, `TipoServico`, `FichaTecnica`, `FotoAtendimento`, `SetaAnotacao`), validação de placa opcional + modelo obrigatório no automotivo e campos de fechadura/imóvel no residencial.
  - `KeychainArmazenamento.swift`: persistência segura de sessão e tokens (`acesso`, `refresh`, `empresaId` e `AmbienteApi`) no **iOS Keychain** (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`). Nenhuma credencial administrativa do Turso ou de storage é embutida no app.
  - `ArmazenamentoLocal.swift`: cache offline-first em disco + gerenciamento das fotos anotadas e originais (`orig_<uuid>.jpg`), fila de operações (`OperacaoSaida`), conflitos (`ConflitoSinc`) e consolidação do **Fichário** por segmento (Veículos vs Residencial).
  - `ClienteApi.swift`: cliente HTTP assíncrono compatível com `contracts/openapi.json`, com renovação automática de sessão em `401` (`/api/v1/auth/renovar`), suporte a login por senha, **Entrar com Google** (`/api/v1/auth/google-login`), convites, equipe e sincronização.
  - `MotorSincronia.swift`: motor de sincronização incremental (`pull` + `push` idempotente + upload/download de fotos com `anotacoes` e resolução explícita de conflitos: **Manter meu aparelho** ou **Aceitar servidor**).
- **`ChaveiroCarApp`** (`Sources/ChaveiroCarApp/`):
  - Telas SwiftUI adaptáveis para iPhone e iPad: Login (e-mail/senha, cadastro e Google), Lista com busca e filtro por segmento (`Todos`, `Automotivo`, `Residencial`), Formulário dinâmico Automotivo/Residencial, Detalhe, **Anotador de fotos com setas** (`Canvas` + `DragGesture`), Fichário segmentado, Consulta Técnica offline e Ajustes/Equipe.
- **`ChaveiroCarTests`** (`Tests/ChaveiroCarTests/`):
  - Testes automatizados (`XCTest`) cobrindo regras de validação automotiva e residencial, serialização de setas e preservação da foto original, cache offline, fichário e resolução de conflitos.

## Como compilar e testar (macOS / Xcode 16+)

```bash
cd ios
swift build
swift test
```

Para abrir no Xcode em iPhone ou iPad (Simulador ou dispositivo físico):

```bash
open ios/Package.swift
```

## Ambientes suportados

Na tela de login ou em Ajustes é possível alternar entre:
- **Produção**: `https://chaveirocar-api.onrender.com`
- **Homologação**: `https://chaveirocar-api-hml.onrender.com`
- **Desenvolvimento**: `http://localhost:3333`
