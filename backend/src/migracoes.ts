import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import type { Banco } from "./banco.js";
import { agora } from "./banco.js";

/**
 * Divide um arquivo SQL em comandos, ignorando `--` fora de strings.
 * O esquema não usa triggers nem procedures com `;` interno.
 */
export function dividirSql(sql: string): string[] {
  const comandos: string[] = [];
  let atual = "";
  let aspas: "'" | '"' | null = null;
  for (let indice = 0; indice < sql.length; indice += 1) {
    const caractere = sql[indice]!;
    if (aspas) {
      atual += caractere;
      if (caractere === aspas) {
        if (sql[indice + 1] === aspas) {
          atual += sql[indice + 1];
          indice += 1;
        } else {
          aspas = null;
        }
      }
      continue;
    }
    if (caractere === "'" || caractere === '"') {
      aspas = caractere;
      atual += caractere;
      continue;
    }
    if (caractere === "-" && sql[indice + 1] === "-") {
      while (indice < sql.length && sql[indice] !== "\n") indice += 1;
      atual += "\n";
      continue;
    }
    if (caractere === ";") {
      const limpo = atual.trim();
      if (limpo) comandos.push(limpo);
      atual = "";
      continue;
    }
    atual += caractere;
  }
  const restante = atual.trim();
  if (restante) comandos.push(restante);
  return comandos;
}

export async function migrar(banco: Banco, diretorio: string): Promise<string[]> {
  await banco.execute(`
    CREATE TABLE IF NOT EXISTS _migracoes (
      nome TEXT PRIMARY KEY,
      aplicada_em INTEGER NOT NULL
    )
  `);
  const aplicadas = new Set(
    (await banco.execute("SELECT nome FROM _migracoes")).rows.map((linha) =>
      String(linha.nome),
    ),
  );
  const arquivos = (await readdir(diretorio))
    .filter((nome) => nome.endsWith(".sql"))
    .sort();

  const executadas: string[] = [];
  for (const arquivo of arquivos) {
    if (aplicadas.has(arquivo)) continue;
    const sql = await readFile(path.join(diretorio, arquivo), "utf8");
    const comandos = dividirSql(sql);
    const transacao = await banco.transaction("write");
    try {
      for (const comando of comandos) {
        await transacao.execute(comando);
      }
      await transacao.execute({
        sql: "INSERT INTO _migracoes (nome, aplicada_em) VALUES (?, ?)",
        args: [arquivo, agora()],
      });
      await transacao.commit();
      executadas.push(arquivo);
    } catch (erro) {
      await transacao.rollback();
      throw new Error(`Falha na migração ${arquivo}: ${(erro as Error).message}`, {
        cause: erro,
      });
    }
  }
  return executadas;
}
