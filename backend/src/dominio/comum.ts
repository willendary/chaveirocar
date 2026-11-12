import type { InStatement, ResultSet } from "@libsql/client";
import { agora } from "../banco.js";

/** Aceita tanto o cliente quanto uma transação aberta. */
export interface ExecutorSql {
  execute(instrucao: InStatement): Promise<ResultSet>;
}

export type TipoMudanca = "upsert" | "exclusao";

export async function registrarMudanca(
  executor: ExecutorSql,
  empresaId: string,
  entidade: "atendimento" | "ficha" | "foto",
  entidadeId: string,
  revisao: number,
  tipo: TipoMudanca,
): Promise<void> {
  await executor.execute({
    sql: `INSERT INTO mudancas (empresa_id, entidade, entidade_id, revisao, tipo, criado_em)
          VALUES (?, ?, ?, ?, ?, ?)`,
    args: [empresaId, entidade, entidadeId, revisao, tipo, agora()],
  });
}

export function lerJson<T>(valor: unknown): T | null {
  if (typeof valor !== "string" || valor.length === 0) return null;
  try {
    return JSON.parse(valor) as T;
  } catch {
    return null;
  }
}

/** UUID determinístico (versão 5 simplificada) a partir de um hash hexadecimal. */
export function uuidDeHash(hex: string): string {
  const base = (hex + "0".repeat(32)).slice(0, 32).split("");
  base[12] = "5";
  const variante = Number.parseInt(base[16] ?? "0", 16);
  base[16] = ((variante & 0x3) | 0x8).toString(16);
  return [
    base.slice(0, 8).join(""),
    base.slice(8, 12).join(""),
    base.slice(12, 16).join(""),
    base.slice(16, 20).join(""),
    base.slice(20, 32).join(""),
  ].join("-");
}

export function textoObrigatorio(valor: string, campo: string): string {
  const limpo = valor.trim();
  if (!limpo) throw new Error(`O campo ${campo} é obrigatório.`);
  return limpo;
}
