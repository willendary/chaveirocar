import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import type { Armazenamento } from "../storage.js";
import { erros } from "../erros.js";
import { novoId, sha256Bytes } from "../util/id.js";
import { texto, inteiro } from "../util/texto.js";
import { registrarMudanca } from "../dominio/comum.js";

export type CategoriaFoto = "CARRO" | "PROCEDIMENTO";

export interface Foto {
  id: string;
  empresaId: string;
  atendimentoId: string | null;
  fichaId: string | null;
  categoria: CategoriaFoto;
  legenda: string;
  ordem: number;
  mimeType: string;
  tamanho: number;
  checksum: string;
  url: string;
  revisao: number;
  criadoEm: number;
  atualizadoEm: number;
  excluidoEm: number | null;
}

interface LinhaFoto {
  id: string;
  empresa_id: string;
  atendimento_id: string | null;
  ficha_id: string | null;
  categoria: string;
  legenda: string;
  ordem: number;
  storage_key: string;
  mime_type: string;
  tamanho: number;
  checksum: string;
  revisao: number;
  criado_em: number;
  atualizado_em: number;
  excluido_em: number | null;
}

const CAMPOS = `id, empresa_id, atendimento_id, ficha_id, categoria, legenda, ordem, storage_key,
  mime_type, tamanho, checksum, revisao, criado_em, atualizado_em, excluido_em`;

function urlFoto(empresaId: string, fotoId: string): string {
  return `/api/v1/empresas/${empresaId}/fotos/${fotoId}/arquivo`;
}

export function paraFoto(linha: LinhaFoto): Foto {
  return {
    id: linha.id,
    empresaId: linha.empresa_id,
    atendimentoId: linha.atendimento_id,
    fichaId: linha.ficha_id,
    categoria: linha.categoria === "PROCEDIMENTO" ? "PROCEDIMENTO" : "CARRO",
    legenda: linha.legenda,
    ordem: Number(linha.ordem),
    mimeType: linha.mime_type,
    tamanho: Number(linha.tamanho),
    checksum: linha.checksum,
    url: urlFoto(linha.empresa_id, linha.id),
    revisao: Number(linha.revisao),
    criadoEm: Number(linha.criado_em),
    atualizadoEm: Number(linha.atualizado_em),
    excluidoEm: linha.excluido_em === null ? null : Number(linha.excluido_em),
  };
}

export function detectarImagem(bytes: Buffer): { mime: string; extensao: string } | null {
  if (bytes.length < 12) return null;
  if (bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) {
    return { mime: "image/jpeg", extensao: "jpg" };
  }
  if (
    bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47 &&
    bytes[4] === 0x0d && bytes[5] === 0x0a && bytes[6] === 0x1a && bytes[7] === 0x0a
  ) {
    return { mime: "image/png", extensao: "png" };
  }
  if (
    bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
    bytes.subarray(8, 12).toString("ascii") === "WEBP"
  ) {
    return { mime: "image/webp", extensao: "webp" };
  }
  if (bytes.subarray(4, 8).toString("ascii") === "ftyp") {
    const marca = bytes.subarray(8, 12).toString("ascii");
    if (["heic", "heix", "hevc", "mif1", "msf1"].includes(marca)) {
      return { mime: "image/heic", extensao: "heic" };
    }
  }
  return null;
}

async function garantirAlvo(
  banco: Banco,
  empresaId: string,
  atendimentoId: string | null,
  fichaId: string | null,
): Promise<void> {
  if (Boolean(atendimentoId) === Boolean(fichaId)) {
    throw erros.validacao("A foto deve pertencer a um atendimento ou a uma ficha, nunca aos dois.");
  }
  const tabela = atendimentoId ? "atendimentos" : "fichas_tecnicas";
  const id = atendimentoId ?? fichaId!;
  const resultado = await banco.execute({
    sql: `SELECT id FROM ${tabela} WHERE id = ? AND empresa_id = ? AND excluido_em IS NULL`,
    args: [id, empresaId],
  });
  if (!resultado.rows[0]) throw erros.naoEncontrado("Registro da foto não encontrado.");
}

