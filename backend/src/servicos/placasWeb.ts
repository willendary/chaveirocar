/**
 * Prova de conceito para páginas públicas de consulta de placas.
 *
 * Inspirada na estratégia de múltiplas fontes do projeto PlacaCarro-API,
 * SEM copiar seu código e SEM contornar autenticação, CAPTCHA, bloqueios ou TLS.
 * A integração só pode ser ativada após validação do acesso automatizado e
 * autorização/licença dos proprietários de cada fonte.
 */
export interface DadosVeiculoWeb {
  marca: string;
  modelo: string;
  ano: string;
}

interface Fonte {
  nome: string;
  origem: string;
  caminho: (placa: string) => string;
}

const FONTES: readonly Fonte[] = [
  { nome: "PlacaIPVA", origem: "https://placaipva.com.br", caminho: (placa) => "/placa/" + placa },
  { nome: "KePlaca", origem: "https://www.keplaca.com", caminho: (placa) => "/placa/" + placa },
  { nome: "PlacaFIPE", origem: "https://placafipe.com", caminho: (placa) => "/placa/" + placa },
];

const MAX_HTML_BYTES = 200_000;
const INTERVALO_HORA = 60 * 60_000;
const MAX_TENTATIVAS_POR_HORA = 30;
let janelaInicio = Date.now();
let tentativas = 0;

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
 * Extrai APENAS as linhas identificadas Marca/Modelo/Ano da tabela,
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

  const marca = campos.get("marca")?.trim() ?? "";
  const modelo = campos.get("modelo")?.trim() ?? "";
  const anoBruto = campos.get("anomodelo") || campos.get("ano") || campos.get("anofabricacao") || "";
  const ano = anoBruto.match(/\b(?:19|20)\d{2}\b/)?.[0] ?? "";

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
 * Executa no máximo três tentativas, apenas quando explicitamente habilitado.
 * Requisições externas não usam cookies, credenciais, proxies ou redirecionamento.
 * 403/429/CAPTCHA são respeitados: nunca tentamos contorná-los.
 */
export async function buscarVeiculoEmPaginas(placa: string): Promise<DadosVeiculoWeb | null> {
  for (const fonte of FONTES) {
    if (!dentroDoOrcamento()) return null;
    const url = fonte.origem + fonte.caminho(placa);
    try {
      const resposta = await fetch(url, {
        method: "GET",
        headers: {
          Accept: "text/html",
          "User-Agent": "ChaveiroCar-ConsultaTecnica/1.13.2 (+https://github.com/willendary/chaveirocar)",
        },
        redirect: "error",
        signal: AbortSignal.timeout(5000),
      });
      if (resposta.status === 403 || resposta.status === 429) continue;
      if (!resposta.ok || !resposta.headers.get("content-type")?.includes("text/html")) continue;
      const html = await lerHtmlLimitado(resposta);
      if (!html) continue;
      const dados = extrairVeiculoDeHtml(html, placa);
      if (dados) return dados;
    } catch {
      // Não ocultamos CAPTCHA ou burlamos bloqueios. A próxima fonte pode responder.
    }
  }
  return null;
}
