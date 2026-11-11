# ChaveiroCar

App Android para chaveiro automotivo registrar os carros que já atendeu: defeito
encontrado, solução aplicada, ferramentas usadas e fotos. Serve como memória de
bolso — busca por placa, modelo, defeito ou solução.

100% offline. Nenhum dado sai do celular.

## Funcionalidades

- Cadastro de serviço por **placa** (aceita Mercosul e placa antiga) com marca,
  modelo, ano, chassi, data, tipo de serviço, defeito, solução, ferramenta e valor.
- **Abertura** (técnica usada) e **código de chave** / **código de configuração**
  por serviço, com botão de copiar.
- **Fotos** do carro e **fotos de procedimento** ("como se faz"), com legenda e
  ordem por passo.
- Busca instantânea em todos os campos, sem acento e sem diferenciar maiúsculas.
- Filtros combináveis por tipo de serviço, período (30/90 dias, este ano ou
  personalizado) e "só com fotos".
- Sugestões do próprio histórico em marca, modelo, defeito, solução e abertura.
- Ao digitar a placa de um carro já atendido, o app mostra o histórico dele.
- **Fichário** por marca/modelo/ano: quantos serviços, abertura, códigos e
  procedimentos consolidados.
- Botão **Novo p/ este carro** para repetir um atendimento do mesmo veículo.
- **Compartilhar** o registro em texto ou gerar **PDF** com os dados e as fotos.
- **Bloqueio** com PIN de 4 a 6 números e digital (quando o aparelho tem).
- **Backup** completo em ZIP (dados + fotos) para exportar/importar, mais uma
  cópia automática semanal (mantém as 4 últimas).
- **Novidades** ao atualizar: mostra o que mudou naquela versão uma única vez.
- **Atualização** pelo próprio app: verifica a última release do GitHub e, se
  houver versão nova, baixa o APK e abre o instalador. Checa sozinho uma vez
  por dia e também pelo botão "Verificar atualização" em Ajustes.

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

Também dá para baixar direto do GitHub:
`https://github.com/willendary/chaveirocar/releases/latest`

## Como organizamos as entregas

- Cada versão é um **milestone** no GitHub (ex.: `v1.2`) com **10 tarefas**.
- Quando as 10 fecham, sobe `versionCode`/`versionName`, cria a tag `vX.Y` e o CI
  publica a release.
- Na primeira abertura depois da atualização, o app mostra a tela **Novidades**
  com o texto da própria release (issue #1).

Backlog: `https://github.com/willendary/chaveirocar/issues`

## Como publicar uma versão nova

O GitHub Actions compila, testa, assina e publica sozinho quando você cria uma tag.

1. Suba o `versionCode` e o `versionName` em `app/build.gradle.kts`.
2. Faça o commit na `main`.
3. Crie e envie a tag:

```bat
git tag -a v1.2 -m "ChaveiroCar 1.2"
git push origin v1.2
```

Em alguns minutos a release aparece em `releases`. Quem já tem o app instalado
vê o aviso de atualização em até um dia (ou na hora, em Ajustes → Verificar
atualização).

Segredos usados pelo workflow (já configurados no repositório): `CHAVEIRO_KEYSTORE_BASE64`,
`CHAVEIRO_KEYSTORE_SENHA`, `CHAVEIRO_KEY_ALIAS`, `CHAVEIRO_KEY_SENHA`.

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

1.2 — fichário por carro, abertura, códigos de chave/configuração, fotos de
procedimento, filtros, sugestões, compartilhamento em PDF, bloqueio por PIN e
backup automático.

1.2.1 — corrige o fluxo de atualização: pede a autorização de "instalar apps
desconhecidos" antes de abrir o instalador e reaproveita o APK já baixado.

1.2.2 — corrige o fechamento do app ao tirar foto (erros da câmera e de
gravação da imagem agora viram aviso) e adiciona "Ver último erro do app" em
Ajustes.
