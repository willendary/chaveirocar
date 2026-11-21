import { describe, it, expect, vi } from "vitest";
import { consultarVeiculoEmPaginas, extrairVeiculoDeHtml, interpretarPreviaCarroFipe, resetarOrcamentoPlacasWeb } from "../src/servicos/placasWeb.js";

/**
 * Auditoria TEMPORÁRIA, NÃO FAZ PARTE DOS TESTES SINTÉTICOS:
 * verifica 31 placas públicas documentadas pela Detran-RR e Prefeitura de Montes Claros.
 * Os nomes foram publicados nos respectivos cadastros; não há dados pessoais.
 * As consultas aqui são REAIS e respeitam 403/429/CAPTCHA, sem contorno.
 * Remover ou marcar como skip depois da investigação; nunca lançar CI com teste de scraping obrigatório.
 */
const placas: Array<[string, string]> = [
  [
    "NAQ2405",
    "Fiat Mobi Drive"
  ],
  [
    "NAS1E84",
    "Fiat Mobi Like"
  ],
  [
    "NUI7727",
    "Fiat Mobi Like"
  ],
  [
    "NUL1I47",
    "Fiat Uno Attractive 1.0"
  ],
  [
    "PHB5711",
    "Fiat Uno Vivace 1.0"
  ],
  [
    "PHW0B18",
    "Volkswagen Gol 1.0"
  ],
  [
    "PHW9B78",
    "Volkswagen Gol 1.0"
  ],
  [
    "HLF2034",
    "Volkswagen Gol 1.0"
  ],
  [
    "HLF7230",
    "Fiat Uno Economy"
  ],
  [
    "QNM6221",
    "Renault Logan 1.6"
  ],
  [
    "QNM6230",
    "Renault Logan 1.6"
  ],
  [
    "HLF7228",
    "Fiat Uno Economy"
  ],
  [
    "HLF7227",
    "Fiat Uno Economy"
  ],
  [
    "QNN6568",
    "Volkswagen Gol Trend"
  ],
  [
    "QNN6560",
    "Volkswagen Gol Trend"
  ],
  [
    "QNN6511",
    "Volkswagen Gol Trend"
  ],
  [
    "HLF2028",
    "Volkswagen Gol 1.0"
  ],
  [
    "EVZ8764",
    "Nissan Grand Livina"
  ],
  [
    "QPS9034",
    "Volkswagen Novo Gol 1.0"
  ],
  [
    "HLF2025",
    "Volkswagen Gol 1.0"
  ],
  [
    "QNM6209",
    "Renault Logan 1.6"
  ],
  [
    "QNP8829",
    "Volkswagen Novo Gol 1.0"
  ],
  [
    "HLF1818",
    "Fiat Linea LX 1.9"
  ],
  [
    "QPS9032",
    "Volkswagen Gol 1.0"
  ],
  [
    "QNM6199",
    "Renault Logan 1.6"
  ],
  [
    "OMG1625",
    "Volkswagen Gol GIV"
  ],
  [
    "QPS9030",
    "Volkswagen Gol 1.0"
  ],
  [
    "HLF2078",
    "Volkswagen Gol 1.0"
  ],
  [
    "HLF2414",
    "Fiat Uno Mille Economy"
  ],
  [
    "QNP8819",
    "Volkswagen Novo Gol 1.0"
  ],
  [
    "HLF2011",
    "Volkswagen Gol 1.0"
  ]
];
const controles: Array<[string, string]> = [
  ["GAK2409", "Fiat Mobi Drive"],
  ["HLF3026", "Fiat Doblo Pfister Amb"],
];

const pausa = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const canon = (s: string) => s.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toUpperCase().replace(/[^A-Z0-9]/g, "");

