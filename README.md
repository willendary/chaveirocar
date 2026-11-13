# ChaveiroCar

App Android para chaveiro automotivo registrar os carros que já atendeu: defeito
encontrado, solução aplicada, ferramentas usadas e fotos. Serve como memória de
bolso — busca por placa, modelo, defeito ou solução.

Funciona offline. O trabalho fica no aparelho e sincroniza entre Android, iOS e
Windows pela API ChaveiroCar quando houver internet, além de permitir exportar
e importar cópias locais em `.zip`.

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
- **Consulta técnica** por equipamento → montadora → modelo → ano → sistema,
  com fichas de chip/transponder, chave, telecomando, frequência, avisos,
  funções e manual salvo para uso **offline**.
- **Leitura da placa** pela câmera (OCR no aparelho), com confirmação do texto
  antes de preencher o campo.
- **Catálogo de veículos** por marca/modelo/ano para preencher o cadastro sem
  digitar.
- Vários **tipos de serviço** e **várias fotos** de uma vez no mesmo atendimento.
- Botão **Novo p/ este carro** para repetir um atendimento do mesmo veículo.
- **Compartilhar** o registro em texto ou gerar **PDF** com os dados e as fotos.
- **Bloqueio** com PIN de 4 a 6 números e digital (quando o aparelho tem).
- **Backup** completo em ZIP (dados + fotos) para exportar/importar no aparelho e
  migrar para a empresa na API, mais uma cópia automática semanal (mantém as 4 últimas).
- **Novidades** ao atualizar: mostra o que mudou naquela versão uma única vez.
- **Conta ChaveiroCar** com e-mail/senha ou **Entrar com Google**, **equipe** com
  convites e permissões (proprietário, administrador, colaborador ou somente leitura).
- **Sincronização** de atendimentos (Automotivo e Residencial), fichas técnicas e
  fotos (com anotações em setas) entre **Android**, **iOS** e **Windows**, com fila
  offline e conflitos mostrados para escolha manual.
- **Atualização** pelo próprio app: verifica a última release do GitHub e, se
  houver versão nova, baixa o APK e abre o instalador. Checa sozinho uma vez
  por dia e também pelo botão "Verificar atualização" em Ajustes.

## Onde ficam os dados

| O quê | Onde |
|---|---|
| Banco SQLite | `/data/data/com.willendary.chaveirocar/databases/chaveirocar.db` |
| Fotos JPEG | `/data/data/com.willendary.chaveirocar/files/fotos/` |
| Dados sincronizados | API ChaveiroCar (`/api/v1`) com banco Turso e storage de fotos |
| Cache do Windows | `%LocalAppData%\ChaveiroCar` |
| Cache do iOS | `Application Support/ChaveiroCar` + Keychain |

A pasta é privada do app. Desinstalar apaga tudo — **exporte o backup antes**.

## Como compilar

Requisitos: JDK 17 e Android SDK (platform 37, build-tools 37).

