import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { consultarPlaca } from "../src/servicos/placas.js";
import { resetarOrcamentoPlacasWeb } from "../src/servicos/placasWeb.js";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

// Estes 240 identificadores têm formato de placa, mas NÃO são um conjunto de
// veículos reais nem fazem acesso a fornecedores externos. O fetch é simulado.
const placasSinteticas = Array.from({ length: 240 }, (_, i) => "TST" + (1000 + i));
let ambiente: Ambiente;

beforeEach(async () => {
  resetarOrcamentoPlacasWeb();
  ambiente = await criarAmbiente();
});
afterEach(async () => {
  vi.unstubAllGlobals();
  await ambiente.fechar();
});

describe("regressão: consultas em lote e isolamento por empresa", () => {
  it("processa 240 placas distintas (220 sucessos, 20 fontes indisponíveis) sem esgotar 60 chamadas", async () => {
    const fakeFetch = vi.fn(async (url: unknown, init?: RequestInit): Promise<Response> => {
      const destino = String(url);
      const placa = destino.endsWith("/api/consultar")
        ? String((JSON.parse(String(init?.body)) as { placa: string }).placa)
        : destino.split("/").at(-1) ?? "";
      const indice = Number(placa.slice(3)) - 1000;
      expect(indice).toBeGreaterThanOrEqual(0);
      expect(indice).toBeLessThan(240);
      if (destino.endsWith("/api/consultar")) {
        if (indice % 12 === 0) return Response.json({ preview: { simulated: false, providerDown: true } });
        if (indice % 12 === 1) return Response.json({ preview: { simulated: false, note: "Prévia não disponível" } });
        return Response.json({
          placa, preview: { placa, simulated: false, marca: "Marca de teste", modelo: "Modelo " + indice, ano: 2021 },
        });
      }
      if (indice % 12 === 1 && destino.includes("placaipva.com.br")) {
        return new Response(`<html><h1>Placa ${placa}</h1><table>
          <tr><th>Marca</th><td>Marca de teste</td></tr>
          <tr><th>Modelo</th><td>Modelo ${indice}</td></tr>
          <tr><th>Ano</th><td>2021</td></tr></table></html>`, {
          headers: { "content-type": "text/html" },
        });
      }
      return new Response("Fornecedor indisponível", { status: 503 });
    });
    vi.stubGlobal("fetch", fakeFetch);

    let encontrados = 0;
    let indisponiveis = 0;
    for (const [indice, placa] of placasSinteticas.entries()) {
      try {
        const dados = await consultarPlaca(ambiente.banco, placa);
        expect(indice % 12).not.toBe(0);
        expect(dados).toEqual({ marca: "Marca de teste", modelo: "Modelo " + indice, ano: "2021" });
        encontrados++;
      } catch (e) {
        expect(indice % 12).toBe(0);
        expect((e as { status: number }).status).toBe(503);
        indisponiveis++;
      }
    }
    expect(new Set(placasSinteticas).size).toBe(240);
    expect({ encontrados, indisponiveis }).toEqual({ encontrados: 220, indisponiveis: 20 });
    const chamadasNoPrimeiroLote = fakeFetch.mock.calls.length;
    expect(chamadasNoPrimeiroLote).toBeGreaterThan(300);
    expect(chamadasNoPrimeiroLote).toBeLessThan(400);

    // Um segundo ciclo completo só consulta novamente as 20 indisponíveis.
    for (const [indice, placa] of placasSinteticas.entries()) {
      if (indice % 12 !== 0) {
        const dados = await consultarPlaca(ambiente.banco, placa);
        expect(dados.modelo).toBe("Modelo " + indice);
      }
    }
    expect(fakeFetch).toHaveBeenCalledTimes(chamadasNoPrimeiroLote);
  }, 60_000);

  it("reaproveita histórico da empresa no Turso sem enviar placas para fora e sem misturar empresas", async () => {
    const empresaA = await cadastrar(ambiente.app);
    const empresaB = await cadastrar(ambiente.app);
    const primeira = empresaA.empresas[0]!.id;
    const segunda = empresaB.empresas[0]!.id;
    const placa = "FWA6B37";
    const fakeFetch = vi.fn(async () => new Response("Bloqueado", { status: 429 }));
    vi.stubGlobal("fetch", fakeFetch);
    for (const [id, conta, modelo] of [
      [primeira, empresaA, "Modelo registrado pela equipe A"],
      [segunda, empresaB, "Modelo registrado pela equipe B"],
    ] as const) {
      const salvo = await ambiente.app.inject({
        method: "POST", url: `/api/v1/empresas/${id}/atendimentos`,
        headers: conta.cabecalho,
        payload: { placa, marca: "Marca informada manualmente", modelo, ano: "2020", tiposServico: ["ABERTURA"] },
      });
      expect(salvo.statusCode).toBe(201);
      const consulta = await ambiente.app.inject({
        method: "GET", url: `/api/v1/empresas/${id}/veiculos/${placa}`, headers: conta.cabecalho,
      });
      expect(consulta.statusCode).toBe(200);
      expect(consulta.json()).toEqual({ modelo, marca: "Marca informada manualmente", ano: "2020" });
    }
    expect(fakeFetch).not.toHaveBeenCalled();
  });
});
