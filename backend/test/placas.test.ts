import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetarOrcamentoPlacasWeb } from "../src/servicos/placasWeb.js";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

function previaVeiculo(placa: string, marca: string, modelo: string, ano: string) {
  return { preview: { placa, marca, modelo, ano, simulated: false, chassi: "*****P246344" } };
}

beforeEach(async () => {
  resetarOrcamentoPlacasWeb();
  ambiente = await criarAmbiente();
});

afterEach(async () => {
  vi.unstubAllGlobals();
  await ambiente.fechar();
});

async function rota(placa = "ABC1234") {
  const conta = await cadastrar(ambiente.app);
  return {
    url: `/api/v1/empresas/${conta.empresas[0]!.id}/veiculos/${placa}`,
    headers: conta.cabecalho,
  };
}

describe("consulta de placas pela prévia gratuita e fontes HTML", () => {
  it("consulta CarroFIPE por padrão sem API Parallelum e reaproveita cache no Turso", async () => {
    const requisicao = vi.fn(async (url: unknown) => {
      expect(String(url)).not.toContain("parallelum.com.br");
      return Response.json(previaVeiculo("ABC1234", "Volkswagen", "SANTANA CG", "1986"));
    });
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();

    const primeira = await ambiente.app.inject({ method: "GET", ...req });
    expect(primeira.statusCode).toBe(200);
    expect(primeira.json()).toEqual({ modelo: "SANTANA CG", marca: "Volkswagen", ano: "1986" });
    expect(primeira.body).not.toContain("P246344");

    const segunda = await ambiente.app.inject({ method: "GET", ...req });
    expect(segunda.statusCode).toBe(200);
    expect(segunda.json()).toEqual(primeira.json());
    expect(requisicao).toHaveBeenCalledTimes(1);
    expect(requisicao.mock.calls[0]?.[0]).toBe("https://www.carrofipe.com.br/api/consultar");
  });

  it("retorna HTTP 404 quando os sites respondem sem localizar a placa", async () => {
    const requisicao = vi.fn(async (url: unknown) => String(url).includes("/api/consultar")
      ? Response.json({ preview: { simulated: false, note: "Placa não localizada" } })
      : new Response("<html><body>ABC1234: Placa não encontrada</body></html>", {
        status: 404,
        headers: { "content-type": "text/html; charset=utf-8" },
      }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(404);
    expect(resultado.json().erro.mensagem).toContain("Placa não localizada");
    expect(requisicao.mock.calls.some((args) => String(args[0]).includes("parallelum.com.br"))).toBe(false);
  });

  it("retorna HTTP 503 quando todas as fontes bloqueiam (403/429) sem chamar Parallelum", async () => {
    const requisicao = vi.fn(async () => new Response("Bloqueado", { status: 403 }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(503);
    expect(resultado.json().erro.mensagem).toContain("Consulta por placa indisponível");
    expect(requisicao.mock.calls.some((args) => String(args[0]).includes("parallelum.com.br"))).toBe(false);
  });

  it("trata resposta não-HTML sem erro 500", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("não é HTML", {
      status: 200,
      headers: { "content-type": "text/plain" },
    })));
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(503);
  });

  it("não transforma formulário vazio e rota HTTP 404 em placa não encontrada", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: unknown) => new Response(
      String(url).includes("/api/consultar") ? "<html>Consultar placa</html>" : "<html>Page not found</html>",
      { status: String(url).includes("/api/consultar") ? 200 : 404, headers: { "content-type": "text/html" } },
    )));
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(503);
    expect(resultado.json().erro.codigo).toBe("INDISPONIVEL");
  });

  it("não guarda falhas no cache e permite que a próxima consulta encontre o veículo", async () => {
    const requisicao = vi.fn(async () => new Response("Indisponível", { status: 503 }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();
    expect((await ambiente.app.inject({ method: "GET", ...req })).statusCode).toBe(503);
    requisicao.mockImplementation(async () => Response.json(previaVeiculo("ABC1234", "Fiat", "Uno", "2010")));
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(200);
    expect(resultado.json()).toEqual({ marca: "Fiat", modelo: "Uno", ano: "2010" });
  });

  it("bloqueia chamadas não autenticadas", async () => {
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", url: req.url });
    expect(resultado.statusCode).toBe(401);
  });

  it("aceita também placa Mercosul", async () => {
    const requisicao = vi.fn(async () => Response.json(previaVeiculo("ABC1D23", "Fiat", "Argo", "2022")));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota("ABC1D23");
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(200);
    expect(resultado.json()).toEqual({ modelo: "Argo", marca: "Fiat", ano: "2022" });
  });
});
