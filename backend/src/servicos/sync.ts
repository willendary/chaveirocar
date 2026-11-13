import type { Banco } from "../banco.js";
import { ErroApi, erros } from "../erros.js";
import type { OperacaoSync } from "../esquemas.js";
import {
  obterAtendimento,
  salvarAtendimento,
  excluirAtendimento,
  type EntradaAtendimento,
} from "./atendimentos.js";
import { obterFicha, salvarFicha, excluirFicha, type EntradaFicha } from "./fichas.js";
import { obterFoto, atualizarFoto, excluirFoto } from "./fotos.js";

export interface ResultadoOperacao {
  opId: string;
  status: "aplicado" | "conflito" | "erro";
  revisao?: number;
  registro?: unknown;
  erro?: { codigo: string; mensagem: string };
}

export interface LotePull {
  cursor: number;
  temMais: boolean;
  atendimentos: unknown[];
  fichas: unknown[];
  fotos: unknown[];
}

const LIMITE_PULL = 200;

export async function puxarMudancas(
  banco: Banco,
  empresaId: string,
  cursor: number,
  limite = LIMITE_PULL,
): Promise<LotePull> {
  const resultado = await banco.execute({
    sql: `SELECT id, entidade, entidade_id FROM mudancas
          WHERE empresa_id = ? AND id > ?
          ORDER BY id LIMIT ?`,
    args: [empresaId, cursor, limite + 1],
  });
  const linhas = resultado.rows.slice(0, limite);
  const temMais = resultado.rows.length > limite;
  const ultimoCursor = linhas.length > 0 ? Number(linhas[linhas.length - 1]!.id) : cursor;

  const idsPorEntidade = new Map<string, Set<string>>();
  for (const linha of linhas) {
    const entidade = String(linha.entidade);
    const conjunto = idsPorEntidade.get(entidade) ?? new Set<string>();
    conjunto.add(String(linha.entidade_id));
    idsPorEntidade.set(entidade, conjunto);
  }

  const atendimentos: unknown[] = [];
  const fichas: unknown[] = [];
  const fotos: unknown[] = [];

  const idsAtendimentos = [...(idsPorEntidade.get("atendimento") ?? [])];
  if (idsAtendimentos.length > 0) {
    const resposta = await banco.execute({
      sql: `SELECT id FROM atendimentos WHERE empresa_id = ? AND id IN (${idsAtendimentos.map(() => "?").join(",")})`,
      args: [empresaId, ...idsAtendimentos],
    });
    for (const linha of resposta.rows) {
      atendimentos.push(await obterAtendimento(banco, empresaId, String(linha.id), true));
    }
  }

  const idsFichas = [...(idsPorEntidade.get("ficha") ?? [])];
  if (idsFichas.length > 0) {
    const resposta = await banco.execute({
      sql: `SELECT id FROM fichas_tecnicas WHERE empresa_id = ? AND id IN (${idsFichas.map(() => "?").join(",")})`,
      args: [empresaId, ...idsFichas],
    });
    for (const linha of resposta.rows) {
      fichas.push(await obterFicha(banco, empresaId, String(linha.id), true));
    }
  }

  const idsFotos = [...(idsPorEntidade.get("foto") ?? [])];
  if (idsFotos.length > 0) {
    const resposta = await banco.execute({
      sql: `SELECT id FROM fotos WHERE empresa_id = ? AND id IN (${idsFotos.map(() => "?").join(",")})`,
      args: [empresaId, ...idsFotos],
    });
    for (const linha of resposta.rows) {
      fotos.push(await obterFoto(banco, empresaId, String(linha.id), true));
    }
  }

  return { cursor: ultimoCursor, temMais, atendimentos, fichas, fotos };
}

export async function cursorAtual(banco: Banco, empresaId: string): Promise<number> {
  const resultado = await banco.execute({
    sql: "SELECT COALESCE(MAX(id), 0) AS cursor FROM mudancas WHERE empresa_id = ?",
    args: [empresaId],
  });
  return Number(resultado.rows[0]?.cursor ?? 0);
}

