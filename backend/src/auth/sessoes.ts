import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import { novoId, novoSegredo, sha256 } from "../util/id.js";

const MINUTO = 60_000;
const DIA = 24 * 60 * MINUTO;
const VALIDADE_ACESSO = 15 * MINUTO;
const VALIDADE_REFRESH = 60 * DIA;
const INTERVALO_ULTIMO_USO = 5 * MINUTO;

export interface Tokens {
  acesso: string;
  expiraEm: number;
  refresh: string;
  refreshExpiraEm: number;
  tipo: "Bearer";
}

export interface SessaoAutenticada {
  sessaoId: string;
  usuarioId: string;
  nome: string;
  email: string;
  emailVerificado: boolean;
}

interface LinhaSessao {
  id: string;
  usuario_id: string;
  nome: string;
  email: string;
  email_verificado_em: number | null;
  ultimo_uso_em: number | null;
}

function montarTokens(acesso: string, refresh: string): Tokens {
  return {
    acesso,
    expiraEm: agora() + VALIDADE_ACESSO,
    refresh,
    refreshExpiraEm: agora() + VALIDADE_REFRESH,
    tipo: "Bearer",
  };
}

export async function criarSessao(
  banco: Banco,
  usuarioId: string,
  ip: string,
  userAgent: string,
): Promise<Tokens> {
  const sessaoId = novoId();
  const acesso = novoSegredo(32);
  const refresh = novoSegredo(48);
  const instante = agora();
  await banco.execute({
    sql: `INSERT INTO sessoes
      (id, usuario_id, acesso_hash, acesso_expira_em, refresh_hash, criado_em, expira_em, ip, user_agent)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    args: [
      sessaoId,
      usuarioId,
      sha256(acesso),
      instante + VALIDADE_ACESSO,
      sha256(refresh),
      instante,
      instante + VALIDADE_REFRESH,
      ip.slice(0, 200),
      userAgent.slice(0, 500),
    ],
  });
  return montarTokens(acesso, refresh);
}

export async function autenticar(
  banco: Banco,
  tokenAcesso: string,
): Promise<SessaoAutenticada> {
  if (!tokenAcesso) throw erros.naoAutenticado();
  const resultado = await banco.execute({
    sql: `SELECT s.id, s.usuario_id, u.nome, u.email, u.email_verificado_em, s.ultimo_uso_em
          FROM sessoes s
          JOIN usuarios u ON u.id = s.usuario_id
          WHERE s.acesso_hash = ? AND s.revogado_em IS NULL
            AND s.acesso_expira_em > ? AND u.excluido_em IS NULL`,
    args: [sha256(tokenAcesso), agora()],
  });
  const linha = resultado.rows[0] as unknown as LinhaSessao | undefined;
  if (!linha) throw erros.naoAutenticado();

  if (!linha.ultimo_uso_em || agora() - linha.ultimo_uso_em > INTERVALO_ULTIMO_USO) {
    await banco.execute({
      sql: "UPDATE sessoes SET ultimo_uso_em = ? WHERE id = ?",
      args: [agora(), linha.id],
    });
  }
  return {
    sessaoId: linha.id,
    usuarioId: linha.usuario_id,
    nome: linha.nome,
    email: linha.email,
    emailVerificado: linha.email_verificado_em !== null,
  };
}

export async function renovarSessao(banco: Banco, refresh: string): Promise<Tokens> {
  if (!refresh) throw erros.naoAutenticado("Refresh token ausente.");
  const hash = sha256(refresh);
  const resultado = await banco.execute({
    sql: `SELECT id FROM sessoes
          WHERE refresh_hash = ? AND revogado_em IS NULL AND expira_em > ?`,
    args: [hash, agora()],
  });
  const linha = resultado.rows[0];
  if (!linha) {
    // Reuso de um refresh já rotacionado: revoga a família inteira.
    const anterior = await banco.execute({
      sql: "SELECT id FROM sessoes WHERE refresh_hash_anterior = ? AND revogado_em IS NULL",
      args: [hash],
    });
    const familia = anterior.rows[0];
    if (familia) {
      await banco.execute({
        sql: "UPDATE sessoes SET revogado_em = ? WHERE id = ?",
        args: [agora(), String(familia.id)],
      });
    }
    throw erros.naoAutenticado("Sessão expirada. Entre novamente.");
  }

  const sessaoId = String(linha.id);
  const acesso = novoSegredo(32);
  const novoRefresh = novoSegredo(48);
  const instante = agora();
  const transacao = await banco.transaction("write");
  try {
    const troca = await transacao.execute({
      sql: `UPDATE sessoes SET
              acesso_hash = ?, acesso_expira_em = ?,
              refresh_hash = ?, refresh_hash_anterior = ?,
              ultimo_uso_em = ?
            WHERE id = ? AND refresh_hash = ? AND revogado_em IS NULL`,
      args: [
        sha256(acesso),
        instante + VALIDADE_ACESSO,
        sha256(novoRefresh),
        hash,
        instante,
        sessaoId,
        hash,
      ],
    });
    if (troca.rowsAffected !== 1) {
      await transacao.rollback();
      throw erros.naoAutenticado("Sessão já renovada em outro dispositivo. Entre novamente.");
    }
    await transacao.commit();
  } catch (erro) {
    if (erro instanceof Error && erro.name === "ErroApi") throw erro;
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return montarTokens(acesso, novoRefresh);
}

export async function revogarSessao(
  banco: Banco,
  sessaoId: string,
  usuarioId: string,
): Promise<void> {
  await banco.execute({
    sql: "UPDATE sessoes SET revogado_em = ? WHERE id = ? AND usuario_id = ? AND revogado_em IS NULL",
    args: [agora(), sessaoId, usuarioId],
  });
}

export async function revogarSessaoDoRefresh(
  banco: Banco,
  refresh: string,
  usuarioId: string,
): Promise<void> {
  if (!refresh) return;
  await banco.execute({
    sql: "UPDATE sessoes SET revogado_em = ? WHERE refresh_hash = ? AND usuario_id = ? AND revogado_em IS NULL",
    args: [agora(), sha256(refresh), usuarioId],
  });
}

export async function revogarTodasAsSessoes(
  banco: Banco,
  usuarioId: string,
  exceto?: string,
): Promise<void> {
  await banco.execute({
    sql: "UPDATE sessoes SET revogado_em = ? WHERE usuario_id = ? AND revogado_em IS NULL AND id <> ?",
    args: [agora(), usuarioId, exceto ?? ""],
  });
}

export function validadeRefreshMs(): number {
  return VALIDADE_REFRESH;
}
