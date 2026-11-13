import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirMembro, exigirEscrita } from "../permissoes.js";
import {
  AtendimentoEntradaSchema,
  AtendimentoRespostaSchema,
  ID,
  ListaAtendimentosSchema,
  SegmentoAtendimentoSchema,
} from "../esquemas.js";
import {
  excluirAtendimento,
  listarAtendimentos,
  obterAtendimento,
  salvarAtendimento,
  type EntradaAtendimento,
} from "../servicos/atendimentos.js";

const EmpresaParams = Type.Object({ empresaId: ID });
const AtendimentoParams = Type.Object({ empresaId: ID, atendimentoId: ID });
const ListaQuery = Type.Object({
  busca: Type.Optional(Type.String({ maxLength: 200 })),
  segmento: Type.Optional(SegmentoAtendimentoSchema),
  de: Type.Optional(Type.Integer({ minimum: 0 })),
  ate: Type.Optional(Type.Integer({ minimum: 0 })),
  pagina: Type.Optional(Type.Integer({ minimum: 1 })),
  tamanho: Type.Optional(Type.Integer({ minimum: 1, maximum: 100 })),
});
const ExcluirQuery = Type.Object({ baseRevisao: Type.Integer({ minimum: 1 }) });

export const rotasAtendimentos: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, autenticacao } = opcoes;

  app.get(
    "/empresas/:empresaId/atendimentos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Atendimentos"],
        summary: "Pesquisa atendimentos da empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        querystring: ListaQuery,
        response: { 200: ListaAtendimentosSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      const filtros = request.query as {
        busca?: string;
        segmento?: string;
        de?: number;
        ate?: number;
        pagina?: number;
        tamanho?: number;
      };
      return listarAtendimentos(banco, empresaId, filtros);
    },
  );

  app.post(
    "/empresas/:empresaId/atendimentos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Atendimentos"],
        summary: "Cria um atendimento",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        body: AtendimentoEntradaSchema,
        response: { 201: AtendimentoRespostaSchema, ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const entrada = request.body as EntradaAtendimento;
      const atendimento = await salvarAtendimento(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        entrada,
      );
      return reply.code(201).send(atendimento);
    },
  );

  app.get(
    "/empresas/:empresaId/atendimentos/:atendimentoId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Atendimentos"],
        summary: "Obtém um atendimento",
        security: [{ bearerAuth: [] }],
        params: AtendimentoParams,
        response: { 200: AtendimentoRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, atendimentoId } = request.params as {
        empresaId: string;
        atendimentoId: string;
      };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      return obterAtendimento(banco, empresaId, atendimentoId);
    },
  );

  app.put(
    "/empresas/:empresaId/atendimentos/:atendimentoId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Atendimentos"],
        summary: "Atualiza um atendimento com controle de revisão",
        security: [{ bearerAuth: [] }],
        params: AtendimentoParams,
        body: AtendimentoEntradaSchema,
        response: { 200: AtendimentoRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, atendimentoId } = request.params as {
        empresaId: string;
        atendimentoId: string;
      };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const entrada = request.body as EntradaAtendimento;
      return salvarAtendimento(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        { ...entrada, id: atendimentoId },
      );
    },
  );

  app.delete(
    "/empresas/:empresaId/atendimentos/:atendimentoId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Atendimentos"],
        summary: "Exclui um atendimento (soft delete)",
        security: [{ bearerAuth: [] }],
        params: AtendimentoParams,
        querystring: ExcluirQuery,
        response: { 200: AtendimentoRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, atendimentoId } = request.params as {
        empresaId: string;
        atendimentoId: string;
      };
      const { baseRevisao } = request.query as { baseRevisao: number };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      return excluirAtendimento(
        banco,
        { empresaId, usuarioId: usuario.usuarioId },
        atendimentoId,
        Number(baseRevisao),
      );
    },
  );
};
