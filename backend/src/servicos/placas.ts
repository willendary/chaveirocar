import type { Banco } from "../banco.js";
import { ErroApi, erros } from "../erros.js";
import { buscarVeiculoEmPaginas } from "./placasWeb.js";

interface DadosVeiculo {
  modelo: string;
  marca: string;
  ano: string;
}

const ENDERECO_PLACAS = "https://fipe.parallelum.com.br/api/v2/plates";
const VALIDADE_CACHE_MS = 90 * 24 * 60 * 60 * 1000;

// Evita consultas duplicadas simultâneas para a mesma placa e o mesmo banco.
const consultasPendentes = new WeakMap<Banco, Map<string, Promise<DadosVeiculo>>>();

function normalizarPlaca(placa: string): string {
  const normalizada = placa.trim().toUpperCase().replace(/[\s-]/g, "");
  if (!/^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$/.test(normalizada)) {
    throw erros.validacao("Placa inválida. Informe uma placa brasileira antiga ou Mercosul.");
  }
  return normalizada;
}

async function consultarParallelum(placa: string): Promise<DadosVeiculo> {
  const indisponivel = new ErroApi(503, "INDISPONIVEL", "Consulta por placa indisponível. Informe o modelo manualmente.");

  const resposta = await fetch(`${ENDERECO_PLACAS}/${placa}`, {
    method: "GET",
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(6000),
    redirect: "error",
  }).catch(() => { throw indisponivel; });

  if (resposta.status === 404) throw erros.naoEncontrado("Placa não localizada. Informe o modelo manualmente.");
  if (resposta.status === 429 || resposta.status === 402) {
    throw erros.limite("Cota gratuita mensal de consultas por placa esgotada. Informe o modelo manualmente.");
  }
  if (resposta.status === 400) throw erros.validacao("Placa não aceita pelo serviço de consulta.");
  if (!resposta.ok) throw indisponivel;

  const corpo: unknown = await resposta.json().catch(() => { throw indisponivel; });
  if (!corpo || typeof corpo !== "object" || Array.isArray(corpo)) throw indisponivel;
  const veiculo = corpo as Record<string, unknown>;

  const modelo = typeof veiculo.model === "string" ? veiculo.model.trim() : "";
  const marca = typeof veiculo.brand === "string" ? veiculo.brand.trim() : "";
  const ano = typeof veiculo.year === "string" || typeof veiculo.year === "number"
    ? String(veiculo.year).trim()
    : "";
  if (!modelo) throw erros.naoEncontrado("Modelo não localizado. Informe manualmente.");

  // Não retornamos nem persistimos chassi, município ou outros dados do provedor.
  return { modelo, marca, ano };
}

export async function consultarPlaca(
  banco: Banco, placa: string, opcoes: { webExperimental?: boolean } = {},
): Promise<DadosVeiculo> {
  const normalizada = normalizarPlaca(placa);

  const consultaCache = await banco.execute({
    sql: `SELECT modelo, marca, ano FROM cache_consultas_placa
          WHERE placa = ? AND consultado_em >= ?`,
    args: [normalizada, Date.now() - VALIDADE_CACHE_MS],
  });
  const salvo = consultaCache.rows[0];
  if (salvo) {
    return {
      modelo: String(salvo.modelo),
      marca: String(salvo.marca),
      ano: String(salvo.ano),
    };
  }

  let pendentes = consultasPendentes.get(banco);
  if (!pendentes) {
    pendentes = new Map();
    consultasPendentes.set(banco, pendentes);
  }
  const anterior = pendentes.get(normalizada);
  if (anterior) return anterior;

  const novaConsulta = (async () => {
    // Somente após homologação/autorização: PoC das fontes HTML antes da Parallelum.
    const dados = (opcoes.webExperimental ? await buscarVeiculoEmPaginas(normalizada) : null)
      ?? await consultarParallelum(normalizada);
    // Uma falha no cache não deve descartar um resultado obtido com sucesso.
    try {
      await banco.execute({
        sql: `INSERT INTO cache_consultas_placa (placa, modelo, marca, ano, consultado_em)
              VALUES (?, ?, ?, ?, ?)
              ON CONFLICT(placa) DO UPDATE SET
                modelo = excluded.modelo,
                marca = excluded.marca,
                ano = excluded.ano,
                consultado_em = excluded.consultado_em`,
        args: [normalizada, dados.modelo, dados.marca, dados.ano, Date.now()],
      });
    } catch {
      // Consulta continua disponível; a próxima tentativa poderá renovar o cache.
    }
    return dados;
  })();
  pendentes.set(normalizada, novaConsulta);
  try {
    return await novaConsulta;
  } finally {
    pendentes.delete(normalizada);
  }
}
