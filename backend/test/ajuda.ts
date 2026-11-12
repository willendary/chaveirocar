import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { randomUUID } from "node:crypto";
import type { FastifyInstance } from "fastify";
import { criarConfig } from "../src/config.js";
import { criarServidor } from "../src/servidor.js";
import type { Banco } from "../src/banco.js";

const RAIZ = path.resolve(import.meta.dirname, "..");

export interface Ambiente {
  app: FastifyInstance;
  banco: Banco;
  diretorio: string;
  fechar: () => Promise<void>;
}

export async function criarAmbiente(
  variaveis: Record<string, string> = {},
): Promise<Ambiente> {
  const diretorio = await mkdtemp(path.join(tmpdir(), "chaveirocar-teste-"));
  const config = criarConfig(
    {
      AMBIENTE: "teste",
      HOST: "127.0.0.1",
      PORTA: "8787",
      URL_PUBLICA: "http://localhost:8787",
      TURSO_URL: `file:${path.join(diretorio, "teste.db")}`,
      DIR_STORAGE: path.join(diretorio, "storage"),
      EXPOR_TOKEN_DEV: "true",
      LOG_NIVEL: "silent",
      ...variaveis,
    },
    RAIZ,
  );
  const { app, banco } = await criarServidor({ config, logger: false });
  await app.ready();
  return {
    app,
    banco,
    diretorio,
    async fechar() {
      await app.close();
      void rm(diretorio, {
        recursive: true,
        force: true,
        maxRetries: 5,
        retryDelay: 200,
      }).catch(() => undefined);
    },
  };
}

export interface Conta {
  usuario: { id: string; nome: string; email: string; emailVerificado: boolean };
  tokens: { acesso: string; refresh: string; expiraEm: number; refreshExpiraEm: number };
  empresas: Array<{ id: string; nome: string; papel: string }>;
  verificacaoToken?: string;
  cabecalho: { authorization: string };
}

export async function cadastrar(
  app: FastifyInstance,
  entrada: {
    nome?: string;
    email?: string;
    senha?: string;
    empresa?: string;
  } = {},
): Promise<Conta> {
  const resposta = await app.inject({
    method: "POST",
    url: "/api/v1/auth/cadastro",
    payload: {
      nome: entrada.nome ?? "Dono Teste",
      email: entrada.email ?? `dono-${randomUUID()}@exemplo.com`,
      senha: entrada.senha ?? "Senha1234",
      empresa: entrada.empresa ?? "Chaveiro Teste",
    },
  });
  if (resposta.statusCode !== 201) {
    throw new Error(`Cadastro falhou: ${resposta.statusCode} ${resposta.body}`);
  }
  const corpo = resposta.json() as Omit<Conta, "cabecalho">;
  return {
    ...corpo,
    cabecalho: { authorization: `Bearer ${corpo.tokens.acesso}` },
  };
}

export function autorizacao(acesso: string): { authorization: string } {
  return { authorization: `Bearer ${acesso}` };
}

export function corpoMultipart(
  campos: Record<string, string>,
  arquivo: { nome: string; mime: string; bytes: Buffer },
): { payload: Buffer; contentType: string } {
  const fronteira = `----chaveirocar-${randomUUID()}`;
  const partes: Buffer[] = [];
  for (const [nome, valor] of Object.entries(campos)) {
    partes.push(
      Buffer.from(
        `--${fronteira}\r\nContent-Disposition: form-data; name="${nome}"\r\n\r\n${valor}\r\n`,
        "utf8",
      ),
    );
  }
  partes.push(
    Buffer.from(
      `--${fronteira}\r\nContent-Disposition: form-data; name="arquivo"; filename="${arquivo.nome}"\r\n` +
        `Content-Type: ${arquivo.mime}\r\n\r\n`,
      "utf8",
    ),
  );
  partes.push(arquivo.bytes);
  partes.push(Buffer.from(`\r\n--${fronteira}--\r\n`, "utf8"));
  return {
    payload: Buffer.concat(partes),
    contentType: `multipart/form-data; boundary=${fronteira}`,
  };
}

/** JPEG mínimo: assinatura válida seguida de bytes arbitrários (o servidor valida a assinatura). */
export function imagemJpeg(conteudo = "foto"): Buffer {
  return Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.from(conteudo.padEnd(16, "x"), "utf8")]);
}

/** ZIP sem compressão (stored) — suficiente para o importador (yauzl). */
export function zipArmazenado(entradas: Array<{ nome: string; bytes: Buffer }>): Buffer {
  const central: Buffer[] = [];
  const partes: Buffer[] = [];
  let deslocamento = 0;
  for (const entrada of entradas) {
    const nome = Buffer.from(entrada.nome, "utf8");
    const cabecalho = Buffer.alloc(30);
    cabecalho.writeUInt32LE(0x04034b50, 0);
    cabecalho.writeUInt16LE(20, 4); // versão
    cabecalho.writeUInt16LE(0, 6); // flags
    cabecalho.writeUInt16LE(0, 8); // método stored
    cabecalho.writeUInt16LE(0, 10); // tempo
    cabecalho.writeUInt16LE(0x21, 12); // data
    cabecalho.writeUInt32LE(0, 14); // CRC (yauzl não exige para stored)
    cabecalho.writeUInt32LE(entrada.bytes.length, 18);
    cabecalho.writeUInt32LE(entrada.bytes.length, 22);
    cabecalho.writeUInt16LE(nome.length, 26);
    cabecalho.writeUInt16LE(0, 28);
    partes.push(cabecalho, nome, entrada.bytes);
    const registro = Buffer.alloc(46);
    registro.writeUInt32LE(0x02014b50, 0);
    registro.writeUInt16LE(20, 4); // versão feita por
    registro.writeUInt16LE(20, 6); // versão necessária
    registro.writeUInt16LE(0, 8); // flags
    registro.writeUInt16LE(0, 10); // stored
    registro.writeUInt16LE(0x21, 14); // data
    registro.writeUInt32LE(entrada.bytes.length, 20);
    registro.writeUInt32LE(entrada.bytes.length, 24);
    registro.writeUInt16LE(nome.length, 28);
    registro.writeUInt32LE(deslocamento, 42);
    central.push(registro, nome);
    deslocamento += cabecalho.length + nome.length + entrada.bytes.length;
  }
  const tamanhoCentral = central.reduce((acc, b) => acc + b.length, 0);
  const fim = Buffer.alloc(22);
  fim.writeUInt32LE(0x06054b50, 0);
  fim.writeUInt16LE(entradas.length, 8);
  fim.writeUInt16LE(entradas.length, 10);
  fim.writeUInt32LE(tamanhoCentral, 12);
  fim.writeUInt32LE(deslocamento, 16);
  return Buffer.concat([...partes, ...central, fim]);
}
