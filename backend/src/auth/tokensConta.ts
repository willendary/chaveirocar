import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import { novoId, novoSegredo, sha256 } from "../util/id.js";

const HORA = 60 * 60 * 1000;

export type TipoTokenConta = "verificacao_email" | "redefinicao_senha";

const VALIDADE: Record<TipoTokenConta, number> = {
  verificacao_email: 24 * HORA,
  redefinicao_senha: HORA,
};

export async function criarTokenConta(
  banco: Banco,
  usuarioId: string,
  tipo: TipoTokenConta,
): Promise<string> {
  const token = novoSegredo(32);
  const instante = agora();
  await banco.execute({
    sql: "UPDATE tokens_conta SET usado_em = ? WHERE usuario_id = ? AND tipo = ? AND usado_em IS NULL",
    args: [instante, usuarioId, tipo],
  });
  await banco.execute({
    sql: `INSERT INTO tokens_conta (id, usuario_id, tipo, token_hash, criado_em, expira_em)
          VALUES (?, ?, ?, ?, ?, ?)`,
    args: [novoId(), usuarioId, tipo, sha256(token), instante, instante + VALIDADE[tipo]],
  });
  return token;
}

export async function consumirTokenConta(
  banco: Banco,
  tipo: TipoTokenConta,
  token: string,
): Promise<string> {
  if (!token) throw erros.tokenInvalido();
  const resultado = await banco.execute({
    sql: `SELECT id, usuario_id FROM tokens_conta
          WHERE token_hash = ? AND tipo = ? AND usado_em IS NULL AND expira_em > ?`,
    args: [sha256(token), tipo, agora()],
  });
  const linha = resultado.rows[0];
  if (!linha) throw erros.tokenInvalido();
  const atualizacao = await banco.execute({
    sql: "UPDATE tokens_conta SET usado_em = ? WHERE id = ? AND usado_em IS NULL",
    args: [agora(), String(linha.id)],
  });
  if (atualizacao.rowsAffected !== 1) throw erros.tokenInvalido();
  return String(linha.usuario_id);
}
