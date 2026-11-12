import { createClient, type Client } from "@libsql/client";
import type { Config } from "./config.js";

export type Banco = Client;

export async function abrirBanco(config: Config): Promise<Banco> {
  const banco = createClient({
    url: config.tursoUrl,
    authToken: config.tursoToken || undefined,
  });
  await banco.execute("PRAGMA foreign_keys = ON");
  if (config.tursoUrl.startsWith("file:")) {
    await banco.execute("PRAGMA busy_timeout = 5000");
  }
  return banco;
}

export function agora(): number {
  return Date.now();
}
