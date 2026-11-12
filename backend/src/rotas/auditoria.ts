import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirGestao, exigirMembro } from "../permissoes.js";
import { AuditoriaSchema, ID } from "../esquemas.js";
import { lerJson } from "../dominio/comum.js";

const EmpresaParams = Type.Object({ empresaId: ID });
const ListaQuery = Type.Object({
  cursor: Type.Optional(Type.Integer({ minimum: 0 })),
  limite: Type.Optional(Type.Integer({ minimum: 1, maximum: 200 })),
});

const ListaAuditoria = Type.Object({
  itens: Type.Array(AuditoriaSchema),
  temMais: Type.Boolean(),
});

export const rotasAuditoria: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, autenticacao } = opcoes;

  app.get(
    "/empresas/:empresaId/auditoria",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Auditoria"],
        summary: "Lista as ações relevantes registradas na empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        querystring: ListaQuery,
        response: { 200: ListaAuditoria, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      const { cursor, limite } = request.query as { cursor?: number; limite?: number };
      const tamanho = Number(limite ?? 50);
      const resultado = await banco.execute({
        sql: `SELECT id, acao, entidade, entidade_id, usuario_id, detalhes, criado_em
              FROM auditoria WHERE empresa_id = ? AND id > ?
              ORDER BY id LIMIT ?`,
        args: [empresaId, Number(cursor ?? 0), tamanho + 1],
      });
      const linhas = resultado.rows.slice(0, tamanho);
      return {
        itens: linhas.map((linha) => ({
          id: Number(linha.id),
          acao: String(linha.acao),
          entidade: String(linha.entidade),
          entidadeId: String(linha.entidade_id),
          usuarioId: linha.usuario_id === null ? null : String(linha.usuario_id),
          detalhes: lerJson<Record<string, unknown>>(linha.detalhes) ?? {},
          criadoEm: Number(linha.criado_em),
        })),
        temMais: resultado.rows.length > tamanho,
      };
    },
  );
};
