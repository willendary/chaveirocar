import { existsSync } from "node:fs";
import path from "node:path";
import { z } from "zod";

const boolean = z
  .enum(["true", "false", "1", "0", "sim", "nao"])
  .transform((valor) => ["true", "1", "sim"].includes(valor));

const numero = z.coerce.number().int().positive();

const esquema = z.object({
  AMBIENTE: z.enum(["dev", "teste", "homologacao", "producao"]).default("dev"),
  HOST: z.string().default("0.0.0.0"),
  PORTA: z.coerce.number().int().min(1).max(65535).default(8787),
  URL_PUBLICA: z.string().url().default("http://localhost:8787"),
  TURSO_URL: z.string().min(1).default("file:./var/chaveirocar.db"),
  TURSO_TOKEN: z.string().default(""),
  STORAGE_TIPO: z.enum(["local", "s3"]).default("local"),
  DIR_STORAGE: z.string().default("./var/storage"),
  S3_ENDPOINT: z.string().default(""),
  S3_REGIAO: z.string().default("auto"),
  S3_BUCKET: z.string().default(""),
  S3_ACCESS_KEY_ID: z.string().default(""),
  S3_SECRET_ACCESS_KEY: z.string().default(""),
  SMTP_URL: z.string().default(""),
  EMAIL_REMETENTE: z.string().default("ChaveiroCar <nao-responda@exemplo.com>"),
  ORIGENS_PERMITIDAS: z.string().default(""),
  LIMITE_CORPO_MB: numero.default(2),
  LIMITE_FOTO_MB: numero.default(15),
  MAX_FOTOS_POR_REGISTRO: numero.default(20),
  MAX_CONVITES_ABERTOS: numero.default(20),
  RETENCAO_MUDANCAS_DIAS: numero.default(180),
  EXPOR_TOKEN_DEV: boolean.default(false),
  GOOGLE_ONLY: boolean.default(false),
  GOOGLE_CLIENT_ID: z.string().default(""),
  GOOGLE_CLIENT_IDS: z.string().default(""),
  LOG_NIVEL: z.string().default("info"),
});

export type Ambiente = z.infer<typeof esquema>["AMBIENTE"];

export interface Config {
  ambiente: Ambiente;
  host: string;
  porta: number;
  urlPublica: string;
  tursoUrl: string;
  tursoToken: string;
  storage: {
    tipo: "local" | "s3";
    diretorio: string;
    s3: {
      endpoint: string;
      regiao: string;
      bucket: string;
      accessKeyId: string;
      secretAccessKey: string;
    };
  };
  email: {
    smtpUrl: string;
    remetente: string;
  };
  origensPermitidas: string[];
  limites: {
    corpoMb: number;
    fotoMb: number;
    fotosPorRegistro: number;
    convitesAbertos: number;
    retencaoMudancasDias: number;
  };
  exporTokenDev: boolean;
  googleOnly: boolean;
  googleClientId: string;
  logNivel: string;
  raiz: string;
}

const DIR_COMPILADO = import.meta.dirname.endsWith(`${path.sep}dist${path.sep}src`);
const RAIZ = path.resolve(import.meta.dirname, DIR_COMPILADO ? "../.." : "..");

export function criarConfig(
  env: NodeJS.ProcessEnv = process.env,
  raiz = RAIZ,
): Config {
  const arquivoEnv = path.join(raiz, ".env");
  if (existsSync(arquivoEnv)) {
    process.loadEnvFile(arquivoEnv);
  }

  // Render (e a maioria dos PaaS) injeta PORT; aceitamos os dois nomes.
  const dados = esquema.parse({ ...env, PORTA: env.PORTA ?? env.PORT });
  const producao = dados.AMBIENTE === "producao";

  if (producao && dados.TURSO_URL.startsWith("file:")) {
    throw new Error("Em produção TURSO_URL deve apontar para o Turso, não para arquivo local.");
  }
  if (producao && dados.TURSO_TOKEN.trim().length === 0) {
    throw new Error("TURSO_TOKEN é obrigatório em produção.");
  }
  if (producao && dados.SMTP_URL.trim().length === 0 && !dados.GOOGLE_ONLY) {
    throw new Error("SMTP_URL é obrigatório em produção para envio seguro de links de conta.");
  }
  if (producao && dados.EXPOR_TOKEN_DEV) {
    throw new Error("EXPOR_TOKEN_DEV não pode ser true em produção.");
  }
  if (dados.STORAGE_TIPO === "s3" && (!dados.S3_BUCKET || !dados.S3_ACCESS_KEY_ID)) {
    throw new Error("Storage S3 exige S3_BUCKET, S3_ACCESS_KEY_ID e S3_SECRET_ACCESS_KEY.");
  }

  return {
    ambiente: dados.AMBIENTE,
    host: dados.HOST,
    porta: dados.PORTA,
    urlPublica: dados.URL_PUBLICA.replace(/\/+$/, ""),
    tursoUrl: resolverCaminhoLocal(dados.TURSO_URL, raiz),
    tursoToken: dados.TURSO_TOKEN,
    storage: {
      tipo: dados.STORAGE_TIPO,
      diretorio: path.resolve(raiz, dados.DIR_STORAGE),
      s3: {
        endpoint: dados.S3_ENDPOINT,
        regiao: dados.S3_REGIAO,
        bucket: dados.S3_BUCKET,
        accessKeyId: dados.S3_ACCESS_KEY_ID,
        secretAccessKey: dados.S3_SECRET_ACCESS_KEY,
      },
    },
    email: {
      smtpUrl: dados.SMTP_URL,
      remetente: dados.EMAIL_REMETENTE,
    },
    origensPermitidas: dados.ORIGENS_PERMITIDAS.split(",")
      .map((origem) => origem.trim())
      .filter(Boolean),
    limites: {
      corpoMb: dados.LIMITE_CORPO_MB,
      fotoMb: dados.LIMITE_FOTO_MB,
      fotosPorRegistro: dados.MAX_FOTOS_POR_REGISTRO,
      convitesAbertos: dados.MAX_CONVITES_ABERTOS,
      retencaoMudancasDias: dados.RETENCAO_MUDANCAS_DIAS,
    },
    exporTokenDev: dados.EXPOR_TOKEN_DEV || !producao,
    googleOnly: dados.GOOGLE_ONLY,
    googleClientId: [dados.GOOGLE_CLIENT_ID, dados.GOOGLE_CLIENT_IDS]
      .map((s) => s.trim())
      .filter(Boolean)
      .join(","),
    logNivel: dados.LOG_NIVEL,
    raiz,
  };
}

function resolverCaminhoLocal(url: string, raiz: string): string {
  if (!url.startsWith("file:")) return url;
  const alvo = url.slice("file:".length);
  if (path.isAbsolute(alvo)) return url;
  return `file:${path.resolve(raiz, alvo)}`;
}