```bat
set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
gradlew.bat assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Windows (requer .NET 10):

```bat
dotnet publish windows\ChaveiroCar.Windows\ChaveiroCar.Windows.csproj -c Release -r win-x64 --self-contained true -o publish
```

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

1. Suba o `versionCode` e o `versionName` em `app/build.gradle.kts` e a versão em
   `windows/ChaveiroCar.Windows/ChaveiroCar.Windows.csproj`.
2. Escreva as notas em `release/v<versão>.md` (o workflow usa esse arquivo como
   corpo da release).
3. Faça o commit na `main`.
4. Crie e envie a tag:

```bat
git tag -a v1.2 -m "ChaveiroCar 1.2"
git push origin v1.2
```

Em alguns minutos a release aparece em `releases` com o APK Android e o ZIP
Windows. Quem já tem o app instalado vê o aviso de atualização em até um dia
(ou na hora, em Ajustes → Verificar atualização).

Segredos usados pelo workflow (já configurados no repositório): `CHAVEIRO_KEYSTORE_BASE64`,
`CHAVEIRO_KEYSTORE_SENHA`, `CHAVEIRO_KEY_ALIAS`, `CHAVEIRO_KEY_SENHA`.

## Como configurar o login com Google (OAuth)

O app Android usa a API de autorização do Google Play Services apenas com os escopos de identidade (`email`, `profile`, `openid`), validando o token na API ChaveiroCar (`POST /api/v1/auth/google-login`).

1. Em https://console.cloud.google.com crie ou abra o projeto `ChaveiroCar`.
2. Configure a tela de consentimento OAuth com os escopos básicos (`email`, `profile`, `openid`).
3. Crie um **OAuth client ID** do tipo **Android**:
   - Pacote: `com.willendary.chaveirocar`
   - SHA-1 da keystore de release:
     `8C:6E:38:4F:90:D0:4D:D6:7B:E6:6F:9F:14:16:80:6D:A2:31:D5:DB`
   - Para testar build debug, crie outro cliente com o SHA-1 de debug:
     `76:3F:B1:F9:87:DF:C8:74:0D:F3:26:15:CD:DF:65:CA:13:B6:2C:80`
4. Na API, se quiser restringir os clientes aceitos, configure `GOOGLE_CLIENT_IDS` (e opcionalmente `GOOGLE_ONLY=true` durante o piloto).

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

1.3.1 — o app passa a exigir a conta Google na abertura: sem conectar,
a tela não abre.

1.3.2 — aviso de versão nova vira popup "Nova versão disponível" na
abertura, com botão "Atualizar agora".

1.3.3 — remove os botões manuais de backup em arquivo de Ajustes; todo
backup passa a ser automático na nuvem.

1.3.4 — lista sem cards duplicados (popup de atualização basta), detalhe
com Compartilhar/PDF no topo e cards de texto juntos, botão "Novo
atendimento (mesmo carro)", e "Última correção" no detalhe.

1.4.0 — nova identidade visual azul/jade, marca vetorial, temas claro e
escuro, telas renovadas para celular e tablet.

1.5.0 — consulta técnica por etapas com fichas offline, leitura de placa
pela câmera, catálogo de veículos, campo chip/transponder, múltiplos tipos
de serviço e seleção de várias fotos.

1.7.0 — login com Google no Android e Windows, escolha de segmento Automotivo
ou Residencial, fluxo completo de chaveiro residencial com fichário separado,
placa opcional (com modelo obrigatório) no automotivo e editor de fotos com
setas indicativas sincronizadas.

1.8.0 — conclusão do épico multiplataforma (#36): migração direta do histórico
local para a API + exportação/importação local em `.zip` com remoção definitiva
do legado Google Drive (#40), e novo aplicativo iOS para iPhone/iPad em SwiftUI
integrado à mesma API (#43).


## Visual 1.4.0

Nova identidade azul/jade, marca vetorial, temas claro e escuro e telas renovadas.
Atendimentos têm busca em destaque, filtros recolhíveis e rolagem única. O fichário
organiza os veículos com favoritos; o formulário separa veículo, serviço, cliente,
memória técnica e fotos. Os dados e o formato do banco são preservados.

A identidade vetorial está em `docs/design/marca.svg`. As capturas das telas reais
são geradas pelo workflow **Visual e navegacao**, no artefato `revisao-visual`,
com perfis de celular (360 dp) e tablet (800 dp), incluindo tema escuro.

### Validação e publicação

O workflow visual compila o aplicativo, executa os testes unitários e testa os
fluxos reais em emulador (busca, filtros, favoritos, edição e cópia de códigos).
Após esse workflow passar na `main`, **Android APK** gera o APK assinado e publica
a versão indicada no Gradle, usando `release/v<versão>.md` como notas. Uma versão
já publicada não é sobrescrita. Tags `v*` continuam aceitas para publicação manual.
