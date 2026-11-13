import { randomUUID } from "node:crypto";
import path from "node:path";
import Fastify, { type FastifyInstance, type FastifyServerOptions } from "fastify";
import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import multipart from "@fastify/multipart";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import { abrirBanco, type Banco } from "./banco.js";
import { criarConfig, type Config } from "./config.js";
import { criarEmail } from "./email.js";
import { ErroApi } from "./erros.js";
import { criarArmazenamento } from "./storage.js";
import { migrar } from "./migracoes.js";
import { criarAutenticacao } from "./auth/autenticacao.js";
import type { ContextoRotas } from "./rotas/contexto.js";
import { rotasAuth } from "./rotas/auth.js";
import { rotasEmpresas } from "./rotas/empresas.js";
import { rotasAtendimentos } from "./rotas/atendimentos.js";
import { rotasFichas } from "./rotas/fichas.js";
import { rotasFotos } from "./rotas/fotos.js";
import { rotasSync } from "./rotas/sync.js";
import { rotasImportacao } from "./rotas/importacao.js";
import { rotasAuditoria } from "./rotas/auditoria.js";

export const VERSAO_API = "1.7.0";

export interface OpcoesServidor {
  config?: Config;
  banco?: Banco;
  logger?: FastifyServerOptions["logger"];
}