async function resultadoAnterior(
  banco: Banco,
  empresaId: string,
  opId: string,
): Promise<ResultadoOperacao | null> {
  const resultado = await banco.execute({
    sql: "SELECT resultado FROM operacoes_sync WHERE empresa_id = ? AND op_id = ?",
    args: [empresaId, opId],
  });
  const linha = resultado.rows[0];
  if (!linha) return null;
  try {
    return JSON.parse(String(linha.resultado)) as ResultadoOperacao;
  } catch {
    return null;
  }
}

async function guardarResultado(
  banco: Banco,
  empresaId: string,
  opId: string,
  resultado: ResultadoOperacao,
): Promise<void> {
  await banco.execute({
    sql: `INSERT INTO operacoes_sync (empresa_id, op_id, resultado, criado_em)
          VALUES (?, ?, ?, ?)
          ON CONFLICT (empresa_id, op_id) DO UPDATE SET resultado = excluded.resultado`,
    args: [empresaId, opId, JSON.stringify(resultado), Date.now()],
  });
}

function mapearErro(opId: string, erro: unknown): ResultadoOperacao {
  if (erro instanceof ErroApi) {
    const conflito = erro.status === 409;
    const detalhes = erro.detalhes as { registro?: unknown } | undefined;
    return {
      opId,
      status: conflito ? "conflito" : "erro",
      registro: conflito ? detalhes?.registro : undefined,
      erro: { codigo: erro.codigo, mensagem: erro.message },
    };
  }
  return {
    opId,
    status: "erro",
    erro: { codigo: "ERRO_INTERNO", mensagem: "Não foi possível aplicar a operação." },
  };
}

export async function empurrarOperacoes(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  operacoes: OperacaoSync[],
): Promise<{ resultados: ResultadoOperacao[]; cursor: number }> {
  const resultados: ResultadoOperacao[] = [];
  for (const operacao of operacoes) {
    const anterior = await resultadoAnterior(banco, contexto.empresaId, operacao.opId);
    if (anterior) {
      resultados.push(anterior);
      continue;
    }
    let resultado: ResultadoOperacao;
    try {
      resultado = await aplicarOperacao(banco, contexto, operacao);
    } catch (erro) {
      resultado = mapearErro(operacao.opId, erro);
    }
    await guardarResultado(banco, contexto.empresaId, operacao.opId, resultado);
    resultados.push(resultado);
  }
  return { resultados, cursor: await cursorAtual(banco, contexto.empresaId) };
}

async function aplicarOperacao(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  operacao: OperacaoSync,
): Promise<ResultadoOperacao> {
  const baseRevisao = operacao.baseRevisao ?? 1;
  const dados = (operacao.dados ?? {}) as Record<string, unknown>;

  if (operacao.entidade === "atendimento") {
    if (operacao.tipo === "exclusao") {
      const registro = await excluirAtendimento(banco, contexto, operacao.id, baseRevisao);
      return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
    }
    const registro = await salvarAtendimento(banco, contexto, {
      ...(dados as object),
      id: operacao.id,
      baseRevisao,
    } as EntradaAtendimento);
    return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
  }

  if (operacao.entidade === "ficha") {
    if (operacao.tipo === "exclusao") {
      const registro = await excluirFicha(banco, contexto, operacao.id, baseRevisao);
      return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
    }
    const registro = await salvarFicha(banco, contexto, {
      ...(dados as object),
      id: operacao.id,
      baseRevisao,
    } as EntradaFicha);
    return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
  }

  if (operacao.tipo === "exclusao") {
    const registro = await excluirFoto(banco, contexto, operacao.id, baseRevisao);
    return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
  }

  const existente = await obterFoto(banco, contexto.empresaId, operacao.id).catch(() => null);
  if (!existente) {
    throw erros.validacao(
      "Fotos novas devem ser enviadas pelo endpoint de upload antes da sincronização.",
    );
  }
  const registro = await atualizarFoto(banco, contexto, operacao.id, {
    baseRevisao: baseRevisao || existente.revisao,
    categoria: typeof dados.categoria === "string" ? dados.categoria : existente.categoria,
    legenda: typeof dados.legenda === "string" ? dados.legenda : existente.legenda,
    ordem: typeof dados.ordem === "number" ? dados.ordem : existente.ordem,
    anotacoes: typeof dados.anotacoes === "string" ? dados.anotacoes : existente.anotacoes,
  });
  return { opId: operacao.opId, status: "aplicado", revisao: registro.revisao, registro };
}
