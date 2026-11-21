import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  consultarVeiculoEmPaginas, extrairVeiculoDeHtml, interpretarPreviaCarroFipe, resetarOrcamentoPlacasWeb,
} from "../src/servicos/placasWeb.js";
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

describe("extrator de HTML de placas", () => {
  it("extrai somente marca, modelo e ano-modelo em estrutura explícita", () => {
    expect(extrairVeiculoDeHtml(exemplo, placa)).toEqual({
      marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018",
    });
  });

  it("extrai o HTML do Puxa Placa e rejeita dados de outra placa", () => {
    const html = '<html><head><title>Placa NAQ2405</title></head><body><h1>Placa NAQ-2405</h1>' +
      '<table><tr><td>Marca:</td><td><a>FIAT</a></td></tr>' +
      '<tr><td>Modelo:</td><td>MOBI DRIVE</td></tr>' +
      '<tr><td>Ano:</td><td>2017</td></tr>' +
      '<tr><td>Ano Modelo:</td><td>2018</td></tr></table></body></html>';
    expect(extrairVeiculoDeHtml(html, "NAQ2405")).toEqual({ marca: "FIAT", modelo: "MOBI DRIVE", ano: "2018" });
    expect(extrairVeiculoDeHtml(html, "HLF2034")).toBeNull();
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

// Formato consumido pelo JavaScript público de /consultar em 20/11/2026.
// Os valores abaixo são fictícios; não são uma consulta real de veículo.
const previa = { simulated: false, marca: "Volkswagen", modelo: "Gol 1.0", ano: "2017/2018" };

describe("prévia gratuita do CarroFIPE", () => {
  it("extrai apenas marca, modelo e ano-modelo do JSON usado pelo site", () => {
    expect(interpretarPreviaCarroFipe({ preview: { ...previa, cor: "Prata", chassi: "ignorar" } }, placa)).toEqual({
      status: "encontrado", dados: { marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018" }, fonte: "CarroFIPE",
    });
  });

  it("aceita ano numérico e placa confirmada pela fonte", () => {
    expect(interpretarPreviaCarroFipe({ preview: { ...previa, placa: "ITR-1J02", ano: 2018 } }, placa)).toMatchObject({
      status: "encontrado", dados: { ano: "2018" },
    });
  });

  it("preenche o modelo mesmo quando a fonte não informa os campos opcionais", () => {
    expect(interpretarPreviaCarroFipe({ preview: { simulated: false, modelo: "Gol" } }, placa)).toMatchObject({
      status: "encontrado", dados: { modelo: "Gol", marca: "", ano: "" },
    });
  });

  it.each([
    null, [], {}, { marca: "Fiat", modelo: "Uno", ano: "2010" }, { preview: [] },
    { preview: { ...previa, simulated: true } }, { preview: { ...previa, simulated: undefined } },
    { preview: { ...previa, simulated: "false" } },
    { preview: { ...previa, providerDown: true } },
    { preview: { ...previa, placa: "ABC1234" } },
    { placa: "ABC1234", preview: previa },
    { preview: { ...previa, modelo: "Não encontrado" } },
    { preview: { ...previa, modelo: "<b>Gol</b>" } },
    { preview: { ...previa, modelo: "G".repeat(101) } },
    { preview: { simulated: false } },
  ])("rejeita prévia inválida, demonstração ou dados de outra placa: %j", (resposta) => {
    expect(interpretarPreviaCarroFipe(resposta, placa)).toEqual({ status: "indisponivel" });
  });

  it("só considera ausência quando a resposta real a informa explicitamente", () => {
    expect(interpretarPreviaCarroFipe({ preview: { simulated: false, note: "Placa não encontrada" } }, placa))
      .toEqual({ status: "nao_encontrado" });
    expect(interpretarPreviaCarroFipe({ preview: { simulated: false, providerDown: true, note: "Placa não encontrada" } }, placa))
      .toEqual({ status: "indisponivel" });
  });

  it("não considera placa ausente quando a prévia real só valida o formato", () => {
    expect(interpretarPreviaCarroFipe({
      preview: { simulated: false },
      note: "Placa válida. Os dados completos saem no relatório.",
    }, placa)).toEqual({ status: "indisponivel" });
  });
});

describe("provedor gratuito no backend (sem API Parallelum)", () => {
  let ambiente: Ambiente | undefined;
  afterEach(async () => {
    vi.unstubAllGlobals();
    if (ambiente) await ambiente.fechar();
    ambiente = undefined;
  });
  beforeEach(() => {
    vi.unstubAllGlobals();
    resetarOrcamentoPlacasWeb();
  });

  async function consultar(ativar?: boolean) {
    const env: Record<string, string> = {};
    if (ativar !== undefined) env.PLACAS_WEB_ATIVAS = ativar ? "true" : "false";
    ambiente = await criarAmbiente(env);
    const conta = await cadastrar(ambiente.app);
    return ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${conta.empresas[0]!.id}/veiculos/${placa}`,
      headers: conta.cabecalho,
    });
  }

  it("usa CarroFIPE por padrão quando PLACAS_WEB_ATIVAS não é informado", async () => {
    const chamada = vi.fn(async (url: unknown, opcoes?: RequestInit) => {
      if (String(url) === "https://www.carrofipe.com.br/api/consultar") {
        expect(opcoes?.method).toBe("POST");
        expect(JSON.parse(String(opcoes?.body))).toEqual({ placa });
        expect(opcoes?.headers).toMatchObject({ "Content-Type": "application/json", Accept: "application/json" });
        expect(opcoes?.headers).not.toHaveProperty("Cookie");
        expect(opcoes?.headers).not.toHaveProperty("Authorization");
        expect(opcoes?.redirect).toBe("error");
        return Response.json({ preview: previa });
      }
      throw new Error("Não deveria consultar outro provedor");
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar();
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018" });
    expect(chamada).toHaveBeenCalledTimes(1);
    expect(String(chamada.mock.calls[0]?.[0])).toBe(
      "https://www.carrofipe.com.br/api/consultar",
    );
  });

  it("tenta as demais páginas HTML quando CarroFIPE responde 403 e nunca chama Parallelum", async () => {
    const chamada = vi.fn(async (url: unknown) => {
      const destino = String(url);
      expect(destino).not.toContain("parallelum.com.br");
      if (destino.startsWith("https://www.carrofipe.com.br/")) {
        return new Response("Bloqueado", { status: 403 });
      }
      if (destino.startsWith("https://puxaplaca.com.br/placa/")) {
        return new Response(exemplo, {
          status: 200, headers: { "content-type": "text/html; charset=utf-8" },
        });
      }
      return new Response("Bloqueado", { status: 403 });
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar(true);
    expect(r.statusCode).toBe(200);
    expect(r.json()).toEqual({ marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018" });
    expect(chamada).toHaveBeenCalledTimes(2);
    expect(String(chamada.mock.calls[0]?.[0])).toBe("https://www.carrofipe.com.br/api/consultar");
    expect(String(chamada.mock.calls[1]?.[0])).toBe("https://puxaplaca.com.br/placa/ITR1J02");
  });

  it("com PLACAS_WEB_ATIVAS=false não faz chamadas externas nem consulta Parallelum", async () => {
    const chamada = vi.fn(async () => {
      throw new Error("Não deveria fazer chamada externa");
    });
    vi.stubGlobal("fetch", chamada);
    const r = await consultar(false);
    expect(r.statusCode).toBe(503);
    expect(chamada).not.toHaveBeenCalled();
  });

  it.each(["html_inicial", "json_invalido", "demonstracao", "fornecedor_fora", "rota_404", "captcha", "grande", "stream_grande", "timeout"])(
    "tenta HTML alternativo quando a prévia falha: %s", async (falha) => {
      const chamada = vi.fn(async (url: unknown) => {
        if (String(url).includes("/api/consultar")) {
          if (falha === "timeout") throw new DOMException("Timeout", "TimeoutError");
          if (falha === "json_invalido") return new Response("{", { headers: { "content-type": "application/json" } });
          if (falha === "demonstracao") return Response.json({ preview: { ...previa, simulated: true } });
          if (falha === "fornecedor_fora") return Response.json({ preview: { ...previa, providerDown: true } });
          if (falha === "rota_404") return new Response("Not Found", { status: 404 });
          if (falha === "grande") return Response.json({ preview: previa }, { headers: { "content-length": "200001" } });
          if (falha === "stream_grande") return Response.json({ preview: previa, extra: "a".repeat(200001) });
          return new Response(falha === "captcha" ? "<html>Verify you are human</html>" : "<html>Consultar placa</html>", {
            headers: { "content-type": "text/html" },
          });
        }
        return new Response(exemplo, { headers: { "content-type": "text/html" } });
      });
      vi.stubGlobal("fetch", chamada);
      expect((await consultar()).json()).toEqual({ marca: "Volkswagen", modelo: "Gol 1.0", ano: "2018" });
      expect(chamada).toHaveBeenCalledTimes(2);
    },
  );

  it("não confunde ausência em uma fonte com falha nas outras", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: unknown) => String(url).includes("/api/consultar")
      ? Response.json({ preview: { simulated: false, note: "Placa não encontrada" } })
      : new Response("<html>Consultar placa</html>", { headers: { "content-type": "text/html" } })));
    expect((await consultar()).statusCode).toBe(503);
  });

  it("respeita o novo orçamento de 1200 requisições também na prévia JSON", async () => {
    const chamada = vi.fn(async () => Response.json({ preview: previa }));
    vi.stubGlobal("fetch", chamada);
    for (let i = 0; i < 1200; i++) expect((await consultarVeiculoEmPaginas(placa)).status).toBe("encontrado");
    expect((await consultarVeiculoEmPaginas(placa)).status).toBe("indisponivel");
    expect(chamada).toHaveBeenCalledTimes(1200);
  });
});
