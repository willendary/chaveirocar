import type { FastifyPluginAsync, FastifyRequest } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirMembro, exigirEscrita } from "../permissoes.js";
import { erros } from "../erros.js";
import { ID, FotoRespostaSchema, ListaFotosSchema } from "../esquemas.js";
import {
  adicionarFoto,
  atualizarFoto,
  excluirFoto,
  lerFoto,
  listarFotos,
} from "../servicos/fotos.js";
import { obterAtendimento } from "../servicos/atendimentos.js";
import { obterFicha } from "../servicos/fichas.js";

const FotoParams = Type.Object({ empresaId: ID, fotoId: ID });
const AtendimentoFotosParams = Type.Object({ empresaId: ID, atendimentoId: ID });
const FichaFotosParams = Type.Object({ empresaId: ID, fichaId: ID });
const AtualizarFotoEntrada = Type.Object({
  baseRevisao: Type.Integer({ minimum: 1 }),
  categoria: Type.Optional(Type.Union([Type.Literal("CARRO"), Type.Literal("PROCEDIMENTO")])),
  legenda: Type.Optional(Type.String({ maxLength: 500 })),
  ordem: Type.Optional(Type.Integer({ minimum: 0 })),
});
const ExcluirFotoQuery = Type.Object({ baseRevisao: Type.Integer({ minimum: 1 }) });

interface EntradaMultipart {
  bytes: Buffer;
  mime: string;
  campos: Record<string, string>;
}

async function lerMultipart(request: FastifyRequest): Promise<EntradaMultipart> {
  let bytes: Buffer | null = null;
  let mime = "";
  const campos: Record<string, string> = {};
  for await (const parte of request.parts()) {
    if (parte.type === "file") {
      if (bytes) continue;
      bytes = await parte.toBuffer();
      mime = parte.mimetype;
    } else if (typeof parte.value === "string") {
      campos[parte.fieldname] = parte.value;
    }
  }
  if (!bytes) {
    throw erros.validacao("Envie o arquivo da foto no campo 'arquivo'.");
  }
  return { bytes, mime, campos };
}

export const rotasFotos: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, config, storage, autenticacao } = opcoes;

  app.get(
    "/empresas/:empresaId/atendimentos/:atendimentoId/fotos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Lista as fotos de um atendimento",
        security: [{ bearerAuth: [] }],
        params: AtendimentoFotosParams,
        response: { 200: ListaFotosSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, atendimentoId } = request.params as {
        empresaId: string;
        atendimentoId: string;
      };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      await obterAtendimento(banco, empresaId, atendimentoId);
      return { itens: await listarFotos(banco, empresaId, { atendimentoId }) };
    },
  );

  app.post(
    "/empresas/:empresaId/atendimentos/:atendimentoId/fotos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Envia uma foto para um atendimento (multipart/form-data)",
        security: [{ bearerAuth: [] }],
        params: AtendimentoFotosParams,
        consumes: ["multipart/form-data"],
        response: { 201: FotoRespostaSchema, ...respostasErro },
      },
      config: { rateLimit: { max: 60, timeWindow: "1 hour" } },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId, atendimentoId } = request.params as {
        empresaId: string;
        atendimentoId: string;
      };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const { bytes, mime, campos } = await lerMultipart(request);
      const foto = await adicionarFoto(
        banco,
        storage,
        { empresaId, usuarioId: usuario.usuarioId },
        {
          id: campos.id,
          atendimentoId,
          categoria: campos.categoria,
          legenda: campos.legenda,
          ordem: campos.ordem ? Number(campos.ordem) : 0,
          bytes,
          mimeInformado: mime,
        },
        config.limites.fotosPorRegistro,
      );
      return reply.code(201).send(foto);
    },
  );

  app.get(
    "/empresas/:empresaId/fichas/:fichaId/fotos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Lista as fotos de uma ficha técnica",
        security: [{ bearerAuth: [] }],
        params: FichaFotosParams,
        response: { 200: ListaFotosSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fichaId } = request.params as { empresaId: string; fichaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      await obterFicha(banco, empresaId, fichaId);
      return { itens: await listarFotos(banco, empresaId, { fichaId }) };
    },
  );

  app.post(
    "/empresas/:empresaId/fichas/:fichaId/fotos",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Envia uma foto para uma ficha técnica (multipart/form-data)",
        security: [{ bearerAuth: [] }],
        params: FichaFotosParams,
        consumes: ["multipart/form-data"],
        response: { 201: FotoRespostaSchema, ...respostasErro },
      },
      config: { rateLimit: { max: 60, timeWindow: "1 hour" } },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fichaId } = request.params as { empresaId: string; fichaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const { bytes, mime, campos } = await lerMultipart(request);
      const foto = await adicionarFoto(
        banco,
        storage,
        { empresaId, usuarioId: usuario.usuarioId },
        {
          id: campos.id,
          fichaId,
          categoria: campos.categoria ?? "PROCEDIMENTO",
          legenda: campos.legenda,
          ordem: campos.ordem ? Number(campos.ordem) : 0,
          bytes,
          mimeInformado: mime,
        },
        config.limites.fotosPorRegistro,
      );
      return reply.code(201).send(foto);
    },
  );

  app.get(
    "/empresas/:empresaId/fotos/:fotoId/arquivo",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Baixa o arquivo de uma foto autenticada",
        security: [{ bearerAuth: [] }],
        params: FotoParams,
        response: { 200: Type.String({ format: "binary" }), ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fotoId } = request.params as { empresaId: string; fotoId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      const { foto, bytes } = await lerFoto(banco, storage, empresaId, fotoId);
      return reply
        .header("Content-Type", foto.mimeType)
        .header("Content-Length", String(bytes.length))
        .header("Cache-Control", "private, max-age=3600")
        .header("ETag", `"${foto.checksum}"`)
        .send(bytes);
    },
  );

  app.patch(
    "/empresas/:empresaId/fotos/:fotoId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Atualiza categoria, legenda ou ordem da foto",
        security: [{ bearerAuth: [] }],
        params: FotoParams,
        body: AtualizarFotoEntrada,
        response: { 200: FotoRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fotoId } = request.params as { empresaId: string; fotoId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      const entrada = request.body as {
        baseRevisao: number;
        categoria?: string;
        legenda?: string;
        ordem?: number;
      };
      return atualizarFoto(banco, { empresaId, usuarioId: usuario.usuarioId }, fotoId, entrada);
    },
  );

  app.delete(
    "/empresas/:empresaId/fotos/:fotoId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Fotos"],
        summary: "Exclui uma foto (soft delete)",
        security: [{ bearerAuth: [] }],
        params: FotoParams,
        querystring: ExcluirFotoQuery,
        response: { 200: FotoRespostaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, fotoId } = request.params as { empresaId: string; fotoId: string };
      const { baseRevisao } = request.query as { baseRevisao: number };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirEscrita(membro);
      return excluirFoto(banco, { empresaId, usuarioId: usuario.usuarioId }, fotoId, Number(baseRevisao));
    },
  );
};
