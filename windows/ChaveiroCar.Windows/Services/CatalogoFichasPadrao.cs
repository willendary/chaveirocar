using System.Globalization;
using System.Text;

namespace ChaveiroCar.Windows.Services;

/// <summary>
/// Catálogo padrão embutido de fichas técnicas e manuais offline (Veicular e Residencial).
/// Não vai para o outbox/sync nem sobrescreve anotações próprias da empresa.
/// </summary>
public static class CatalogoFichasPadrao
{
    public const string OrigemOficial = "CATALOGO_OFICIAL";

    private static readonly List<ArmazenamentoLocal.Ficha> Todas =
    [
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1001",
            Segmento = "AUTOMOTIVO",
            Equipamento = "OBDMap / Xhorse VVDI",
            Marca = "Volkswagen",
            Modelo = "Gol / Voyage / Saveiro (G5 / G6)",
            Ano = "2009-2016",
            Sistema = "Immo 4 · Painel VDO / Marelli",
            Descricao = "Linha VW G5 e G6 com painel imobilizador integrado (VDO 24C32/24C64 ou Marelli 9S12).",
            Chip = "ID48 (TP23 VW CAN)",
            Chave = "Lâmina HU66 · Lishi HU66",
            Telecomando = "3 botões VW 5K0 / KD VW G6",
            Frequencia = "433,92 MHz",
            Avisos = "Em painéis Marelli ou perda total (All Keys Lost) pode exigir leitura de memória/processador em bancada antes de gerar chave.",
            Funcoes = "Adicionar chave, programar telecomando (canal 01 adaptação), pré-codificar TP23.",
            Manual = "1. Identifique se o painel é VDO ou Magneti Marelli.\n2. Em adição com chave válida, faça pré-codificação do ID48 para TP23 VW e leia os dados de imobilizador via OBD.\n3. Em perda total, coloque em modo serviço lendo a EEPROM (24C32/24C64) ou MCU em bancada.\n4. Após gravar o transponder, programe o telecomando no módulo Conforto/Central (canal 01).",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1002",
            Segmento = "AUTOMOTIVO",
            Equipamento = "Xhorse VVDI2 / Autel IM508",
            Marca = "Volkswagen",
            Modelo = "Polo / Virtus / T-Cross / Nivus",
            Ano = "2018-2025",
            Sistema = "MQB / Immo 5",
            Descricao = "Plataforma MQB com painel digital ou analógico VDO/JCI e chave canivete ou presencial.",
            Chip = "MQB AES / ID48 / Hitag Pro",
            Chave = "Lâmina HU66 ou HU162T (8/9 cortes) · Lishi HU66 / HU162T",
            Telecomando = "MQB 3 botões (5G0 / 2G0)",
            Frequencia = "433,92 MHz",
            Avisos = "Perda total MQB exige arquivo de sincronização (sync data) e atenção para não corromper o cluster durante leitura.",
            Funcoes = "Leitura de immo data, geração de chave revendedora, aprendizado via OBD.",
            Manual = "1. Confira o perfil exato da lâmina (HU66 ou HU162T).\n2. Conecte bateria auxiliar estabilizada acima de 12,5V.\n3. Leia o Immo Data via OBD ou adaptador sem solda.\n4. Gere o transponder/chave revendedora com o CS obtido e conclua o aprendizado de todas as chaves na mesma sessão.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1003",
            Segmento = "AUTOMOTIVO",
            Equipamento = "OBDMap / Gamarra",
            Marca = "Fiat",
            Modelo = "Palio / Strada / Uno / Siena",
            Ano = "2001-2017",
            Sistema = "Fiat Code 2 (BC Delphi / Marelli)",
            Descricao = "Body Computer com EEPROM 93C66 ou processador 9S12 dependendo do ano e versão.",
            Chip = "ID48 Megamos (pré-codificado)",
            Chave = "Lâmina SIP22 · Lishi SIP22",
            Telecomando = "Telecomando integrado no BC (precodificação conjunta)",
            Frequencia = "433,92 MHz",
            Avisos = "Em Fiat Code 2 a chave precisa ser pré-codificada com os bytes do Body Computer antes de apresentar por diagnose; chaves não programadas na mesma sessão são bloqueadas.",
            Funcoes = "Extração de PIN code, pré-codificação do ID48 e programação de até 8 chaves.",
            Manual = "1. Leia a EEPROM 93C66 (Delphi) ou arquivo do BC (Marelli).\n2. Extraia o código eletrônico (PIN de 5 dígitos) e os dados de pré-codificação.\n3. Grave os dados no transponder ID48 virgem.\n4. Via tomada OBD, execute o aprendizado informando o PIN com todas as chaves do cliente em mãos.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1004",
            Segmento = "AUTOMOTIVO",
            Equipamento = "Autel IM508 / Keydiy KD-X2",
            Marca = "Fiat",
            Modelo = "Argo / Cronos / Pulse / Toro",
            Ano = "2018-2025",
            Sistema = "Fiat BSI Continental / Marelli (Hitag AES)",
            Descricao = "Sistemas modernos FCA com módulo SGW nos anos mais recentes.",
            Chip = "ID4A (Hitag AES PCF7939M)",
            Chave = "Lâmina SIP22 · Lishi SIP22",
            Telecomando = "Canivete 3 botões ou Smart Key 4 botões",
            Frequencia = "433,92 MHz FSK",
            Avisos = "Veículos 2020+ possuem Security Gateway (SGW): utilize cabo bypass direto no módulo para liberar gravação.",
            Funcoes = "Leitura de senha, pré-codificação e gravação de chave canivete ou presencial.",
            Manual = "1. Localize o conector SGW (atrás do porta-luvas ou coluna de direção) se o carro for 2020+.\n2. Conecte o cabo bypass SGW.\n3. Leia o PIN do BSI e faça o aprendizado via OBD.\n4. Em chave presencial, aproxime a chave da antena de emergência (console central ou botão Start).",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1005",
            Segmento = "AUTOMOTIVO",
            Equipamento = "OBDMap / Xhorse Key Tool Max",
            Marca = "Chevrolet",
            Modelo = "Onix / Prisma / Cobalt / Spin",
            Ano = "2013-2019",
            Sistema = "GM BCM (24C16 / 24C32)",
            Descricao = "Linha GM com Body Control Module (BCM) e chave canivete integrada.",
            Chip = "ID46 (PCF7937E / PCF7936)",
            Chave = "Lâmina HU100 · Lishi HU100",
            Telecomando = "Canivete GM 2 ou 3 botões (integrado ao transponder)",
            Frequencia = "433,92 MHz ASK",
            Avisos = "O telecomando grava automaticamente junto com o transponder quando usa placa original ou universal configurada para GM Brasil.",
            Funcoes = "Leitura de senha via OBD (ou pinça 24C16 no BCM) e gravação de chaves.",
            Manual = "1. Extraia o PIN de 4 dígitos via OBD ou diretamente na EEPROM 24C16 do BCM.\n2. Utilize chave canivete ID46 específica para GM.\n3. Inicie o procedimento de adição ou perda total (aguardar ciclo de 10 minutos se aplicável no modelo).\n4. Teste travas elétricas e partida.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1006",
            Segmento = "AUTOMOTIVO",
            Equipamento = "OBDMap / Autel IM508",
            Marca = "Ford",
            Modelo = "Ka / EcoSport / New Fiesta",
            Ano = "2014-2021",
            Sistema = "Ford PATS / BCM",
            Descricao = "Sistema PATS com incode/outcode automático ou calculado.",
            Chip = "ID63 (4D-63 80-bit) ou ID49 (Smart Key)",
            Chave = "Lâmina HU101 · Lishi HU101",
            Telecomando = "Integrado ou separado 3 botões",
            Frequencia = "433,92 MHz / 315 MHz (conferir etiqueta)",
            Avisos = "Em perda total são necessárias ao menos 2 chaves com chips diferentes para fechar o ciclo de programação PATS.",
            Funcoes = "Apagar chaves perdidas, adicionar chave e programar telecomando.",
            Manual = "1. Confira no aparelho se o sistema é chave lâmina (4D63 80-bit) ou presença (ID49).\n2. Em perda total, tenha 2 transponders distintos preparados.\n3. Para telecomando separado em modelos compatíveis: vire a ignição de 0 para II quatro vezes em 6 segundos até o bip e aperte um botão do controle.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1007",
            Segmento = "AUTOMOTIVO",
            Equipamento = "OBDMap / Keydiy KD-X2",
            Marca = "Renault",
            Modelo = "Sandero / Logan / Duster / Kwid",
            Ano = "2012-2024",
            Sistema = "UCH Continental / Valeo",
            Descricao = "Linha Renault nacional com UCH e transponder PCF7936/PCF7946 ou AES (Kwid/novos).",
            Chip = "ID46 (PCF7936 / PCF7946) ou ID4A (Kwid/novos)",
            Chave = "Lâmina NE72 / VA2 / HU136 · Lishi VA2 / NE72",
            Telecomando = "2 botões (integrado PCF7946/7961)",
            Frequencia = "433,92 MHz",
            Avisos = "Em UCH novos há risco de bloqueio temporário por tentativas repetidas de senha; mantenha a bateria acima de 12,4V.",
            Funcoes = "Leitura de código ISK/PIN via OBD e gravação de chaves.",
            Manual = "1. Identifique se é chave seca com transponder separado (PCF7936) ou telecomando integrado (PCF7946/7961).\n2. Leia a senha via OBD.\n3. Execute a programação apresentando todas as chaves na ignição conforme instruído na tela.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1008",
            Segmento = "AUTOMOTIVO",
            Equipamento = "Xhorse VVDI / Keydiy KD-X2",
            Marca = "Hyundai",
            Modelo = "HB20 / HB20S / Creta",
            Ano = "2013-2024",
            Sistema = "Immo Kefico / Smartra / Smart Key",
            Descricao = "Modelos nacionais Hyundai com leitura de PIN por OBD ou chassi.",
            Chip = "ID46 (PCF7936) / ID47 / ID4A (versões mais novas)",
            Chave = "Lâmina TOY49 / HY22 / HYN14R · Lishi HYN14R / HY22",
            Telecomando = "Canivete 3 botões ou presença",
            Frequencia = "433,92 MHz FSK",
            Avisos = "Diferenciar HB20 com chave simples/canivete (ID46/47) de versões com partida por botão (Smart Key ID47/4A).",
            Funcoes = "Leitura de PIN, programação de transponder e telecomando.",
            Manual = "1. Confira o perfil da lâmina (corte lateral ou pantográfica HY22).\n2. Leia o PIN de 6 dígitos via OBD ou tabela pelo chassi/ECU.\n3. Programe primeiro o transponder via OBD e em seguida o telecomando na função específica.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1009",
            Segmento = "AUTOMOTIVO",
            Equipamento = "Autel IM508 / Xhorse VVDI",
            Marca = "Toyota",
            Modelo = "Corolla / Hilux / Etios",
            Ano = "2009-2023",
            Sistema = "Toyota G / H / Smart Key",
            Descricao = "Sistemas Toyota com reset de imobilizador ou adição direta.",
            Chip = "4D67 / 4D72 (G) / 8A (H) / Smart 8A",
            Chave = "Lâmina TOY43 / TOY48 · Lishi TOY43 / TOY48",
            Telecomando = "2 ou 3 botões (on-board em alguns modelos)",
            Frequencia = "433,92 MHz / 314 MHz",
            Avisos = "Chips G e H exigem identificação exata pela letra estampada na lâmina ou leitura em clonadora antes da programação.",
            Funcoes = "Reset de imobilizador, adição de chave mestre, sincronização de telecomando.",
            Manual = "1. Leia a chave original (se houver) para confirmar se é 4D67, G ou H (8A).\n2. Use função Add Key quando há chave mestre.\n3. Em perda total em modelos com imobilizador separado, realize o reset com jumper/OBD ou leitura da caixa imobilizadora.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-1010",
            Segmento = "AUTOMOTIVO",
            Equipamento = "Xhorse VVDI / OBDMap",
            Marca = "Honda",
            Modelo = "Civic / Fit / HR-V / City",
            Ano = "2007-2022",
            Sistema = "Honda Immo / Smart Entry",
            Descricao = "Sistema Honda com adição direta de chaves sem exigir PIN externo na maioria dos modelos.",
            Chip = "ID46 (PCF7936) / ID47 (Hitag 3)",
            Chave = "Lâmina HON66 · Lishi HON66",
            Telecomando = "2 ou 3 botões (grava junto em modelos modernos)",
            Frequencia = "433,92 MHz",
            Avisos = "No procedimento Honda All Keys Lost siga exatamente o tempo de liga/desliga da ignição exibido pelo scanner.",
            Funcoes = "Adicionar chave, apagar chaves perdidas, programar controle remoto.",
            Manual = "1. Confirme se a lâmina é HON66.\n2. Em adição/perda total via OBD, siga o ciclo de ignição ON/OFF solicitado pelo programador.\n3. Em modelos com chip separado, sincronize o telecomando após o chip reconhecido no painel.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-2001",
            Segmento = "RESIDENCIAL",
            Equipamento = "Manual Fabricante",
            Marca = "Intelbras",
            Modelo = "FR 101 / FR 201 / FR 220",
            Ano = "Linha FR",
            Sistema = "Fechadura Digital (Senha + Tag RFID 13,56 MHz)",
            Descricao = "Fechaduras digitais de sobrepor com teclado touch e suporte a chaveiros RFID.",
            Chip = "Tag RFID Mifare 13,56 MHz",
            Chave = "Sem chave mecânica (alimentação externa 9V em emergência)",
            Telecomando = "Tag adesiva / chaveiro de proximidade",
            Frequencia = "13,56 MHz",
            Avisos = "Nunca deixe a porta fechar durante o cadastro de senha mestre; mantenha as pilhas alcalinas AA novas (não recarregáveis).",
            Funcoes = "Alterar senha mestre, cadastrar até 100 tags RFID, ativar travamento automático e reset de fábrica.",
            Manual = "1. Abra a tampa de pilhas e pressione o botão interno de cadastro (REG/PROG).\n2. Digite a senha mestre atual seguida de # (padrão de fábrica costuma ser 1234).\n3. Para cadastrar Tag RFID: aproxime os chaveiros em sequência e finalize pressionando o botão interno novamente.\n4. Em bateria esgotada, encoste uma bateria 9V nos polos frontais para despertar o teclado.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-2002",
            Segmento = "RESIDENCIAL",
            Equipamento = "Manual Fabricante",
            Marca = "Yale",
            Modelo = "YDF 40 / YRD 226 / YRD 256",
            Ano = "Linha Digital",
            Sistema = "Fechadura Digital de Embutir / Sobrepor",
            Descricao = "Fechaduras digitais com menu guiado por voz e cadastro por código mestre.",
            Chip = "Cartão / Tag RFID (modelos compatíveis)",
            Chave = "YRD/YDF conforme modelo (modelos com cilindro usam perfil Yale)",
            Telecomando = "Módulo Zigbee/Bluetooth opcional",
            Frequencia = "13,56 MHz (tags)",
            Avisos = "Ao resetar para fábrica é obrigatório criar uma nova Senha Mestre imediatamente para liberar as demais funções.",
            Funcoes = "Definir senha mestre, incluir/excluir usuários, ajustar volume e travamento automático.",
            Manual = "1. Toque no teclado com a palma da mão para ativar.\n2. Digite a Senha Mestre seguida da tecla * ou engrenagem.\n3. Escolha a opção 1 para gerenciar usuários (senhas, biometrias ou cartões conforme modelo).\n4. Siga a voz orientadora e finalize pressionando *.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-2003",
            Segmento = "RESIDENCIAL",
            Equipamento = "Catálogo Gold / Jas / Silca",
            Marca = "Papaiz / Stam / Pado",
            Modelo = "Travas Tetra e Cilindros Multiponto",
            Ano = "Residencial",
            Sistema = "Cilindro Tetra (4 lados) e Multiponto",
            Descricao = "Referência de conversão de brutos e procedimentos de troca de segredo/abertura.",
            Chip = "Mecânica (sem chip)",
            Chave = "Stam Tetra: Gold 1044 / Jas 387 · Papaiz Tetra: Gold 1030 · Multiponto conforme gabarito",
            Telecomando = "Não se aplica",
            Frequencia = "Mecânica",
            Avisos = "Em chaves tetra, confira o sentido dos 4 entalhes e a espessura da haste antes de copiar na máquina.",
            Funcoes = "Abertura com micha tetra / multiponto, confecção de chave pelo cilindro e troca de segredo.",
            Manual = "1. Identifique a marca e o perfil do bruto no catálogo (Gold/Jas/Land/Silca).\n2. Para trava tetra com chave perdida, verifique os 4 lados do cilindro e alinhe os pinos com micha tetra específica.\n3. Na troca de segredo de cilindro euro-perfil ou tetra, organize os pinos e contrapinos na bandeja na ordem exata de desmontagem.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
        new ArmazenamentoLocal.Ficha
        {
            Uuid = "catalogo-oficial-2004",
            Segmento = "RESIDENCIAL",
            Equipamento = "Manual Centrais de Portão",
            Marca = "PPA / Rossi / Garen",
            Modelo = "Centrais Pop / Facility / NKX / Fit",
            Ano = "Automatizadores",
            Sistema = "Receptor RF 433,92 MHz (Code Learning / Rolling Code)",
            Descricao = "Guia rápido de gravação e apagamento de controles remotos em placas de portão.",
            Chip = "HCS Rolling Code / Code Learning HT6P20B",
            Chave = "Controle TX 433,92 MHz",
            Telecomando = "PPA/Garen (Code Learning/HCS) · Rossi (Rolling Code próprio)",
            Frequencia = "433,92 MHz",
            Avisos = "Placas Rossi utilizam protocolo Rolling Code próprio (não aceitam controles Code Learning comuns). Antes de apagar toda a memória confirme com o condomínio/morador.",
            Funcoes = "Gravar controle TX, apagar memória de controles e conferir LED de programação.",
            Manual = "PPA (Pop/Facility):\n1. Pressione e solte o botão LEARN/PROG na placa (LED acende).\n2. Aperte o botão do controle remoto (LED pisca confirmando).\n\nGaren:\n1. Pressione o botão TX na central.\n2. Aperte o botão do controle desejado até o LED piscar rápido.\n\nRossi (NKX/DZ):\n1. Pressione e solte o botão APRENDE/PROG na central.\n2. Aperte o botão do controle Rossi e aguarde o LED confirmar/apagar.",
            Confirmado = true,
            Origem = OrigemOficial,
        },
    ];

