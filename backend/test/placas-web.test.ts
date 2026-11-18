import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { extrairVeiculoDeHtml } from "../src/servicos/placasWeb.js";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

const placa = "ITR1J02";
const exemplo = `<!doctype html><html><body>
  <section><h1>Consulta da placa ITR1J02</h1>
  <table>
    <tr><th>Marca</th><td>Volkswagen</td></tr>
    <tr><th>Modelo</th><td>Gol 1.0</td></tr>
    <tr><th>Ano Fab/Mod</th><td>2017/2018</td></tr>
  </table>
</section></body></html>`;

describe("extrator de HTML de placas (PoC)", () => {
  it("extrai somente marca, modelo e ano-modelo em estrutura explícita", () => {
    expect(extrairVeiculoDeHtml(exemplo, placa)).toEqual({
      marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018",
    });
  });

  it("aceita pares dt/dd e placas com hífen", () => {
    const html = `<html><body>Placa ABC-1234<dl>
      <dt>Marca</dt><dd>Fiat</dd>
      <dt>Modelo</dt><dd>Uno</dd>
      <dt>Ano Modelo</dt><dd>2010</dd>
    </dl></body></html>`;
    expect(extrairVeiculoDeHtml(html, "ABC1234")).toEqual({
      marca: "Fiat", modelo: "Uno", ano: "2010",
    });
  });

  it("não aceita conteúdo promocional ou HTML de outra placa", () => {
    expect(extrairVeiculoDeHtml("<h1>Marca, modelo e ano grátis</h1>", placa)).toBeNull();
    expect(extrairVeiculoDeHtml(exemplo, "ABC1234")).toBeNull();
    expect(extrairVeiculoDeHtml("<h1>Consulta ITR1J02</h1><p>Marca, modelo e ano</p>", placa)).toBeNull();
  });
});

describe("provedor HTML opt-in no backend", () => {
  let ambiente: Ambiente | undefined;
  afterEach(async () => {
    vi.unstubAllGlobals();
    if (ambiente) await ambiente.fechar();
    ambiente = undefined;
  });
  beforeEach(() => vi.unstubAllGlobals());

  async function consultar(ativar: boolean) {
    ambiente = await criarAmbiente({ PLACAS_WEB_EXPERIMENTAL: ativar ? "true" : "false" });
    const conta = await cadastrar(ambiente.app);
    return ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${conta.empresas[0]!.id}/veiculos/${placa}`,
      headers: conta.cabecalho,
    });
  }

  it("usa Carro FIPE apenas quando a integração experimental está habilitada", async () => {
    const chamada = vi.fn(async (url: unknown) => {
      if (String(url).startsWith("https://www.carrofipe.com.br/consultar?placa=")) {
        return new Response(exemplo, {
          status: 200, headers: { "content-type": "text/html; charset=utf-8" },
        });
      }
      throw new Error("Não deveria consultar outro provedor");
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar(true);
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018" });
    expect(chamada).toHaveBeenCalledTimes(1);
    expect(String(chamada.mock.calls[0]?.[0])).toBe(
      "https://www.carrofipe.com.br/consultar?placa=ITR1J02",
    );
  });

  it("com opção desabilitada não consulta sites HTML", async () => {
    const chamada = vi.fn(async (url: unknown) => {
      expect(String(url)).toBe("https://fipe.parallelum.com.br/api/v2/plates/ITR1J02");
      return new Response(JSON.stringify({
        brand: "VW", model: "Polo", year: "2020",
      }), { status: 200 });
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar(false);
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ marca: "VW", modelo: "Polo", ano: "2020" });
    expect(chamada).toHaveBeenCalledTimes(1);
  });

  it("não contorna resposta 403 e mantém fallback Parallelum", async () => {
    const chamada = vi.fn(async (url: unknown) => {
      if (String(url).includes("parallelum.com.br")) {
        return new Response(JSON.stringify({
          brand: "Fiat", model: "Palio", year: "2015",
        }), { status: 200 });
      }
      return new Response("Bloqueado", { status: 403 });
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar(true);
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ marca: "Fiat", modelo: "Palio", ano: "2015" });
    expect(chamada.mock.calls.some(args => String(args[0]).includes("parallelum.com.br"))).toBe(true);
  });
});