export async function adicionarFoto(
  banco: Banco,
  storage: Armazenamento,
  contexto: { empresaId: string; usuarioId: string },
  entrada: {
    id?: string;
    atendimentoId?: string | null;
    fichaId?: string | null;
    categoria?: string;
    legenda?: string;
    ordem?: number;
    bytes: Buffer;
    mimeInformado?: string;
  },
  limitePorRegistro: number,
): Promise<Foto> {
  const atendimentoId = entrada.atendimentoId?.trim() || null;
  const fichaId = entrada.fichaId?.trim() || null;
  await garantirAlvo(banco, contexto.empresaId, atendimentoId, fichaId);

  const total = await banco.execute({
    sql: `SELECT COUNT(*) AS total FROM fotos
          WHERE empresa_id = ? AND excluido_em IS NULL
            AND ((atendimento_id IS NOT NULL AND atendimento_id = ?) OR (ficha_id IS NOT NULL AND ficha_id = ?))`,
    args: [contexto.empresaId, atendimentoId, fichaId],
  });
  if (Number(total.rows[0]?.total ?? 0) >= limitePorRegistro) {
    throw erros.limite(`Cada registro aceita no máximo ${limitePorRegistro} fotos.`);
  }

  const imagem = detectarImagem(entrada.bytes);
  if (!imagem) {
    throw erros.validacao("Envie uma imagem JPEG, PNG, WebP ou HEIC.");
  }
  if (entrada.mimeInformado && entrada.mimeInformado !== imagem.mime) {
    throw erros.validacao("O tipo informado não confere com o conteúdo do arquivo.");
  }

  const id = entrada.id?.trim() || novoId();
  const checksum = sha256Bytes(entrada.bytes);
  const objeto = atendimentoId ? `atendimentos/${atendimentoId}` : `fichas/${fichaId}`;
  const chave = `${contexto.empresaId}/${objeto}/${id}.${imagem.extensao}`;
  const instante = agora();
  const categoria: CategoriaFoto = entrada.categoria === "PROCEDIMENTO" ? "PROCEDIMENTO" : "CARRO";

  await storage.salvar(chave, entrada.bytes, imagem.mime);
  const transacao = await banco.transaction("write");
  try {
    const conflito = await transacao.execute({
      sql: "SELECT id FROM fotos WHERE id = ?",
      args: [id],
    });
    if (conflito.rows[0]) throw erros.conflito("Já existe uma foto com este identificador.");
    await transacao.execute({
      sql: `INSERT INTO fotos (
          id, empresa_id, atendimento_id, ficha_id, categoria, legenda, ordem, storage_key,
          mime_type, tamanho, checksum, revisao, criado_em, atualizado_em, criado_por
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)`,
      args: [
        id,
        contexto.empresaId,
        atendimentoId,
        fichaId,
        categoria,
        texto(entrada.legenda, 500),
        inteiro(entrada.ordem, 0),
        chave,
        imagem.mime,
        entrada.bytes.length,
        checksum,
        instante,
        instante,
        contexto.usuarioId,
      ],
    });
    await registrarMudanca(transacao, contexto.empresaId, "foto", id, 1, "upsert");
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    await storage.remover(chave).catch(() => undefined);
    throw erro;
  }
  return obterFoto(banco, contexto.empresaId, id);
}

export async function obterFoto(
  banco: Banco,
  empresaId: string,
  id: string,
  incluirExcluida = false,
): Promise<Foto> {
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM fotos WHERE id = ? AND empresa_id = ?${
      incluirExcluida ? "" : " AND excluido_em IS NULL"
    }`,
    args: [id, empresaId],
  });
  const linha = resultado.rows[0] as unknown as LinhaFoto | undefined;
  if (!linha) throw erros.naoEncontrado("Foto não encontrada.");
  return paraFoto(linha);
}

export async function lerFoto(
  banco: Banco,
  storage: Armazenamento,
  empresaId: string,
  id: string,
): Promise<{ foto: Foto; bytes: Buffer }> {
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM fotos WHERE id = ? AND empresa_id = ? AND excluido_em IS NULL`,
    args: [id, empresaId],
  });
  const linha = resultado.rows[0] as unknown as LinhaFoto | undefined;
  if (!linha) throw erros.naoEncontrado("Foto não encontrada.");
  const bytes = await storage.ler(linha.storage_key);
  return { foto: paraFoto(linha), bytes };
}

