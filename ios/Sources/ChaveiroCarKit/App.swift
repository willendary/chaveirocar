import SwiftUI
import ChaveiroCarKit

@main
struct ChaveiroCarApp: App {
    var body: some Scene {
        WindowGroup { ListaAtendimentos() }
    }
}

struct ListaAtendimentos: View {
    @State private var itens: [[String: Any]] = []
    @State private var busca = ""
    @State private var erro = ""

    var body: some View {
        NavigationStack {
            VStack {
                HStack {
                    TextField("Placa ou cliente", text: $busca)
                        .textFieldStyle(.roundedBorder)
                    Button("Pesquisar") { Task { await carregar() } }
                }.padding()
                if !erro.isEmpty { Text(erro).foregroundStyle(.red) }
                List(itens, id: \.self) { item in
                    Text(String(describing: item["placa"] ?? ""))
                }
            }.navigationTitle("Atendimentos")
        }
    }

    func carregar() async {
        // Login e empresa fixa entram na próxima iteração.
        do {
            let api = API()
            let resultado = try await api.atendimentos(empresaId: "00000000-0000-0000-0000-000000000000", busca: busca)
            itens = resultado["itens"] as? [[String: Any]] ?? []
        } catch {
            erro = "Não foi possível carregar. Verifique a conexão."
        }
    }
}
