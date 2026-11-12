import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import type { DadosFicha } from "../esquemas.js";
import { normalizar, texto, booleano, pagina, tamanhoPagina } from "../util/texto.js";
import { novoId } from "../util/id.js";
import { registrarMudanca, type ExecutorSql } from "../dominio/comum.js";

export interface EntradaFicha extends DadosFicha {
  id?: string;
  baseRevisao?: number;
}

export interface Ficha {
  id: string;
  empresaId: string;
  equipamento: string;
  marca: string;
  modelo: string;
  ano: string;
  sistema: string;
  descricao: string;
  chip: string;
  chave: string;
  telecomando: string;
  frequencia: string;
  avisos: string;
  funcoes: string;
  manual: string;
  confirmado: boolean;
  origem: string;
  fotoId: string | null;
  revisao: number;
  criadoEm: number;
  atualizadoEm: number;
  excluidoEm: number | null;
  criadoPor: string | null;
  atualizadoPor: string | null;
}

interface LinhaFicha {
  id: string;
  empresa_id: string;
  equipamento: string;
  marca: string;
  modelo: string;
  ano: string;
  sistema: string;
  descricao: string;
  chip: string;
  chave: string;
  telecomando: string;
  frequencia: string;
  avisos: string;
  funcoes: string;
  manual: string;
  confirmado: number;
  origem: string;
  foto_id: string | null;
  revisao: number;
  criado_em: number;
  atualizado_em: number;
  excluido_em: number | null;
  criado_por: string | null;
  atualizado_por: string | null;
}

const CAMPOS = `id, empresa_id, equipamento, marca, modelo, ano, sistema, descricao, chip, chave,
  telecomando, frequencia, avisos, funcoes, manual, confirmado, origem, revisao, criado_em,
  atualizado_em, excluido_em, criado_por, atualizado_por,
  (SELECT f.id FROM fotos f WHERE f.ficha_id = fichas_tecnicas.id AND f.excluido_em IS NULL
     ORDER BY f.ordem, f.criado_em LIMIT 1) AS foto_id`;

export function paraFicha(linha: LinhaFicha): Ficha {
  return {
    id: linha.id,
    empresaId: linha.empresa_id,
    equipamento: linha.equipamento,
    marca: linha.marca,
    modelo: linha.modelo,
    ano: linha.ano,
    sistema: linha.sistema,
    descricao: linha.descricao,
    chip: linha.chip,
    chave: linha.chave,
    telecomando: linha.telecomando,
    frequencia: linha.frequencia,
    avisos: linha.avisos,
    funcoes: linha.funcoes,
    manual: linha.manual,
    confirmado: Number(linha.confirmado) !== 0,
    origem: linha.origem,
    fotoId: linha.foto_id,
    revisao: Number(linha.revisao),
    criadoEm: Number(linha.criado_em),
    atualizadoEm: Number(linha.atualizado_em),
    excluidoEm: linha.excluido_em === null ? null : Number(linha.excluido_em),
    criadoPor: linha.criado_por,
    atualizadoPor: linha.atualizado_por,
  };
}

export function normalizarFicha(entrada: EntradaFicha) {
  const equipamento = texto(entrada.equipamento, 120);
  const marca = texto(entrada.marca, 120);
  const modelo = texto(entrada.modelo, 120);
  const sistema = texto(entrada.sistema, 200);
  const descricao = texto(entrada.descricao, 20_000);
  const chip = texto(entrada.chip, 200);
  const chave = texto(entrada.chave, 500);
  const telecomando = texto(entrada.telecomando, 500);
  const frequencia = texto(entrada.frequencia, 100);
  const avisos = texto(entrada.avisos, 20_000);
  const funcoes = texto(entrada.funcoes, 20_000);
  if (!equipamento && !marca && !modelo && !sistema && !descricao) {
    throw erros.validacao("Informe ao menos equipamento, veículo ou descrição da ficha.");
  }
  return {
    equipamento,
    marca,
    modelo,
    ano: texto(entrada.ano, 30),
    sistema,
    descricao,
    chip,
    chave,
    telecomando,
    frequencia,
    avisos,
    funcoes,
    manual: texto(entrada.manual, 100_000),
    confirmado: booleano(entrada.confirmado, false) ? 1 : 0,
    origem: texto(entrada.origem, 500),
    busca: normalizar(
      [equipamento, marca, modelo, sistema, chip, chave, telecomando, frequencia, descricao, avisos, funcoes].join(" "),
    ),
  };
}