export async function criarServidor(opcoes: OpcoesServidor = {}): Promise<{
  app: FastifyInstance;
  config: Config;
  banco: Banco;
}> {
  const config = opcoes.config ?? criarConfig();
  const banco = opcoes.banco ?? (await abrirBanco(config));
  await migrar(banco, path.join(config.raiz, "migrations"));

  const app = Fastify({
    logger:
      opcoes.logger ??
      {
        level: config.logNivel,
        redact: ["req.headers.authorization", "req.headers.cookie", "res.headers['set-cookie']"],
      },
    genReqId: () => randomUUID(),
    bodyLimit: config.limites.corpoMb * 1024 * 1024,
    trustProxy: true,
  });

  const email = criarEmail(config, (mensagem) => app.log.info(mensagem));
  const storage = criarArmazenamento(config);
  const autenticacao = criarAutenticacao(banco);

  await app.register(helmet, { contentSecurityPolicy: false });
  await app.register(cors, {
    origin: config.origensPermitidas.length > 0 ? config.origensPermitidas : false,
    methods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowedHeaders: ["Authorization", "Content-Type", "If-None-Match"],
    exposedHeaders: ["ETag", "Content-Length"],
    maxAge: 3600,
  });
  await app.register(rateLimit, {
    max: 300,
    timeWindow: "1 minute",
    keyGenerator: (request) => request.usuario?.usuarioId ?? request.ip,
  });
  await app.register(multipart, {
    limits: {
      fileSize: config.limites.fotoMb * 1024 * 1024,
      files: 1,
      fields: 20,
    },
  });
  await app.register(swagger, {
    openapi: {
      openapi: "3.1.0",
      info: {
        title: "ChaveiroCar API",
        version: VERSAO_API,
        description:
          "API única dos aplicativos Android, iOS e Windows do ChaveiroCar. " +
          "Autenticação por Bearer token; dados isolados por empresa.",
      },
      servers: [{ url: config.urlPublica, description: config.ambiente }],
      components: {
        securitySchemes: {
          bearerAuth: {
            type: "http",
            scheme: "bearer",
            bearerFormat: "token opaco",
          },
        },
      },
      tags: [
        { name: "Autenticação", description: "Conta, sessão e recuperação de senha" },
        { name: "Empresas", description: "Workspaces e seus dados" },
        { name: "Equipe", description: "Membros, convites e permissões" },
        { name: "Atendimentos", description: "Registros de serviço" },
        { name: "Fichas técnicas", description: "Conhecimento técnico consultável" },
        { name: "Fotos", description: "Upload e download autenticado de imagens" },
        { name: "Sincronização", description: "Pull incremental e push idempotente" },
        { name: "Migração", description: "Importação de backups do aplicativo Android" },
        { name: "Auditoria", description: "Ações relevantes da empresa" },
      ],
    },
  });
  await app.register(swaggerUi, { routePrefix: "/docs", staticCSP: true });

  app.setErrorHandler((erro, request, reply) => {
    if (erro instanceof ErroApi) {
      return reply.code(erro.status).send({
        erro: {
          codigo: erro.codigo,
          mensagem: erro.message,
          detalhes: erro.detalhes,
        },
        requisicaoId: request.id,
      });
    }

    const codigoFastify = (erro as { code?: string }).code ?? "";
    if (codigoFastify === "FST_REQ_FILE_TOO_LARGE" || codigoFastify === "FST_FILES_LIMIT") {
      return reply.code(413).send({
        erro: { codigo: "LIMITE_EXCEDIDO", mensagem: "O arquivo enviado é grande demais." },
        requisicaoId: request.id,
      });
    }
    if (codigoFastify === "FST_INVALID_MULTIPART_CONTENT_TYPE") {
      return reply.code(400).send({
        erro: { codigo: "VALIDACAO", mensagem: "Envie o conteúdo como multipart/form-data." },
        requisicaoId: request.id,
      });
    }
    if ((erro as { validation?: unknown }).validation) {
      return reply.code(400).send({
        erro: {
          codigo: "VALIDACAO",
          mensagem: (erro as { message?: string }).message ?? "Dados inválidos.",
          detalhes: (erro as { validation?: unknown }).validation,
        },
        requisicaoId: request.id,
      });
    }
    if ((erro as { statusCode?: number }).statusCode === 429) {
      return reply.code(429).send({
        erro: { codigo: "LIMITE_EXCEDIDO", mensagem: "Muitas requisições. Tente novamente em instantes." },
        requisicaoId: request.id,
      });
    }

    request.log.error({ err: erro }, "erro não tratado");
    return reply.code(500).send({
      erro: { codigo: "ERRO_INTERNO", mensagem: "Erro interno. Tente novamente." },
      requisicaoId: request.id,
    });
  });

  app.setNotFoundHandler((request, reply) => {
    reply.code(404).send({
      erro: { codigo: "NAO_ENCONTRADO", mensagem: "Rota não encontrada." },
      requisicaoId: request.id,
    });
  });

  app.get(
    "/saude",
    {
      schema: {
        tags: ["Operação"],
        summary: "Verificação simples de vida",
        response: {
          200: {
            type: "object",
            properties: {
              status: { type: "string" },
              versao: { type: "string" },
              ambiente: { type: "string" },
            },
            required: ["status", "versao", "ambiente"],
          },
        },
      },
    },
    async () => ({ status: "ok", versao: VERSAO_API, ambiente: config.ambiente }),
  );

  app.get(
    "/pronto",
    {
      schema: {
        tags: ["Operação"],
        summary: "Verificação de prontidão (banco acessível)",
        response: {
          200: {
            type: "object",
            properties: { status: { type: "string" }, banco: { type: "string" } },
            required: ["status", "banco"],
          },
          503: {
            type: "object",
            properties: { status: { type: "string" }, banco: { type: "string" } },
            required: ["status", "banco"],
          },
        },
      },
    },
    async (_request, reply) => {
      try {
        await banco.execute("SELECT 1");
        return { status: "ok", banco: "ok" };
      } catch {
        return reply.code(503).send({ status: "indisponivel", banco: "erro" });
      }
    },
  );

  const contexto: ContextoRotas = {
    banco,
    config,
    email,
    storage,
    autenticacao,
  };

  await app.register(rotasAuth, { prefix: "/api/v1", ...contexto });
  await app.register(rotasEmpresas, { prefix: "/api/v1", ...contexto });
  await app.register(rotasAtendimentos, { prefix: "/api/v1", ...contexto });
  await app.register(rotasFichas, { prefix: "/api/v1", ...contexto });
  await app.register(rotasFotos, { prefix: "/api/v1", ...contexto });
  await app.register(rotasSync, { prefix: "/api/v1", ...contexto });
  await app.register(rotasImportacao, { prefix: "/api/v1", ...contexto });
  await app.register(rotasAuditoria, { prefix: "/api/v1", ...contexto });

  app.get(
    "/api/v1/openapi.json",
    {
      schema: {
        tags: ["Operação"],
        summary: "Contrato OpenAPI 3.1 da API",
        response: { 200: { type: "object" } },
      },
    },
    async () => app.swagger(),
  );

  app.addHook("onClose", async () => {
    await banco.close();
  });

  return { app, config, banco };
}
