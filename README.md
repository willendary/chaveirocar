# ChaveiroCar

App Android para chaveiro automotivo registrar os carros que já atendeu: defeito
encontrado, solução aplicada, ferramentas usadas e fotos. Serve como memória de
bolso — busca por placa, modelo, defeito ou solução.

100% offline. Nenhum dado sai do celular.

## Funcionalidades

- Cadastro de serviço por **placa** (aceita Mercosul e placa antiga) com marca,
  modelo, ano, chassi, data, tipo de serviço, defeito, solução, ferramenta e valor.
- **Fotos** tiradas na hora ou escolhidas da galeria (várias por registro).
- Busca instantânea em todos os campos, sem acento e sem diferenciar maiúsculas.
- Ao digitar a placa de um carro já atendido, o app mostra o histórico dele.
- Botão **Novo p/ este carro** para repetir um atendimento do mesmo veículo.
- **Backup** completo em ZIP (dados + fotos) para exportar/importar.

## Onde ficam os dados

| O quê | Onde |
|---|---|
| Banco SQLite | `/data/data/com.willendary.chaveirocar/databases/chaveirocar.db` |
| Fotos JPEG | `/data/data/com.willendary.chaveirocar/files/fotos/` |

A pasta é privada do app. Desinstalar apaga tudo — **exporte o backup antes**.

## Como compilar

Requisitos: JDK 17 e Android SDK (platform 37, build-tools 37).

```bat
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Testes unitários:

```bat
gradlew.bat test
```

## Como instalar no celular

1. Copie o `.apk` para o celular (USB, Drive ou WhatsApp para você mesmo).
2. Abra o arquivo e permita "instalar de fontes desconhecidas" para o app que
   abriu (geralmente Arquivos ou Chrome).
3. Instale e abra o ChaveiroCar.

## Estrutura

```
app/src/main/kotlin/com/willendary/chaveirocar/
├── App.kt                    navegação entre as telas
├── MainActivity.kt
├── dados/
│   ├── BancoDeAtendimentos.kt  SQLite: registros, fotos, busca
│   ├── Fotos.kt                salvar/ler/apagar JPEG em disco
│   └── Backup.kt               exportar/importar ZIP
├── modelo/Atendimento.kt
├── ui/
│   ├── TelaLista.kt
│   ├── TelaFormulario.kt
│   ├── TelaDetalhe.kt
│   └── Componentes.kt
└── util/Texto.kt             normalização de busca e formatação
```

## Versão

1.0 — histórico de carros/serviços com fotos e backup. Próximos passos possíveis:
orçamento, cadastro de clientes, fichário técnico de chaves/transponder.