export async function listarFichas(
  banco: Banco,
  empresaId: string,
  filtros: { busca?: string; pagina?: number; tamanho?: number; incluirExcluidos?: boolean } = {},
): Promise<{ itens: Ficha[]; total: number; pagina: number; tamanho: number }> {
  const numeroPagina = pagina(filtros.pagina, 1);
  const limite = tamanhoPagina(filtros.tamanho, 30);
  const condicoes = ["empresa_id = ?"];
  const argumentos: Array<string | number> = [empresaId];
  if (!filtros.incluirExcluidos) condicoes.push("excluido_em IS NULL");
  for (const termo of normalizar(filtros.busca).split(" ").filter(Boolean)) {
    condicoes.push("busca LIKE ?");
    argumentos.push(`%${termo}%`);
  }
  const onde = condicoes.join(" AND ");
  const total = await banco.execute({
    sql: `SELECT COUNT(*) AS total FROM fichas_tecnicas WHERE ${onde}`,
    args: argumentos,
  });
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM fichas_tecnicas WHERE ${onde}
          ORDER BY atualizado_em DESC LIMIT ? OFFSET ?`,
    args: [...argumentos, limite, (numeroPagina - 1) * limite],
  });
  return {
    itens: resultado.rows.map((linha) => paraFicha(linha as unknown as LinhaFicha)),
    total: Number(total.rows[0]?.total ?? 0),
    pagina: numeroPagina,
    tamanho: limite,
  };
}

export async function obterFicha(
  banco: Banco,
  empresaId: string,
  id: string,
  incluirExcluida = false,
): Promise<Ficha> {
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM fichas_tecnicas WHERE id = ? AND empresa_id = ?${
      incluirExcluida ? "" : " AND excluido_em IS NULL"
    }`,
    args: [id, empresaId],
  });
  const linha = resultado.rows[0] as unknown as LinhaFicha | undefined;
  if (!linha) throw erros.naoEncontrado("Ficha técnica não encontrada.");
  return paraFicha(linha);
}

