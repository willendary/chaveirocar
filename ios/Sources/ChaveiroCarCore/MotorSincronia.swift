import Foundation

public struct ResumoSincronia: Equatable, Sendable {
    public let enviadas: Int
    public let recebidas: Int
    public let conflitos: Int
    public let cursor: Int64

    public init(enviadas: Int, recebidas: Int, conflitos: Int, cursor: Int64) {
        self.enviadas = enviadas
        self.recebidas = recebidas
        self.conflitos = conflitos
        self.cursor = cursor
    }
}

public enum MotorSincronia {
    public static func sincronizar(
        armazenamento: ArmazenamentoLocal,
        api: ClienteApi,
        empresaId: String
    ) async throws -> ResumoSincronia {
        var enviadas = 0
        var conflitosNovos = 0

        if !armazenamento.fila.isEmpty {
            var operacoesPayload: [[String: Any]] = []
            for op in armazenamento.fila {
                var item: [String: Any] = [
                    "opId": op.opId,
                    "entidade": op.entidade,
                    "id": op.idEntidade,
                    "tipo": op.tipo,
                    "baseRevisao": op.baseRevisao,
                ]
                if op.tipo == "salvar" {
                    if op.entidade == "atendimento", let atd = armazenamento.obterAtendimento(op.idEntidade) {
                        item["dados"] = ClienteApi.dicionarioDeAtendimento(atd)
                    } else if op.entidade == "ficha",
                              let ficha = armazenamento.listarFichas().first(where: { $0.uuid == op.idEntidade }) {
                        item["dados"] = ClienteApi.dicionarioDeFicha(ficha)
                    }
                }
                operacoesPayload.append(item)
            }

            let respostaPush = try await api.enviarLoteSync(empresaId: empresaId, operacoes: operacoesPayload)
            for res in respostaPush.resultados {
                guard let op = armazenamento.fila.first(where: { $0.opId == res.opId }) else { continue }
                if res.status == "aplicada" || res.status == "ignorada_duplicada" {
                    armazenamento.marcarOperacaoConcluida(opId: res.opId, novaRevisao: res.revisao)
                    enviadas += 1
                } else if res.status == "conflito" {
                    let ident = armazenamento.obterAtendimento(op.idEntidade)?.identificacaoPrincipal ?? op.idEntidade
                    armazenamento.registrarConflito(
                        ConflitoSinc(
                            idEntidade: op.idEntidade,
                            entidade: op.entidade,
                            identificacao: ident,
                            revisaoServidor: res.revisao,
                            snapshotServidorJson: res.registroJson ?? ""
                        ),
                        opId: res.opId
                    )
                    conflitosNovos += 1
                }
            }
        }

        // Envia fotos pendentes cujos atendimentos já possuem revisão remota > 0
        for foto in armazenamento.fotos where foto.pendenteUpload && !foto.excluido {
            guard let atd = armazenamento.obterAtendimento(foto.atendimentoUuid),
                  atd.revisao > 0,
                  let bytes = try? Data(contentsOf: URL(fileURLWithPath: foto.arquivoLocal)) else { continue }
            if let revisaoFoto = try? await api.enviarFoto(
                empresaId: empresaId,
                atendimentoUuid: foto.atendimentoUuid,
                foto: foto,
                bytes: bytes
            ) {
                var atualizada = foto
                atualizada.revisao = revisaoFoto
                atualizada.pendenteUpload = false
                armazenamento.aplicarFotoServidor(atualizada, bytes: nil)
                enviadas += 1
            }
        }

        // Pull incremental
        var cursorAtual = armazenamento.cursor
        var recebidas = 0
        for _ in 0..<20 {
            let lote = try await api.puxarSync(empresaId: empresaId, cursor: cursorAtual)
            let pendentesIds = Set(armazenamento.fila.map { "\($0.entidade):\($0.idEntidade)" })
            let conflitosIds = Set(armazenamento.conflitos.map { "\($0.entidade):\($0.idEntidade)" })

            for atd in lote.atendimentos {
                let chave = "atendimento:\(atd.uuid)"
                if pendentesIds.contains(chave) || conflitosIds.contains(chave) { continue }
                armazenamento.aplicarAtendimentoServidor(atd)
                recebidas += 1
            }

            for ficha in lote.fichas {
                let chave = "ficha:\(ficha.uuid)"
                if pendentesIds.contains(chave) || conflitosIds.contains(chave) { continue }
                armazenamento.aplicarFichaServidor(ficha)
                recebidas += 1
            }

            for remota in lote.fotos {
                let existente = armazenamento.fotos.first { $0.uuid == remota.uuid }
                if existente?.pendenteUpload == true { continue }
                var bytesBaixados: Data? = nil
                let precisaBaixar = !remota.excluido && !remota.url.isEmpty && (
                    existente == nil ||
                    !FileManager.default.fileExists(atPath: existente!.arquivoLocal) ||
                    remota.revisao > existente!.revisao
                )
                if precisaBaixar {
                    bytesBaixados = try? await api.baixarFoto(urlRelativa: remota.url)
                }
                let fotoLocal = FotoAtendimento(
                    uuid: remota.uuid,
                    atendimentoUuid: remota.atendimentoUuid,
                    fichaUuid: remota.fichaUuid,
                    arquivoLocal: existente?.arquivoLocal ?? "",
                    categoria: remota.categoria,
                    legenda: remota.legenda,
                    anotacoes: remota.anotacoes,
                    ordem: remota.ordem,
                    revisao: remota.revisao,
                    pendenteUpload: false,
                    excluido: remota.excluido
                )
                armazenamento.aplicarFotoServidor(fotoLocal, bytes: bytesBaixados)
            }

            cursorAtual = lote.cursor
            armazenamento.cursor = cursorAtual
            if !lote.temMais { break }
        }

        return ResumoSincronia(
            enviadas: enviadas,
            recebidas: recebidas,
            conflitos: armazenamento.conflitos.count + conflitosNovos,
            cursor: cursorAtual
        )
    }

    public static func manterLocal(
        armazenamento: ArmazenamentoLocal,
        api: ClienteApi,
        empresaId: String,
        conflito: ConflitoSinc
    ) async throws {
        armazenamento.removerConflito(entidade: conflito.entidade, idEntidade: conflito.idEntidade)
        if conflito.entidade == "atendimento", var local = armazenamento.obterAtendimento(conflito.idEntidade) {
            local.revisao = conflito.revisaoServidor
            armazenamento.aplicarAtendimentoServidor(local)
            try armazenamento.salvarAtendimentoLocal(local)
        }
        _ = try await sincronizar(armazenamento: armazenamento, api: api, empresaId: empresaId)
    }

    public static func aceitarServidor(
        armazenamento: ArmazenamentoLocal,
        conflito: ConflitoSinc
    ) {
        if conflito.entidade == "atendimento",
           let dados = conflito.snapshotServidorJson.data(using: .utf8),
           let dict = try? JSONSerialization.jsonObject(with: dados) as? [String: Any] {
            let remoto = ClienteApi.atendimentoDeDicionario(dict)
            armazenamento.aplicarAtendimentoServidor(remoto)
        }
        armazenamento.removerConflito(entidade: conflito.entidade, idEntidade: conflito.idEntidade)
    }
}
