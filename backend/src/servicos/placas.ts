import type { Banco } from "../banco.js";
import { ErroApi, erros } from "../erros.js";
import { consultarVeiculoEmPaginas } from "./placasWeb.js";

interface DadosVeiculo {
  modelo: string;
  marca: string;
  ano: string;
}

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

export async function consultarPlaca(
  banco: Banco,
  placa: string,
  opcoes: { consultaWebAtiva?: boolean } = {},
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

  if (opcoes.consultaWebAtiva === false) {
    throw new ErroApi(
      503,
      "INDISPONIVEL",
      "Consulta automática por placa desativada no momento. Informe o modelo manualmente.",
    );
  }

  let pendentes = consultasPendentes.get(banco);
  if (!pendentes) {
    pendentes = new Map();
    consultasPendentes.set(banco, pendentes);
  }
  const anterior = pendentes.get(normalizada);
  if (anterior) return anterior;

  const novaConsulta = (async () => {
    const resultado = await consultarVeiculoEmPaginas(normalizada);
    if (resultado.status === "nao_encontrado") {
      throw erros.naoEncontrado("Placa não localizada. Informe o modelo manualmente.");
    }
    if (resultado.status === "indisponivel") {
      throw new ErroApi(
        503,
        "INDISPONIVEL",
        "Consulta por placa indisponível no momento. Informe o modelo manualmente.",
      );
    }

    const { dados } = resultado;
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
