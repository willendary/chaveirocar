import XCTest
@testable import ChaveiroCarCore

final class ChaveiroCarCoreTests: XCTestCase {
    private var pastaTemp: URL!

    override func setUpWithError() throws {
        pastaTemp = FileManager.default.temporaryDirectory
            .appendingPathComponent("chaveirocar-ios-tests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: pastaTemp, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let pastaTemp {
            try? FileManager.default.removeItem(at: pastaTemp)
        }
    }

    func testValidacaoAutomotivaPlacaOpcionalEModeloObrigatorio() throws {
        let semModelo = Atendimento(segmento: .automotivo, placa: "", marca: "Fiat", modelo: "")
        XCTAssertThrowsError(try semModelo.validar())

        let semPlacaComModelo = Atendimento(segmento: .automotivo, placa: "", marca: "Fiat", modelo: "Strada")
        XCTAssertNoThrow(try semPlacaComModelo.validar())
        XCTAssertEqual(semPlacaComModelo.identificacaoPrincipal, "Fiat Strada")

        let comPlacaValida = Atendimento(segmento: .automotivo, placa: "ABC1D23", marca: "VW", modelo: "Polo")
        XCTAssertNoThrow(try comPlacaValida.validar())
        XCTAssertEqual(comPlacaValida.identificacaoPrincipal, "ABC1D23")

        let comPlacaInvalida = Atendimento(segmento: .automotivo, placa: "123", marca: "VW", modelo: "Polo")
        XCTAssertThrowsError(try comPlacaInvalida.validar())
    }

    func testValidacaoResidencialSemDadosDeVeiculo() throws {
        let vazio = Atendimento(segmento: .residencial)
        XCTAssertThrowsError(try vazio.validar())

        let residencialValido = Atendimento(
            segmento: .residencial,
            tipoLocal: "Apartamento",
            ambientePorta: "Porta social",
            marcaFechadura: "Papaiz",
            modeloFechadura: "Multiponto",
            tipoCilindro: "Euro 70mm",
            tiposServico: [TipoServico.trocaCilindro.rawValue]
        )
        XCTAssertNoThrow(try residencialValido.validar())
        XCTAssertEqual(residencialValido.identificacaoPrincipal, "Apartamento · Porta social")
        XCTAssertEqual(residencialValido.subtituloPrincipal, "Papaiz · Multiponto · Euro 70mm")
    }

    func testSerializacaoAnotacoesComSetasEPreservacaoDoOriginal() throws {
        let setas = [
            SetaAnotacao(inicioX: 0.15, inicioY: 0.25, fimX: 0.75, fimY: 0.80, cor: "#FF3B30", espessura: 8)
        ]
        let json = SetaAnotacao.serializar(setas)
        let lidas = SetaAnotacao.ler(json)
        XCTAssertEqual(lidas.count, 1)
        XCTAssertEqual(lidas[0].inicioX, 0.15, accuracy: 0.0001)
        XCTAssertEqual(lidas[0].fimY, 0.80, accuracy: 0.0001)
        XCTAssertEqual(lidas[0].cor, "#FF3B30")

        let store = ArmazenamentoLocal(pastaBase: pastaTemp)
        let originalBytes = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x01, 0x02])
        let anotadaBytes = Data([0xFF, 0xD8, 0xFF, 0xE0, 0x09, 0x09])
        let foto = store.salvarFotoLocal(
            uuid: "foto-1",
            atendimentoUuid: "atd-1",
            dadosImagem: anotadaBytes,
            dadosOriginal: originalBytes,
            anotacoes: json
        )
        XCTAssertTrue(foto.temAnotacoes)
        XCTAssertEqual(store.carregarOriginalDaFoto(foto), originalBytes)
    }

    func testCacheOfflineFiltroPorSegmentoEFicharioSeparado() throws {
        let store = ArmazenamentoLocal(pastaBase: pastaTemp)
        try store.salvarAtendimentoLocal(Atendimento(
            uuid: "auto-1",
            segmento: .automotivo,
            placa: "",
            marca: "Chevrolet",
            modelo: "Onix",
            ano: "2023",
            abertura: "Lishi HU100",
            codigoChave: "M123",
            chipTransponder: "ID4A"
        ))
        try store.salvarAtendimentoLocal(Atendimento(
            uuid: "res-1",
            segmento: .residencial,
            tipoLocal: "Casa",
            ambientePorta: "Portão lateral",
            marcaFechadura: "Stam",
            modeloFechadura: "803",
            tipoCilindro: "Pino duplo",
            abertura: "Micha",
            codigoChave: "S55"
        ))

        XCTAssertEqual(store.listarAtendimentos().count, 2)
        XCTAssertEqual(store.listarAtendimentos(segmento: .automotivo).count, 1)
        XCTAssertEqual(store.listarAtendimentos(segmento: .residencial).count, 1)

        let veiculos = store.ficharioVeiculos()
        XCTAssertEqual(veiculos.count, 1)
        XCTAssertEqual(veiculos[0].modelo, "Onix")
        XCTAssertEqual(veiculos[0].chipTransponder, "ID4A")

        let residenciais = store.ficharioResidencial()
        XCTAssertEqual(residenciais.count, 1)
        XCTAssertEqual(residenciais[0].marcaFechadura, "Stam")
        XCTAssertEqual(residenciais[0].tipoCilindro, "Pino duplo")

        // Tratamento de conflito (aceitar servidor)
        let conflito = ConflitoSinc(
            idEntidade: "auto-1",
            entidade: "atendimento",
            identificacao: "Chevrolet Onix",
            revisaoServidor: 4,
            snapshotServidorJson: """
            {"id":"auto-1","segmento":"AUTOMOTIVO","placa":"BRA2E19","marca":"Chevrolet","modelo":"Onix Plus","revisao":4}
            """
        )
        store.registrarConflito(conflito, opId: store.fila.first!.opId)
        XCTAssertEqual(store.conflitos.count, 1)

        MotorSincronia.aceitarServidor(armazenamento: store, conflito: conflito)
        XCTAssertEqual(store.conflitos.count, 0)
        XCTAssertEqual(store.obterAtendimento("auto-1")?.modelo, "Onix Plus")
        XCTAssertEqual(store.obterAtendimento("auto-1")?.revisao, 4)
    }
}
