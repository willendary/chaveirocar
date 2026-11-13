import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import type { DadosAtendimento } from "../esquemas.js";
import { normalizar, texto, inteiro, booleano, pagina, tamanhoPagina } from "../util/texto.js";
import { normalizarPlaca, novoId } from "../util/id.js";
import { registrarMudanca, type ExecutorSql, type TipoMudanca } from "../dominio/comum.js";

const TIPOS = new Set([
  "CHAVE_CODIFICADA",
  "TRANSPONDER",
  "TELECOMANDO",
  "ABERTURA",
  "IGNICAO",
  "CONSERTO",
  "PROGRAMACAO_CHAVE",
  "OUTRO",
]);

export interface Contexto {
  empresaId: string;
  usuarioId: string;
}

export interface EntradaAtendimento extends DadosAtendimento {
  id?: string;
  baseRevisao?: number;
}

export interface Atendimento {
  id: string;
  empresaId: string;
  placa: string;
  marca: string;
  modelo: string;
  ano: string;
  chassi: string;
  dataServico: number;
  tipoServico: string;
  tiposServico: string[];
  defeito: string;
  solucao: string;
  abertura: string;
  codigoChave: string;
  codigoConfiguracao: string;
  chipTransponder: string;
  ferramenta: string;
  valorCentavos: number;
  clienteNome: string;
  clienteTelefone: string;
  pago: boolean;
  revisao: number;
  criadoEm: number;
  atualizadoEm: number;
  excluidoEm: number | null;
  criadoPor: string | null;
  atualizadoPor: string | null;
  quantidadeFotos: number;
}

interface LinhaAtendimento {
  id: string;
  empresa_id: string;
  placa: string;
  marca: string;
  modelo: string;
  ano: string;
  chassi: string;
  data_servico: number;
  tipos_servico: string;
  defeito: string;
  solucao: string;
  abertura: string;
  codigo_chave: string;
  codigo_configuracao: string;
  chip_transponder: string;
  ferramenta: string;
  valor_centavos: number;
  cliente_nome: string;
  cliente_telefone: string;
  pago: number;
  revisao: number;
  criado_em: number;
  atualizado_em: number;
  excluido_em: number | null;
  criado_por: string | null;
  atualizado_por: string | null;
  quantidade_fotos: number;
}

const CAMPOS = `id, empresa_id, placa, marca, modelo, ano, chassi, data_servico, tipos_servico,
  defeito, solucao, abertura, codigo_chave, codigo_configuracao, chip_transponder, ferramenta,
  valor_centavos, cliente_nome, cliente_telefone, pago, revisao, criado_em, atualizado_em,
  excluido_em, criado_por, atualizado_por,
  (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id AND f.excluido_em IS NULL) AS quantidade_fotos`;

export function paraAtendimento(linha: LinhaAtendimento): Atendimento {
  let tipos: string[] = [];
  try {
    const bruto = JSON.parse(linha.tipos_servico || "[]") as unknown;
    if (Array.isArray(bruto)) {
      tipos = bruto.filter((item): item is string => typeof item === "string" && TIPOS.has(item));
    }
  } catch {
    tipos = [];
  }
  if (tipos.length === 0) tipos = ["OUTRO"];
  return {
    id: linha.id,
    empresaId: linha.empresa_id,
    placa: linha.placa,
    marca: linha.marca,
    modelo: linha.modelo,
    ano: linha.ano,
    chassi: linha.chassi,
    dataServico: Number(linha.data_servico),
    tipoServico: tipos[0]!,
    tiposServico: tipos,
    defeito: linha.defeito,
    solucao: linha.solucao,
    abertura: linha.abertura,
    codigoChave: linha.codigo_chave,
    codigoConfiguracao: linha.codigo_configuracao,
    chipTransponder: linha.chip_transponder,
    ferramenta: linha.ferramenta,
    valorCentavos: Number(linha.valor_centavos),
    clienteNome: linha.cliente_nome,
    clienteTelefone: linha.cliente_telefone,
    pago: Number(linha.pago) !== 0,
    revisao: Number(linha.revisao),
    criadoEm: Number(linha.criado_em),
    atualizadoEm: Number(linha.atualizado_em),
    excluidoEm: linha.excluido_em === null ? null : Number(linha.excluido_em),
    criadoPor: linha.criado_por,
    atualizadoPor: linha.atualizado_por,
    quantidadeFotos: Number(linha.quantidade_fotos ?? 0),
  };
}

function validarTipos(valor: unknown): string[] {
  if (!Array.isArray(valor) || valor.length === 0) return ["OUTRO"];
  const vistos = new Set<string>();
  for (const item of valor) {
    if (typeof item === "string" && TIPOS.has(item)) vistos.add(item);
  }
  return vistos.size > 0 ? [...vistos] : ["OUTRO"];
}

