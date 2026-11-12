import { mkdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { criarServidor } from "../src/servidor.js";

const bancoTemporario = path.join(tmpdir(), `chaveirocar-openapi-${process.pid}.db`);
process.env.TURSO_URL ??= `file:${bancoTemporario}`;
process.env.AMBIENTE ??= "dev";
process.env.EXPOR_TOKEN_DEV ??= "false";

const { app, config } = await criarServidor({ logger: false });
try {
  await app.ready();
  const documento = app.swagger();
  const destino = path.resolve(config.raiz, "..", "contracts", "openapi.json");
  await mkdir(path.dirname(destino), { recursive: true });
  await writeFile(destino, `${JSON.stringify(documento, null, 2)}\n`, "utf8");
  console.log(`OpenAPI gerado em ${destino}`);
} finally {
  await app.close();
  await rm(bancoTemporario, { force: true });
}