describe("auditoria live de 31 placas oficiais (diagnóstico, sem fixtures)", () => {
 it("executa o fluxo REAL do backend nas 31 placas e dois controles", async () => {
    resetarOrcamentoPlacasWeb();
    expect(placas.length).toBe(31);
    expect(new Set(placas.map(x => x[0])).size).toBe(31);
    const originalFetch = globalThis.fetch;
    const hostsBloqueados = new Set<string>();
    const falhasServidor = new Map<string, number>();
    const registros: Array<{placa: string; esperado: string; obtido: string; status: string; fonte: string}> = [];
    let requestsReais = 0, semDadosEmPaginas = 0, parserPerdeu = 0;
    vi.stubGlobal("fetch", async (url: string | URL | Request, init?: RequestInit) => {
       const destino = String(url);
       const host = new URL(destino).hostname;
       const placa = String(init?.method) === "POST" ? 
          String((JSON.parse(String(init?.body)) as {placa: string}).placa) :
          destino.split("/").at(-1) ?? "";
       if (hostsBloqueados.has(host)) {
         console.log("HTTP_SKIP", host, placa, "bloqueado por 403/429 ou indisponibilidade repetida");
         return new Response("fonte suspensa nesta auditoria", {status: 503});
       }
       // Espaçamento para não bombardear o mesmo provedor.
       await pausa(650);
       requestsReais++;
       try {
         const resposta = await originalFetch(url, init);
         const tipo = resposta.headers.get("content-type") || "";
         let extraido = "nao"; let temRotulo = false; let titulo = "";
         if (resposta.ok && /application\/json|text\/html/i.test(tipo)) {
            const tam = Number(resposta.headers.get("content-length") || "0");
            if (!tam || tam < 250_000) {
              const corpo = await resposta.clone().text();
              if (corpo.length < 250_000) {
                if (/json/i.test(tipo)) {
                  try {
                    const p = interpretarPreviaCarroFipe(JSON.parse(corpo), placa);
                    extraido = p.status === "encontrado" ? p.dados.modelo : p.status;
                  } catch { extraido = "json-invalido"; }
                } else {
                  temRotulo = /modelo\s*:/i.test(corpo) || /<t[dh][^>]*>\s*modelo\s*<\/t[dh]>/i.test(corpo);
                  const m = corpo.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
                  titulo = m ? m[1].replace(/<[^>]*>/g, "").slice(0,100).trim() : "";
                  const p = extrairVeiculoDeHtml(corpo, placa);
                  extraido = p?.modelo ?? "nao";
                  if (temRotulo) {
                    semDadosEmPaginas++;
                    if (!p) parserPerdeu++;
                  }
                }
              }
            }
         }
         console.log("HTTP_REAL", host, placa, resposta.status, tipo.split(";")[0], "rotulo_modelo="+temRotulo,
                     "parser="+extraido, "titulo="+JSON.stringify(titulo));
         if (resposta.status === 403 || resposta.status === 429 || resposta.status === 451) hostsBloqueados.add(host);
         if (resposta.status >= 500) {
           const falhas = (falhasServidor.get(host) ?? 0) + 1;
           falhasServidor.set(host, falhas);
           if (falhas >= 3) hostsBloqueados.add(host);
         }
         return resposta;
       } catch (e) {
         const err = e as Error;
         console.log("HTTP_ERRO", host, placa, err.name, (err.message ?? "").slice(0,95));
         const falhas = (falhasServidor.get(host) ?? 0) + 1;
         falhasServidor.set(host,falhas);
         if (falhas >= 3) hostsBloqueados.add(host);
         throw e;
       }
    });

    try {
      for(const [placa, esperado] of [...placas, ...controles]) {
        const resultado = await consultarVeiculoEmPaginas(placa);
        const obtido = resultado.status === "encontrado" ? resultado.dados.modelo : "";
        const fonte = resultado.status === "encontrado" ? resultado.fonte : "";
        const status = resultado.status;
        registros.push({placa, esperado, obtido, status, fonte});
        console.log("RESULTADO_REAL", placa, JSON.stringify({esperado, obtido, status, fonte, compativel: !!obtido && canon(esperado).includes(canon(obtido))}));
      }
    } finally {
      vi.unstubAllGlobals();
    }
    const amostra = registros.slice(0,31);
    const encontrados = amostra.filter(x=>x.status==="encontrado");
    const semDados = amostra.filter(x=>x.status==="indisponivel");
    const ausentes = amostra.filter(x=>x.status==="nao_encontrado");
    console.log("RESUMO_31_PLACAS_REAIS",JSON.stringify({
      total:amostra.length, identificadas:encontrados.length, indisponiveis:semDados.length,
      nao_encontradas:ausentes.length, requestsReais,
      paginasComRotuloModelo:semDadosEmPaginas, paginasComModeloQueParserNaoExtraiu:parserPerdeu,
      hostsBloqueados:[...hostsBloqueados],
      controles:registros.slice(31),
    }));
    expect(amostra.length).toBe(31);
  }, 600_000);
});
