import path from "node:path";
import { abrirBanco } from "./banco.js";
import { criarConfig } from "./config.js";
import { migrar } from "./migracoes.js";

const config = criarConfig();
const banco = await abrirBanco(config);
try {
  const aplicadas = await migrar(banco, path.join(config.raiz, "migrations"));
  if (aplicadas.length === 0) {
    console.log("Nenhuma migração pendente.");
  } else {
    for (const arquivo of aplicadas) console.log(`Aplicada: ${arquivo}`);
  }
} finally {
  await banco.close();
}
