import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resetarOrcamentoPlacasWeb } from "../src/servicos/placasWeb.js";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

function htmlVeiculo(placa: string, marca: string, modelo: string, ano: string) {
  return `<!doctype html><html><body>
    <h1>Consulta ${placa}</h1>
    <table>
      <tr><th>Marca</th><td>${marca}</td></tr>
      <tr><th>Modelo</th><td>${modelo}</td></tr>
      <tr><th>Ano Modelo</th><td>${ano}</td></tr>
      <tr><th>Chassi</th><td>*****P246344</td></tr>
    </table>
  </body></html>`;
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

describe("consulta de placas por leitura direta de páginas HTML", () => {
  it("consulta CarroFIPE por padrão sem API Parallelum e reaproveita cache no Turso", async () => {
    const requisicao = vi.fn(async (url: unknown) => {
      expect(String(url)).not.toContain("parallelum.com.br");
      return new Response(htmlVeiculo("ABC1234", "Volkswagen", "SANTANA CG", "1986"), {
        status: 200,
        headers: { "content-type": "text/html; charset=utf-8" },
      });
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
    expect(requisicao.mock.calls[0]?.[0]).toBe("https://www.carrofipe.com.br/consultar?placa=ABC1234");
  });

  it("retorna HTTP 404 quando os sites respondem sem localizar a placa", async () => {
    const requisicao = vi.fn(async () => new Response("<html><body>Não encontrada</body></html>", {
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

  it("bloqueia chamadas não autenticadas", async () => {
    const req = await rota();
    const resultado = await ambiente.app.inject({ method: "GET", url: req.url });
    expect(resultado.statusCode).toBe(401);
  });

  it("aceita também placa Mercosul", async () => {
    const requisicao = vi.fn(async () => new Response(htmlVeiculo("ABC1D23", "Fiat", "Argo", "2022"), {
      status: 200,
      headers: { "content-type": "text/html; charset=utf-8" },
    }));
    vi.stubGlobal("fetch", requisicao);
    const req = await rota("ABC1D23");
    const resultado = await ambiente.app.inject({ method: "GET", ...req });
    expect(resultado.statusCode).toBe(200);
    expect(resultado.json()).toEqual({ modelo: "Argo", marca: "Fiat", ano: "2022" });
  });
});