    public static IReadOnlyList<ArmazenamentoLocal.Ficha> Listar(string? segmento = null, string? busca = null)
    {
        var tokens = Normalizar(busca).Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return Todas.Where(f =>
        {
            if (!string.IsNullOrWhiteSpace(segmento) &&
                !string.Equals(f.Segmento, segmento, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }
            if (tokens.Length == 0) return true;
            var texto = Normalizar(string.Join(" ",
                f.Equipamento, f.Marca, f.Modelo, f.Ano, f.Sistema,
                f.Descricao, f.Chip, f.Chave, f.Telecomando, f.Frequencia,
                f.Avisos, f.Funcoes, f.Manual));
            return tokens.All(t => texto.Contains(t, StringComparison.Ordinal));
        }).ToList();
    }

    public static List<ArmazenamentoLocal.Ficha> BuscarParaAtendimento(
        Atendimento atendimento,
        IEnumerable<ArmazenamentoLocal.Ficha> daEmpresa)
    {
        var segmento = string.IsNullOrWhiteSpace(atendimento.Segmento) ? "AUTOMOTIVO" : atendimento.Segmento;
        var marca = segmento == "RESIDENCIAL" ? atendimento.MarcaFechadura : atendimento.Marca;
        var modelo = segmento == "RESIDENCIAL" ? atendimento.ModeloFechadura : atendimento.Modelo;
        var marcaNorm = Normalizar(marca);
        var modeloNorm = Normalizar(modelo);
        if (string.IsNullOrWhiteSpace(marcaNorm) && string.IsNullOrWhiteSpace(modeloNorm))
        {
            return [];
        }

        bool Combina(ArmazenamentoLocal.Ficha f)
        {
            if (!string.Equals(f.Segmento, segmento, StringComparison.OrdinalIgnoreCase)) return false;
            var fMarca = Normalizar(f.Marca);
            var fModelo = Normalizar(f.Modelo);
            var bateMarca = string.IsNullOrWhiteSpace(marcaNorm) ||
                            fMarca.Contains(marcaNorm, StringComparison.Ordinal) ||
                            marcaNorm.Contains(fMarca, StringComparison.Ordinal);
            var partesModelo = modeloNorm.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            var bateModelo = string.IsNullOrWhiteSpace(modeloNorm) ||
                             fModelo.Contains(modeloNorm, StringComparison.Ordinal) ||
                             partesModelo.Any(p => p.Length >= 3 && fModelo.Contains(p, StringComparison.Ordinal));
            return bateMarca && bateModelo;
        }

        return daEmpresa.Where(Combina).Concat(Todas.Where(Combina)).ToList();
    }

    private static string Normalizar(string? valor)
    {
        if (string.IsNullOrWhiteSpace(valor)) return "";
        var decompos = valor.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(decompos.Length);
        foreach (var ch in decompos)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(ch) != UnicodeCategory.NonSpacingMark)
            {
                sb.Append(char.ToLowerInvariant(ch));
            }
        }
        return sb.ToString().Normalize(NormalizationForm.FormC).Trim();
    }
}
