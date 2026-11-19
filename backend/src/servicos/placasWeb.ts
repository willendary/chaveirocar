/**
 * Consulta direta em páginas HTML públicas de consulta veicular por placa.
 *
 * Estratégia conservadora de múltiplas fontes HTML (CarroFIPE como principal,
 * seguida por PlacaIPVA, KePlaca e PlacaFIPE), sem contornar autenticação,
 * CAPTCHA, bloqueios HTTP 403/429 ou TLS, extraindo apenas marca, modelo e ano.
 */
export interface DadosVeiculoWeb {
  marca: string;
  modelo: string;
  ano: string;
}

export type ResultadoConsultaWeb =
  | { status: "encontrado"; dados: DadosVeiculoWeb; fonte: string }
  | { status: "nao_encontrado" }
  | { status: "indisponivel" };

interface Fonte {
  nome: string;
  origem: string;
  caminho: (placa: string) => string;
}

export const FONTES_HTML_PLACAS: readonly Fonte[] = [
  { nome: "CarroFIPE", origem: "https://www.carrofipe.com.br", caminho: (placa) => "/consultar?placa=" + encodeURIComponent(placa) },
  { nome: "PlacaIPVA", origem: "https://placaipva.com.br", caminho: (placa) => "/placa/" + placa },
  { nome: "KePlaca", origem: "https://www.keplaca.com", caminho: (placa) => "/placa/" + placa },
  { nome: "PlacaFIPE", origem: "https://placafipe.com", caminho: (placa) => "/placa/" + placa },
];

const MAX_HTML_BYTES = 200_000;
const INTERVALO_HORA = 60 * 60_000;
const MAX_TENTATIVAS_POR_HORA = 60;
let janelaInicio = Date.now();
let tentativas = 0;

export function resetarOrcamentoPlacasWeb(): void {
  janelaInicio = Date.now();
  tentativas = 0;
}

function dentroDoOrcamento(): boolean {
  if (Date.now() - janelaInicio >= INTERVALO_HORA) {
    janelaInicio = Date.now();
    tentativas = 0;
  }
  if (tentativas >= MAX_TENTATIVAS_POR_HORA) return false;
  tentativas += 1;
  return true;
}