export async function salvarFicha(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  entrada: EntradaFicha,
): Promise<Ficha> {
  const dados = normalizarFicha(entrada);
  const instante = agora();
  const id = entrada.id?.trim() || novoId();
  const existente = await banco.execute({
    sql: "SELECT id, revisao, excluido_em FROM fichas_tecnicas WHERE id = ? AND empresa_id = ?",
    args: [id, contexto.empresaId],
  });
  const linhaExistente = existente.rows[0];
  const registroAtual = linhaExistente
    ? await obterFicha(banco, contexto.empresaId, id, true)
    : null;

  const transacao = await banco.transaction("write");
  try {
    if (!linhaExistente) {
      await transacao.execute({
        sql: `INSERT INTO fichas_tecnicas (
            id, empresa_id, equipamento, marca, modelo, ano, sistema, descricao, chip, chave,
            telecomando, frequencia, avisos, funcoes, manual, confirmado, origem, busca,
            revisao, criado_em, atualizado_em, criado_por, atualizado_por
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)`,
        args: [
          id,
          contexto.empresaId,
          dados.equipamento,
          dados.marca,
          dados.modelo,
          dados.ano,
          dados.sistema,
          dados.descricao,
          dados.chip,
          dados.chave,
          dados.telecomando,
          dados.frequencia,
          dados.avisos,
          dados.funcoes,
          dados.manual,
          dados.confirmado,
          dados.origem,
          dados.busca,
          instante,
          instante,
          contexto.usuarioId,
          contexto.usuarioId,
        ],
      });
      await registrarMudanca(transacao, contexto.empresaId, "ficha", id, 1, "upsert");
    } else {
      const revisaoAtual = Number(linhaExistente.revisao);
      if (Number(linhaExistente.excluido_em ?? 0) > 0) {
        throw erros.conflito("Esta ficha foi excluída.", { registro: registroAtual });
      }
      if (entrada.baseRevisao === undefined || Number(entrada.baseRevisao) !== revisaoAtual) {
        throw erros.conflito("A ficha foi alterada por outra pessoa.", { registro: registroAtual });
      }
      const atualizacao = await transacao.execute({
        sql: `UPDATE fichas_tecnicas SET
            equipamento = ?, marca = ?, modelo = ?, ano = ?, sistema = ?, descricao = ?, chip = ?,
            chave = ?, telecomando = ?, frequencia = ?, avisos = ?, funcoes = ?, manual = ?,
            confirmado = ?, origem = ?, busca = ?, revisao = revisao + 1, atualizado_em = ?,
            atualizado_por = ?
          WHERE id = ? AND empresa_id = ? AND revisao = ?`,
        args: [
          dados.equipamento,
          dados.marca,
          dados.modelo,
          dados.ano,
          dados.sistema,
          dados.descricao,
          dados.chip,
          dados.chave,
          dados.telecomando,
          dados.frequencia,
          dados.avisos,
          dados.funcoes,
          dados.manual,
          dados.confirmado,
          dados.origem,
          dados.busca,
          instante,
          contexto.usuarioId,
          id,
          contexto.empresaId,
          revisaoAtual,
        ],
      });
      if (atualizacao.rowsAffected !== 1) {
        await transacao.rollback();
        throw erros.conflito("A ficha foi alterada por outra pessoa.", {
          registro: await obterFicha(banco, contexto.empresaId, id),
        });
      }
      await registrarMudanca(transacao, contexto.empresaId, "ficha", id, revisaoAtual + 1, "upsert");
    }
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterFicha(banco, contexto.empresaId, id);
}

async function marcarFotosDaFichaExcluidas(
  executor: ExecutorSql,
  empresaId: string,
  fichaId: string,
  instante: number,
): Promise<void> {
  const fotos = await executor.execute({
    sql: "SELECT id, revisao FROM fotos WHERE empresa_id = ? AND ficha_id = ? AND excluido_em IS NULL",
    args: [empresaId, fichaId],
  });
  for (const foto of fotos.rows) {
    const revisao = Number(foto.revisao) + 1;
    await executor.execute({
      sql: "UPDATE fotos SET excluido_em = ?, revisao = ?, atualizado_em = ? WHERE id = ?",
      args: [instante, revisao, instante, String(foto.id)],
    });
    await registrarMudanca(executor, empresaId, "foto", String(foto.id), revisao, "exclusao");
  }
}

export async function excluirFicha(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  id: string,
  baseRevisao: number,
): Promise<Ficha> {
  const atual = await obterFicha(banco, contexto.empresaId, id, true);
  if (atual.excluidoEm !== null) {
    if (atual.revisao === baseRevisao) return atual;
    throw erros.conflito("A ficha já foi excluída.", { registro: atual });
  }
  if (atual.revisao !== baseRevisao) {
    throw erros.conflito("A ficha foi alterada por outra pessoa.", { registro: atual });
  }
  const instante = agora();
  const novaRevisao = atual.revisao + 1;
  const transacao = await banco.transaction("write");
  try {
    const resultado = await transacao.execute({
      sql: `UPDATE fichas_tecnicas SET excluido_em = ?, revisao = ?, atualizado_em = ?, atualizado_por = ?
            WHERE id = ? AND empresa_id = ? AND revisao = ? AND excluido_em IS NULL`,
      args: [instante, novaRevisao, instante, contexto.usuarioId, id, contexto.empresaId, baseRevisao],
    });
    if (resultado.rowsAffected !== 1) {
      await transacao.rollback();
      throw erros.conflito("A ficha foi alterada por outra pessoa.", {
        registro: await obterFicha(banco, contexto.empresaId, id, true),
      });
    }
    await marcarFotosDaFichaExcluidas(transacao, contexto.empresaId, id, instante);
    await registrarMudanca(transacao, contexto.empresaId, "ficha", id, novaRevisao, "exclusao");
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterFicha(banco, contexto.empresaId, id, true);
}
