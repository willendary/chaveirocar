import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

beforeEach(async () => {
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

describe("consulta gratuita de placas via Parallelum", () => {
  it("consulta sem chave, converte campos e reaproveita cache no Turso", async () => {
    const requisicao = vi.fn(async () => new Response(JSON.stringify({
      brand: "Volkswagen", model: "SANTANA CG", year: "1986",
      chassis: "*****P246344", city: "LOBATO",
    }), { status: 200 }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();

    const primeira = await ambiente.app.inject({ method: "GET", ...req });
    expect(primeira.statusCode).toBe(200);
    expect(primeira.json()).toEqual({ modelo: "SANTANA CG", marca: "Volkswagen", ano: "1986" });
    expect(primeira.body).not.toContain("chassis");
    expect(primeira.body).not.toContain("city");

    const segunda = await ambiente.app.inject({ method: "GET", ...req });
    expect(segunda.statusCode).toBe(200);
    expect(segunda.json()).toEqual(primeira.json());
    expect(requisicao).toHaveBeenCalledTimes(1);
    expect(requisicao.mock.calls[0]?.[0]).toBe("https://fipe.parallelum.com.br/api/v2/plates/ABC1234");
  });

  it.each([
    [404, 404, "Placa não localizada"],
    [429, 429, "Cota gratuita mensal"],
    [503, 503, "Consulta por placa indisponível"],
  ])("traduz HTTP %i do provedor em HTTP %i", async (upstream, esperado, mensagem) => {
    const requisicao = vi.fn(async () => new Response("{}", { status: upstream }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(esperado);
    expect(resultado.json().erro.mensagem).toContain(mensagem);
  });

  it("trata resposta JSON inválida sem erro 500", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("não é JSON", { status: 200 })));
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(503);
  });

  it("bloqueia chamadas não autenticadas", async () => {
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", url: req.url });
    expect(resultado.statusCode).toBe(401);
  });

  it("aceita também placa Mercosul", async () => {
    const requisicao = vi.fn(async () => new Response(JSON.stringify({
      brand: "Fiat", model: "Argo", year: 2022,
    }), { status: 200 }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota("ABC1D23");
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(200);
    expect(resultado.json()).toEqual({ modelo: "Argo", marca: "Fiat", ano: "2022" });
  });
});