export function normalizarAtendimento(entrada: EntradaAtendimento) {
  const tipos = validarTipos(entrada.tiposServico);
  const placa = normalizarPlaca(texto(entrada.placa, 10));
  if (!placa) throw erros.validacao("Informe a placa do veículo.");
  const clienteNome = texto(entrada.clienteNome, 200);
  const clienteTelefone = texto(entrada.clienteTelefone, 60);
  const marca = texto(entrada.marca, 120);
  const modelo = texto(entrada.modelo, 120);
  const chassi = texto(entrada.chassi, 60);
  const codigoChave = texto(entrada.codigoChave, 500);
  const chipTransponder = texto(entrada.chipTransponder, 200);
  const busca = normalizar(
    [placa, marca, modelo, chassi, clienteNome, clienteTelefone, codigoChave, chipTransponder].join(" "),
  );
  return {
    placa,
    placaBusca: placa,
    marca,
    modelo,
    ano: texto(entrada.ano, 30),
    chassi,
    dataServico: inteiro(entrada.dataServico, agora()),
    tipoServico: tipos[0]!,
    tiposServico: JSON.stringify(tipos),
    defeito: texto(entrada.defeito, 20_000),
    solucao: texto(entrada.solucao, 20_000),
    abertura: texto(entrada.abertura, 20_000),
    codigoChave,
    codigoConfiguracao: texto(entrada.codigoConfiguracao, 500),
    chipTransponder,
    ferramenta: texto(entrada.ferramenta, 500),
    valorCentavos: Math.max(0, inteiro(entrada.valorCentavos, 0)),
    clienteNome,
    clienteTelefone,
    pago: booleano(entrada.pago, true) ? 1 : 0,
    busca,
  };
}

export async function listarAtendimentos(
  banco: Banco,
  empresaId: string,
  filtros: {
    busca?: string;
    de?: number;
    ate?: number;
    pagina?: number;
    tamanho?: number;
    incluirExcluidos?: boolean;
  } = {},
): Promise<{ itens: Atendimento[]; total: number; pagina: number; tamanho: number }> {
  const numeroPagina = pagina(filtros.pagina, 1);
  const limite = tamanhoPagina(filtros.tamanho, 30);
  const condicoes = ["a.empresa_id = ?"];
  const argumentos: Array<string | number> = [empresaId];
  if (!filtros.incluirExcluidos) condicoes.push("a.excluido_em IS NULL");
  const termos = normalizar(filtros.busca).split(" ").filter(Boolean);
  for (const termo of termos) {
    condicoes.push("a.busca LIKE ?");
    argumentos.push(`%${termo}%`);
  }
  if (Number.isFinite(filtros.de)) {
    condicoes.push("a.data_servico >= ?");
    argumentos.push(Number(filtros.de));
  }
  if (Number.isFinite(filtros.ate)) {
    condicoes.push("a.data_servico <= ?");
    argumentos.push(Number(filtros.ate));
  }
  const onde = condicoes.join(" AND ");

  const total = await banco.execute({
    sql: `SELECT COUNT(*) AS total FROM atendimentos a WHERE ${onde}`,
    args: argumentos,
  });
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM atendimentos a
          WHERE ${onde}
          ORDER BY a.data_servico DESC, a.criado_em DESC
          LIMIT ? OFFSET ?`,
    args: [...argumentos, limite, (numeroPagina - 1) * limite],
  });
  return {
    itens: resultado.rows.map((linha) => paraAtendimento(linha as unknown as LinhaAtendimento)),
    total: Number(total.rows[0]?.total ?? 0),
    pagina: numeroPagina,
    tamanho: limite,
  };
}

export async function obterAtendimento(
  banco: Banco,
  empresaId: string,
  id: string,
  incluirExcluido = false,
): Promise<Atendimento> {
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM atendimentos a WHERE a.id = ? AND a.empresa_id = ?${
      incluirExcluido ? "" : " AND a.excluido_em IS NULL"
    }`,
    args: [id, empresaId],
  });
  const linha = resultado.rows[0] as unknown as LinhaAtendimento | undefined;
  if (!linha) throw erros.naoEncontrado("Atendimento não encontrado.");
  return paraAtendimento(linha);
}