function textoHtml(texto: string): string {
  return texto
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, " ")
    .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]*>/g, " ")
    .replace(/&(?:nbsp|#160);/gi, " ")
    .replace(/&(?:amp|#38);/gi, "&")
    .replace(/&(?:quot|#34);/gi, '"')
    .replace(/&(?:apos|#39);/gi, "'")
    .replace(/&#(\d+);/g, (_, dec: string) => String.fromCodePoint(Number(dec)))
    .replace(/\s+/g, " ")
    .trim();
}

function normalizarRotulo(valor: string): string {
  return valor.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z]/g, "");
}

/**
 * Extrai APENAS campos identificados Marca/Modelo/Ano de tabela ou lista de descrição,
 * nunca usando conteúdo editorial, JS ou atributos desconhecidos da página.
 * Descarta páginas sem a placa solicitada ou campos incompletos.
 */
export function extrairVeiculoDeHtml(html: string, placa: string): DadosVeiculoWeb | null {
  if (html.length > MAX_HTML_BYTES) return null;
  const placaLimpa = placa.toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (!/^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$/.test(placaLimpa)) return null;

  const texto = textoHtml(html).toUpperCase();
  const busca = new RegExp("(^|[^A-Z0-9])" + placaLimpa.slice(0, 3) +
    "[-\\s]?" + placaLimpa.slice(3) + "($|[^A-Z0-9])");
  if (!busca.test(texto)) return null;

  const campos = new Map<string, string>();
  const linhas = html.match(/<tr\b[^>]*>[\s\S]*?<\/tr>/gi) ?? [];
  for (const linha of linhas) {
    const celulas = [...linha.matchAll(/<(?:td|th)\b[^>]*>([\s\S]*?)<\/(?:td|th)>/gi)]
      .map((match) => textoHtml(match[1] ?? ""));
    if (celulas.length < 2) continue;
    const nome = normalizarRotulo(celulas[0] ?? "");
    if (!campos.has(nome)) campos.set(nome, (celulas[1] ?? "").slice(0, 100));
  }

  // Alguns provedores renderizam resultados como <dl><dt>Marca</dt><dd>...</dd></dl>.
  // Só usamos os pares explícitos; texto promocional isolado nunca gera cadastro.
  const paresDescricao = [...html.matchAll(/<dt\b[^>]*>([\s\S]*?)<\/dt>\s*<dd\b[^>]*>([\s\S]*?)<\/dd>/gi)];
  for (const par of paresDescricao) {
    const nome = normalizarRotulo(textoHtml(par[1] ?? ""));
    if (!campos.has(nome)) campos.set(nome, textoHtml(par[2] ?? "").slice(0, 100));
  }

  // Não basta uma menção genérica a "marca/modelo" em uma página de propaganda.
  // Os campos precisam vir em estrutura de chave/valor associada à placa.
  const marca = campos.get("marca")?.trim() ?? "";
  const modelo = campos.get("modelo")?.trim() ?? "";
  const anoBruto = campos.get("anomodelo") || campos.get("anofabmod") || campos.get("ano") || campos.get("anofabricacao") || "";
  const anos = anoBruto.match(/\b(?:19|20)\d{2}\b/g) ?? [];
  const ano = anos.at(-1) ?? ""; // Quando há fabricação/modelo, preferir ano-modelo.

  if (!marca || !modelo || !ano ||
      marca.length > 80 || modelo.length > 100 ||
      /^(?:nao encontrado|n\/a|desconhecido|indisponivel)$/i.test(modelo)) return null;
  return { marca, modelo, ano };
}

async function lerHtmlLimitado(resposta: Response): Promise<string | null> {
  const tamanho = resposta.headers.get("content-length");
  if (tamanho && Number(tamanho) > MAX_HTML_BYTES) return null;
  if (!resposta.body) return null;

  const leitor = resposta.body.getReader();
  const pedacos: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await leitor.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_HTML_BYTES) return null;
      pedacos.push(value);
    }
  } finally {
    await leitor.cancel().catch(() => undefined);
  }
  const buffer = new Uint8Array(total);
  let posicao = 0;
  for (const parte of pedacos) {
    buffer.set(parte, posicao);
    posicao += parte.length;
  }
  return new TextDecoder().decode(buffer);
}

/**
 * Consulta as fontes HTML públicas em ordem de prioridade.
 * Requisições externas não usam cookies, credenciais, proxies ou redirecionamento.
 * 403/429/CAPTCHA são respeitados: nunca tentamos contorná-los.
 */
export async function consultarVeiculoEmPaginas(placa: string): Promise<ResultadoConsultaWeb> {
  let houveRespostaSemResultado = false;

  for (const fonte of FONTES_HTML_PLACAS) {
    if (!dentroDoOrcamento()) {
      return { status: "indisponivel" };
    }
    const url = fonte.origem + fonte.caminho(placa);
    try {
      const resposta = await fetch(url, {
        method: "GET",
        headers: {
          Accept: "text/html",
          "User-Agent": "ChaveiroCar-ConsultaTecnica/1.14.0 (+https://github.com/willendary/chaveirocar)",
        },
        redirect: "error",
        signal: AbortSignal.timeout(5000),
      });
      if (resposta.status === 404) {
        houveRespostaSemResultado = true;
        continue;
      }
      if (resposta.status === 403 || resposta.status === 429) continue;
      if (!resposta.ok || !resposta.headers.get("content-type")?.includes("text/html")) continue;
      const html = await lerHtmlLimitado(resposta);
      if (!html) continue;
      const dados = extrairVeiculoDeHtml(html, placa);
      if (dados) {
        return { status: "encontrado", dados, fonte: fonte.nome };
      }
      houveRespostaSemResultado = true;
    } catch {
      // Não ocultamos CAPTCHA ou burlamos bloqueios. A próxima fonte pode responder.
    }
  }

  return houveRespostaSemResultado ? { status: "nao_encontrado" } : { status: "indisponivel" };
}

export async function buscarVeiculoEmPaginas(placa: string): Promise<DadosVeiculoWeb | null> {
  const resultado = await consultarVeiculoEmPaginas(placa);
  return resultado.status === "encontrado" ? resultado.dados : null;
}
