import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import type { Config } from "./config.js";

export interface Armazenamento {
  salvar(chave: string, bytes: Buffer, mime: string): Promise<void>;
  ler(chave: string): Promise<Buffer>;
  remover(chave: string): Promise<void>;
}

/** Chaves são geradas internamente; ainda assim, nunca aceitamos `..` vindo de fora. */
function chaveSegura(chave: string): string {
  if (!chave || chave.includes("..") || chave.startsWith("/") || chave.includes("\\")) {
    throw new Error("Chave de armazenamento inválida.");
  }
  return chave;
}

function criarLocal(diretorio: string): Armazenamento {
  return {
    async salvar(chave, bytes) {
      const alvo = path.join(diretorio, chaveSegura(chave));
      await mkdir(path.dirname(alvo), { recursive: true });
      await writeFile(alvo, bytes);
    },
    async ler(chave) {
      return readFile(path.join(diretorio, chaveSegura(chave)));
    },
    async remover(chave) {
      await rm(path.join(diretorio, chaveSegura(chave)), { force: true });
    },
  };
}

function criarS3(config: Config): Armazenamento {
  const cliente = new S3Client({
    region: config.storage.s3.regiao,
    endpoint: config.storage.s3.endpoint || undefined,
    forcePathStyle: Boolean(config.storage.s3.endpoint),
    credentials: {
      accessKeyId: config.storage.s3.accessKeyId,
      secretAccessKey: config.storage.s3.secretAccessKey,
    },
  });
  const bucket = config.storage.s3.bucket;

  return {
    async salvar(chave, bytes, mime) {
      await cliente.send(
        new PutObjectCommand({
          Bucket: bucket,
          Key: chaveSegura(chave),
          Body: bytes,
          ContentType: mime,
          CacheControl: "private, max-age=3600",
        }),
      );
    },
    async ler(chave) {
      const resposta = await cliente.send(
        new GetObjectCommand({ Bucket: bucket, Key: chaveSegura(chave) }),
      );
      const corpo = resposta.Body;
      if (!corpo) throw new Error("Arquivo não encontrado no storage.");
      const bytes = await corpo.transformToByteArray();
      return Buffer.from(bytes);
    },
    async remover(chave) {
      await cliente.send(new DeleteObjectCommand({ Bucket: bucket, Key: chaveSegura(chave) }));
    },
  };
}

export function criarArmazenamento(config: Config): Armazenamento {
  return config.storage.tipo === "s3" ? criarS3(config) : criarLocal(config.storage.diretorio);
}
