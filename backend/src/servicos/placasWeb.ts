/**
 * Consulta a prévia pública gratuita do CarroFIPE e páginas HTML alternativas.
 *
 * O CarroFIPE carrega a prévia por POST /api/consultar no navegador: o HTML de
 * /consultar é apenas o formulário. As alternativas são consultadas sem contornar autenticação,
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
  { nome: "PlacaIPVA", origem: "https://placaipva.com.br", caminho: (placa) => "/placa/" + placa },
  { nome: "KePlaca", origem: "https://www.keplaca.com", caminho: (placa) => "/placa/" + placa },
  { nome: "PlacaFIPE", origem: "https://placafipe.com", caminho: (placa) => "/placa/" + placa },
];

const URL_PREVIA_CARROFIPE = "https://www.carrofipe.com.br/api/consultar";
const USER_AGENT = "ChaveiroCar-ConsultaTecnica (+https://github.com/willendary/chaveirocar)";
const MAX_RESPOSTA_BYTES = 200_000;
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
  if (html.length > MAX_RESPOSTA_BYTES) return null;
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

async function lerTextoLimitado(resposta: Response): Promise<string | null> {
  const tamanho = resposta.headers.get("content-length");
  if (tamanho && Number(tamanho) > MAX_RESPOSTA_BYTES) {
    await resposta.body?.cancel().catch(() => undefined);
    return null;
  }
  if (!resposta.body) return null;

  const leitor = resposta.body.getReader();
  const pedacos: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await leitor.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_RESPOSTA_BYTES) return null;
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

function objeto(valor: unknown): Record<string, unknown> | null {
  return valor !== null && typeof valor === "object" && !Array.isArray(valor)
    ? valor as Record<string, unknown> : null;
}

function textoCampo(valor: unknown, limite: number): string {
  if (typeof valor !== "string") return "";
  const texto = valor.replace(/\s+/g, " ").trim();
  if (texto.length > limite || /[<>\x00-\x1f]/.test(texto) ||
      /^(?:n[aã]o (?:encontrad[oa]|informad[oa])|n\/a|desconhecid[oa]|indispon[ií]vel|-+)$/i.test(texto)) return "";
  return texto;
}

function confirmaAusencia(texto: unknown): boolean {
  if (typeof texto !== "string") return false;
  const normalizado = texto.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  return /\b(?:placa|veiculo) nao (?:encontrad[oa]|localizad[oa])\b/.test(normalizado) ||
    /\b(?:nenhum veiculo encontrado|nao (?:encontramos|localizamos) (?:dados|o veiculo) para (?:esta|essa) placa)\b/.test(normalizado);
}

/** Aceita somente a prévia real; nunca usa demonstrações ou dados do laudo pago. */
export function interpretarPreviaCarroFipe(valor: unknown, placa: string): ResultadoConsultaWeb {
  const resposta = objeto(valor);
  const previa = objeto(resposta?.preview);
  if (!previa || previa.simulated !== false || previa.providerDown === true) {
    return { status: "indisponivel" };
  }
  // A prévia nem sempre repete a placa. Quando informa, deve coincidir com a consulta.
  for (const informada of [resposta?.placa, previa.placa]) {
    if (informada !== undefined && (typeof informada !== "string" ||
        informada.toUpperCase().replace(/[\s-]/g, "") !== placa)) {
      return { status: "indisponivel" };
    }
  }
  const modelo = textoCampo(previa.modelo, 100);
  if (!modelo) {
    return confirmaAusencia(previa.note ?? resposta?.note)
      ? { status: "nao_encontrado" } : { status: "indisponivel" };
  }
  const marca = textoCampo(previa.marca, 80);
  const anoBruto = typeof previa.ano === "number" ? String(previa.ano) : textoCampo(previa.ano, 30);
  const ano = anoBruto.match(/\b(?:19|20)\d{2}\b/g)?.at(-1) ?? "";
  return { status: "encontrado", dados: { marca, modelo, ano }, fonte: "CarroFIPE" };
}

async function consultarPreviaCarroFipe(placa: string): Promise<ResultadoConsultaWeb> {
  if (!dentroDoOrcamento()) return { status: "indisponivel" };
  try {
    const resposta = await fetch(URL_PREVIA_CARROFIPE, {
      method: "POST",
      headers: { Accept: "application/json", "Content-Type": "application/json", "User-Agent": USER_AGENT },
      body: JSON.stringify({ placa }),
      redirect: "error",
      signal: AbortSignal.timeout(12_000),
    });
    if (!resposta.ok || !resposta.headers.get("content-type")?.toLowerCase().includes("application/json")) {
      await resposta.body?.cancel().catch(() => undefined);
      return { status: "indisponivel" };
    }
    const texto = await lerTextoLimitado(resposta);
    return texto ? interpretarPreviaCarroFipe(JSON.parse(texto), placa) : { status: "indisponivel" };
  } catch {
    return { status: "indisponivel" };
  }
}

/**
 * Consulta a prévia gratuita e, se necessário, as fontes HTML alternativas.
 * Requisições externas não usam cookies, credenciais, proxies ou redirecionamento.
 * 403/429/CAPTCHA são respeitados: nunca tentamos contorná-los.
 */
export async function consultarVeiculoEmPaginas(placa: string): Promise<ResultadoConsultaWeb> {
  const previa = await consultarPreviaCarroFipe(placa);
  if (previa.status === "encontrado") return previa;
  let houveIndisponibilidade = previa.status === "indisponivel";

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
          "User-Agent": USER_AGENT,
        },
        redirect: "error",
        signal: AbortSignal.timeout(5000),
      });
      if ((!resposta.ok && resposta.status !== 404) ||
          !resposta.headers.get("content-type")?.toLowerCase().includes("text/html")) {
        houveIndisponibilidade = true;
        await resposta.body?.cancel().catch(() => undefined);
        continue;
      }
      const html = await lerTextoLimitado(resposta);
      if (!html) {
        houveIndisponibilidade = true;
        continue;
      }
      const dados = resposta.ok ? extrairVeiculoDeHtml(html, placa) : null;
      if (dados) {
        return { status: "encontrado", dados, fonte: fonte.nome };
      }
      // HTML inicial, CAPTCHA, mudança de layout ou 404 da rota não provam ausência.
      const texto = textoHtml(html);
      const placaPresente = texto.toUpperCase().replace(/[\s-]/g, "").includes(placa);
      if (!placaPresente || !confirmaAusencia(texto)) houveIndisponibilidade = true;
    } catch {
      houveIndisponibilidade = true;
      // Não ocultamos CAPTCHA ou burlamos bloqueios. A próxima fonte pode responder.
    }
  }

  return houveIndisponibilidade ? { status: "indisponivel" } : { status: "nao_encontrado" };
}

export async function buscarVeiculoEmPaginas(placa: string): Promise<DadosVeiculoWeb | null> {
  const resultado = await consultarVeiculoEmPaginas(placa);
  return resultado.status === "encontrado" ? resultado.dados : null;
}