export async function salvarAtendimento(
  banco: Banco,
  contexto: Contexto,
  entrada: EntradaAtendimento,
): Promise<Atendimento> {
  const dados = normalizarAtendimento(entrada);
  const instante = agora();
  const id = entrada.id?.trim() || novoId();
  const existente = await banco.execute({
    sql: "SELECT id, revisao, excluido_em FROM atendimentos WHERE id = ? AND empresa_id = ?",
    args: [id, contexto.empresaId],
  });
  const linhaExistente = existente.rows[0];
  const registroAtual = linhaExistente
    ? await obterAtendimento(banco, contexto.empresaId, id, true)
    : null;

  const transacao = await banco.transaction("write");
  try {
    if (!linhaExistente) {
      await transacao.execute({
        sql: `INSERT INTO atendimentos (
            id, empresa_id, placa, placa_busca, marca, modelo, ano, chassi, data_servico,
            tipo_servico, tipos_servico, defeito, solucao, abertura, codigo_chave,
            codigo_configuracao, chip_transponder, ferramenta, valor_centavos, cliente_nome,
            cliente_telefone, pago, busca, revisao, criado_em, atualizado_em, criado_por, atualizado_por
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)`,
        args: [
          id,
          contexto.empresaId,
          dados.placa,
          dados.placaBusca,
          dados.marca,
          dados.modelo,
          dados.ano,
          dados.chassi,
          dados.dataServico,
          dados.tipoServico,
          dados.tiposServico,
          dados.defeito,
          dados.solucao,
          dados.abertura,
          dados.codigoChave,
          dados.codigoConfiguracao,
          dados.chipTransponder,
          dados.ferramenta,
          dados.valorCentavos,
          dados.clienteNome,
          dados.clienteTelefone,
          dados.pago,
          dados.busca,
          instante,
          instante,
          contexto.usuarioId,
          contexto.usuarioId,
        ],
      });
      await registrarMudanca(transacao, contexto.empresaId, "atendimento", id, 1, "upsert");
    } else {
      const revisaoAtual = Number(linhaExistente.revisao);
      if (Number(linhaExistente.excluido_em ?? 0) > 0) {
        throw erros.conflito("Este atendimento foi excluído.", { registro: registroAtual });
      }
      if (entrada.baseRevisao === undefined) {
        throw erros.conflito("Informe a revisão do atendimento para editar.", {
          registro: registroAtual,
        });
      }
      if (Number(entrada.baseRevisao) !== revisaoAtual) {
        throw erros.conflito("O atendimento foi alterado por outra pessoa.", {
          registro: registroAtual,
        });
      }
      const atualizacao = await transacao.execute({
        sql: `UPDATE atendimentos SET
            placa = ?, placa_busca = ?, marca = ?, modelo = ?, ano = ?, chassi = ?, data_servico = ?,
            tipo_servico = ?, tipos_servico = ?, defeito = ?, solucao = ?, abertura = ?,
            codigo_chave = ?, codigo_configuracao = ?, chip_transponder = ?, ferramenta = ?,
            valor_centavos = ?, cliente_nome = ?, cliente_telefone = ?, pago = ?, busca = ?,
            revisao = revisao + 1, atualizado_em = ?, atualizado_por = ?
          WHERE id = ? AND empresa_id = ? AND revisao = ?`,
        args: [
          dados.placa,
          dados.placaBusca,
          dados.marca,
          dados.modelo,
          dados.ano,
          dados.chassi,
          dados.dataServico,
          dados.tipoServico,
          dados.tiposServico,
          dados.defeito,
          dados.solucao,
          dados.abertura,
          dados.codigoChave,
          dados.codigoConfiguracao,
          dados.chipTransponder,
          dados.ferramenta,
          dados.valorCentavos,
          dados.clienteNome,
          dados.clienteTelefone,
          dados.pago,
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
        throw erros.conflito("O atendimento foi alterado por outra pessoa.", {
          registro: await obterAtendimento(banco, contexto.empresaId, id),
        });
      }
      await registrarMudanca(transacao, contexto.empresaId, "atendimento", id, revisaoAtual + 1, "upsert");
    }
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterAtendimento(banco, contexto.empresaId, id);
}

async function marcarFotosDoAtendimentoExcluidas(
  executor: ExecutorSql,
  empresaId: string,
  atendimentoId: string,
  instante: number,
): Promise<void> {
  const fotos = await executor.execute({
    sql: "SELECT id, revisao FROM fotos WHERE empresa_id = ? AND atendimento_id = ? AND excluido_em IS NULL",
    args: [empresaId, atendimentoId],
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

export async function excluirAtendimento(
  banco: Banco,
  contexto: Contexto,
  id: string,
  baseRevisao: number,
): Promise<Atendimento> {
  const atual = await obterAtendimento(banco, contexto.empresaId, id, true);
  if (atual.excluidoEm !== null) {
    if (atual.revisao === baseRevisao) return atual;
    throw erros.conflito("O atendimento já foi excluído.", { registro: atual });
  }
  if (atual.revisao !== baseRevisao) {
    throw erros.conflito("O atendimento foi alterado por outra pessoa.", { registro: atual });
  }
  const instante = agora();
  const novaRevisao = atual.revisao + 1;
  const transacao = await banco.transaction("write");
  try {
    const resultado = await transacao.execute({
      sql: `UPDATE atendimentos SET excluido_em = ?, revisao = ?, atualizado_em = ?, atualizado_por = ?
            WHERE id = ? AND empresa_id = ? AND revisao = ? AND excluido_em IS NULL`,
      args: [instante, novaRevisao, instante, contexto.usuarioId, id, contexto.empresaId, baseRevisao],
    });
    if (resultado.rowsAffected !== 1) {
      await transacao.rollback();
      throw erros.conflito("O atendimento foi alterado por outra pessoa.", {
        registro: await obterAtendimento(banco, contexto.empresaId, id, true),
      });
    }
    await marcarFotosDoAtendimentoExcluidas(transacao, contexto.empresaId, id, instante);
    await registrarMudanca(transacao, contexto.empresaId, "atendimento", id, novaRevisao, "exclusao");
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterAtendimento(banco, contexto.empresaId, id, true);
}

export type { TipoMudanca };
