import type { Banco } from "./banco.js";
import { agora } from "./banco.js";
import { erros } from "./erros.js";
import type { Papel } from "./esquemas.js";

export interface Membro {
  empresaId: string;
  usuarioId: string;
  papel: Papel;
}

const PAPEIS_LEITURA: Papel[] = ["proprietario", "administrador", "colaborador", "leitor"];
const PAPEIS_ESCRITA: Papel[] = ["proprietario", "administrador", "colaborador"];
const PAPEIS_GESTAO: Papel[] = ["proprietario", "administrador"];

export async function obterMembro(
  banco: Banco,
  empresaId: string,
  usuarioId: string,
): Promise<Membro | null> {
  const resultado = await banco.execute({
    sql: `SELECT empresa_id, usuario_id, papel FROM membros
          WHERE empresa_id = ? AND usuario_id = ? AND excluido_em IS NULL`,
    args: [empresaId, usuarioId],
  });
  const linha = resultado.rows[0];
  if (!linha) return null;
  return {
    empresaId: String(linha.empresa_id),
    usuarioId: String(linha.usuario_id),
    papel: String(linha.papel) as Papel,
  };
}

export async function exigirMembro(
  banco: Banco,
  empresaId: string,
  usuarioId: string,
  papeis: Papel[] = PAPEIS_LEITURA,
): Promise<Membro> {
  const membro = await obterMembro(banco, empresaId, usuarioId);
  if (!membro) throw erros.naoEncontrado("Empresa não encontrada ou acesso removido.");
  if (!papeis.includes(membro.papel)) throw erros.semPermissao();
  return membro;
}

export function exigirEscrita(membro: Membro): void {
  if (!PAPEIS_ESCRITA.includes(membro.papel)) throw erros.semPermissao("Seu acesso é somente leitura.");
}

export function exigirGestao(membro: Membro): void {
  if (!PAPEIS_GESTAO.includes(membro.papel)) {
    throw erros.semPermissao("Somente proprietário ou administrador pode gerenciar a equipe.");
  }
}

export async function membrosAtivos(banco: Banco, empresaId: string): Promise<number> {
  const resultado = await banco.execute({
    sql: "SELECT COUNT(*) AS total FROM membros WHERE empresa_id = ? AND excluido_em IS NULL",
    args: [empresaId],
  });
  return Number(resultado.rows[0]?.total ?? 0);
}

export async function quantidadeProprietarios(banco: Banco, empresaId: string): Promise<number> {
  const resultado = await banco.execute({
    sql: `SELECT COUNT(*) AS total FROM membros
          WHERE empresa_id = ? AND papel = 'proprietario' AND excluido_em IS NULL`,
    args: [empresaId],
  });
  return Number(resultado.rows[0]?.total ?? 0);
}

export async function garantirProprietarioRestante(
  banco: Banco,
  empresaId: string,
  usuarioAlvo: string,
): Promise<void> {
  const alvo = await obterMembro(banco, empresaId, usuarioAlvo);
  if (!alvo || alvo.papel !== "proprietario") return;
  const total = await quantidadeProprietarios(banco, empresaId);
  if (total <= 1) throw erros.semPermissao("A empresa precisa de pelo menos um proprietário.");
}

export function podeGerenciarPapel(ator: Papel, alvo: Papel, novo: Papel): boolean {
  if (ator === "proprietario") return true;
  if (ator !== "administrador") return false;
  if (alvo === "proprietario" || alvo === "administrador") return false;
  return novo === "colaborador" || novo === "leitor";
}

export async function registrarAuditoria(
  banco: Banco,
  dados: {
    empresaId?: string | null;
    usuarioId?: string | null;
    acao: string;
    entidade?: string;
    entidadeId?: string;
    detalhes?: Record<string, unknown>;
    ip?: string;
  },
): Promise<void> {
  await banco.execute({
    sql: `INSERT INTO auditoria
          (empresa_id, usuario_id, acao, entidade, entidade_id, detalhes, ip, criado_em)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    args: [
      dados.empresaId ?? null,
      dados.usuarioId ?? null,
      dados.acao,
      dados.entidade ?? "",
      dados.entidadeId ?? "",
      JSON.stringify(dados.detalhes ?? {}),
      (dados.ip ?? "").slice(0, 200),
      agora(),
    ],
  });
}
