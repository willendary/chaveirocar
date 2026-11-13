import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirMembro, exigirEscrita } from "../permissoes.js";
import { ID, LoteSyncSchema, PullSyncSchema, RespostaSyncSchema } from "../esquemas.js";
import { empurrarOperacoes, puxarMudancas } from "../servicos/sync.js";

const EmpresaParams = Type.Object({ empresaId: ID });
const PullQuery = Type.Object({
  cursor: Type.Optional(Type.Integer({ minimum: 0 })),
  limite: Type.Optional(Type.Integer({ minimum: 1, maximum: 500 })),
});

export const rotasSync: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, autenticacao } = opcoes;

  app.get(
    "/empresas/:empresaId/sync",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Sincronização"],
        summary: "Puxa mudanças incrementais desde um cursor",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        querystring: PullQuery,
        response: { 200: PullSyncSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      const { cursor, limite } = request.query as { cursor?: number; limite?: number };
      return puxarMudancas(banco, empresaId, Number(cursor ?? 0), Number(limite ?? 200));
    },
  );

  app.post(
    "/empresas/:empresaId/sync",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Sincronização"],
        summary: "Envia um lote idempotente de operações offline",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        body: LoteSyncSchema,
        response: { 200: RespostaSyncSchema, ...respostasErro },
      },
      config: { rateLimit: { max: 120, timeWindow: "1 minute" } },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const { operacoes } = request.body as { operacoes: Parameters<typeof empurrarOperacoes>[2] };
      return empurrarOperacoes(banco, { empresaId, usuarioId: usuario.usuarioId }, operacoes);
    },
  );
};
