# ChaveiroCar

App Android para chaveiro automotivo registrar os carros que já atendeu: defeito
encontrado, solução aplicada, ferramentas usadas e fotos. Serve como memória de
bolso — busca por placa, modelo, defeito ou solução.

Funciona offline. Nenhum dado sai do celular sem você mandar — o backup na
nuvem é opcional e fica na sua conta Google.

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
- **Backup na nuvem** opcional no Google Drive, na pasta oculta do app
  (`appDataFolder`): envia o ZIP, mantém as 4 últimas cópias e restaura em outro
  celular com a mesma conta Google, sem servidor próprio.
- **Novidades** ao atualizar: mostra o que mudou naquela versão uma única vez.
- **Atualização** pelo próprio app: verifica a última release do GitHub e, se
  houver versão nova, baixa o APK e abre o instalador. Checa sozinho uma vez
  por dia e também pelo botão "Verificar atualização" em Ajustes.

## Onde ficam os dados

| O quê | Onde |
|---|---|
| Banco SQLite | `/data/data/com.willendary.chaveirocar/databases/chaveirocar.db` |
| Fotos JPEG | `/data/data/com.willendary.chaveirocar/files/fotos/` |
| Backup na nuvem | pasta oculta do app no Google Drive (`appDataFolder`) |

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

## Como configurar o backup na nuvem

O app usa a API de autorização do Google Play Services e a API REST do Drive,
apenas com o escopo `drive.appdata`. Não existe backend nem client secret no APK.

1. Em https://console.cloud.google.com crie um projeto (ex.: `ChaveiroCar`).
2. Ative a **Google Drive API** em APIs e serviços → Biblioteca.
3. Configure a tela de consentimento OAuth (External) com o escopo
   `https://www.googleapis.com/auth/drive.appdata`. Se o app ficar em "Testing",
   cadastre a conta Google usada como usuário de teste.
4. Crie um **OAuth client ID** do tipo **Android**:
   - Pacote: `com.willendary.chaveirocar`
   - SHA-1 da keystore de release:
     `8C:6E:38:4F:90:D0:4D:D6:7B:E6:6F:9F:14:16:80:6D:A2:31:D5:DB`
   - Para testar build debug, crie outro cliente com o SHA-1 de debug:
     `76:3F:B1:F9:87:DF:C8:74:0D:F3:26:15:CD:DF:65:CA:13:B6:2C:80`
5. Espere alguns minutos e use o app assinado com essa keystore.

O SHA-1 muda se a keystore mudar — nesse caso, atualize o cliente OAuth.

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

1.3.0 — backup na nuvem opcional no Google Drive (pasta oculta do app):
conectar conta Google, enviar e restaurar, inclusive com a cópia automática
semanal.