export async function listarFotos(
  banco: Banco,
  empresaId: string,
  filtro: { atendimentoId?: string; fichaId?: string },
): Promise<Foto[]> {
  if (Boolean(filtro.atendimentoId) === Boolean(filtro.fichaId)) {
    throw erros.validacao("Informe o atendimento ou a ficha para listar as fotos.");
  }
  const resultado = await banco.execute({
    sql: `SELECT ${CAMPOS} FROM fotos
          WHERE empresa_id = ? AND excluido_em IS NULL AND ${
            filtro.atendimentoId ? "atendimento_id = ?" : "ficha_id = ?"
          }
          ORDER BY ordem, criado_em`,
    args: [empresaId, filtro.atendimentoId ?? filtro.fichaId!],
  });
  return resultado.rows.map((linha) => paraFoto(linha as unknown as LinhaFoto));
}

export async function atualizarFoto(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  id: string,
  entrada: { baseRevisao: number; categoria?: string; legenda?: string; ordem?: number },
): Promise<Foto> {
  const atual = await obterFoto(banco, contexto.empresaId, id);
  if (atual.revisao !== entrada.baseRevisao) {
    throw erros.conflito("A foto foi alterada por outra pessoa.", { registro: atual });
  }
  const instante = agora();
  const transacao = await banco.transaction("write");
  try {
    const resultado = await transacao.execute({
      sql: `UPDATE fotos SET categoria = ?, legenda = ?, ordem = ?, revisao = revisao + 1,
              atualizado_em = ?
            WHERE id = ? AND empresa_id = ? AND revisao = ? AND excluido_em IS NULL`,
      args: [
        entrada.categoria === "PROCEDIMENTO" ? "PROCEDIMENTO" : "CARRO",
        texto(entrada.legenda, 500),
        inteiro(entrada.ordem, 0),
        instante,
        id,
        contexto.empresaId,
        entrada.baseRevisao,
      ],
    });
    if (resultado.rowsAffected !== 1) {
      await transacao.rollback();
      throw erros.conflito("A foto foi alterada por outra pessoa.", {
        registro: await obterFoto(banco, contexto.empresaId, id),
      });
    }
    await registrarMudanca(transacao, contexto.empresaId, "foto", id, entrada.baseRevisao + 1, "upsert");
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterFoto(banco, contexto.empresaId, id);
}

export async function excluirFoto(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  id: string,
  baseRevisao: number,
): Promise<Foto> {
  const atual = await obterFoto(banco, contexto.empresaId, id, true);
  if (atual.excluidoEm !== null) {
    if (atual.revisao === baseRevisao) return atual;
    throw erros.conflito("A foto já foi excluída.", { registro: atual });
  }
  if (atual.revisao !== baseRevisao) {
    throw erros.conflito("A foto foi alterada por outra pessoa.", { registro: atual });
  }
  const instante = agora();
  const novaRevisao = atual.revisao + 1;
  const transacao = await banco.transaction("write");
  try {
    const resultado = await transacao.execute({
      sql: `UPDATE fotos SET excluido_em = ?, revisao = ?, atualizado_em = ?
            WHERE id = ? AND empresa_id = ? AND revisao = ? AND excluido_em IS NULL`,
      args: [instante, novaRevisao, instante, id, contexto.empresaId, baseRevisao],
    });
    if (resultado.rowsAffected !== 1) {
      await transacao.rollback();
      throw erros.conflito("A foto foi alterada por outra pessoa.", {
        registro: await obterFoto(banco, contexto.empresaId, id),
      });
    }
    await registrarMudanca(transacao, contexto.empresaId, "foto", id, novaRevisao, "exclusao");
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return obterFoto(banco, contexto.empresaId, id, true);
}
