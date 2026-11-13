import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirMembro, exigirEscrita } from "../permissoes.js";
import { FichaEntradaSchema, FichaRespostaSchema, ID, ListaFichasSchema } from "../esquemas.js";
import {
  excluirFicha,
  listarFichas,
  obterFicha,
  salvarFicha,
  type EntradaFicha,
} from "../servicos/fichas.js";

const EmpresaParams = Type.Object({ empresaId: ID });
const FichaParams = Type.Object({ empresaId: ID, fichaId: ID });
const ListaQuery = Type.Object({
  busca: Type.Optional(Type.String({ maxLength: 200 })),
  pagina: Type.Optional(Type.Integer({ minimum: 1 })),
  tamanho: Type.Optional(Type.Integer({ minimum: 1, maximum: 100 })),
});
const ExcluirQuery = Type.Object({ baseRevisao: Type.Integer({ minimum: 1 }) });

export const rotasFichas: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, autenticacao } = opcoes;

  app.get(
    "/empresas/:empresaId/fichas",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fichas técnicas"],
        summary: "Pesquisa fichas técnicas da empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        querystring: ListaQuery,
        response: { 200: ListaFichasSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      const filtros = request.query as { busca?: string; pagina?: number; tamanho?: number };
      return listarFichas(banco, empresaId, filtros);
    },
  );

  app.post(
    "/empresas/:empresaId/fichas",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fichas técnicas"],
        summary: "Cria uma ficha técnica",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        body: FichaEntradaSchema,
        response: { 201: FichaRespostaSchema, ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const entrada = request.body as EntradaFicha;
      const ficha = await salvarFicha(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        entrada,
      );
      return reply.code(201).send(ficha);
    },
  );

  app.get(
    "/empresas/:empresaId/fichas/:fichaId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fichas técnicas"],
        summary: "Obtém uma ficha técnica",
        security: [{ bearerAuth: [] }],
        params: FichaParams,
        response: { 200: FichaRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fichaId } = request.params as { empresaId: string; fichaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      return obterFicha(banco, empresaId, fichaId);
    },
  );

  app.put(
    "/empresas/:empresaId/fichas/:fichaId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fichas técnicas"],
        summary: "Atualiza uma ficha técnica com controle de revisão",
        security: [{ bearerAuth: [] }],
        params: FichaParams,
        body: FichaEntradaSchema,
        response: { 200: FichaRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fichaId } = request.params as { empresaId: string; fichaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const entrada = request.body as EntradaFicha;
      return salvarFicha(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        { ...entrada, id: fichaId },
      );
    },
  );

  app.delete(
    "/empresas/:empresaId/fichas/:fichaId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fichas técnicas"],
        summary: "Exclui uma ficha técnica (soft delete)",
        security: [{ bearerAuth: [] }],
        params: FichaParams,
        querystring: ExcluirQuery,
        response: { 200: FichaRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fichaId } = request.params as { empresaId: string; fichaId: string };
      const { baseRevisao } = request.query as { baseRevisao: number };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      return excluirFicha(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        fichaId,
        Number(baseRevisao),
      );
    },
  );
};
