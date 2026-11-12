import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario, obterIp, obterUserAgent } from "../auth/autenticacao.js";
import { criarEmpresa } from "../auth/contas.js";
import {
  ConviteSchema,
  EmpresaSchema,
  ID,
  MembroSchema,
  PapelSchema,
  TokensSchema,
  UsuarioSchema,
  type Papel,
} from "../esquemas.js";
import { exigirMembro, exigirGestao, registrarAuditoria } from "../permissoes.js";
import {
  aceitarConvite,
  alterarPapel,
  cancelarConvite,
  criarConvite,
  listarConvites,
  listarMembros,
  removerMembro,
  visualizarConvite,
} from "../servicos/equipe.js";
import { agora } from "../banco.js";

const EmpresaParams = Type.Object({ empresaId: ID });
const MembroParams = Type.Object({ empresaId: ID, usuarioId: ID });
const ConviteParams = Type.Object({ empresaId: ID, conviteId: ID });
const TokenConviteParams = Type.Object({ token: Type.String() });

const CriarEmpresaEntrada = Type.Object({ nome: Type.String({ minLength: 1, maxLength: 120 }) });
const RenomearEmpresaEntrada = Type.Object({ nome: Type.String({ minLength: 1, maxLength: 120 }) });
const AlterarPapelEntrada = Type.Object({ papel: PapelSchema });
const CriarConviteEntrada = Type.Object({
  email: Type.String({ maxLength: 254 }),
  papel: Type.Union([
    Type.Literal("administrador"),
    Type.Literal("colaborador"),
    Type.Literal("leitor"),
  ]),
});
const AceitarConviteEntrada = Type.Object({
  nome: Type.Optional(Type.String({ maxLength: 120 })),
  senha: Type.Optional(Type.String({ maxLength: 128 })),
});

const ListaEmpresas = Type.Object({ itens: Type.Array(EmpresaSchema) });
const ListaMembros = Type.Object({ itens: Type.Array(MembroSchema) });
const ListaConvites = Type.Object({ itens: Type.Array(ConviteSchema) });
const ConviteCriadoResposta = Type.Object({
  convite: ConviteSchema,
  token: Type.Optional(Type.String()),
  emailEnviado: Type.Boolean(),
});
const ConvitePreview = Type.Object({
  empresaNome: Type.String(),
  email: Type.String(),
  papel: PapelSchema,
  expiraEm: Type.Integer(),
});
const ConviteAceito = Type.Object({
  usuarioId: ID,
  empresaId: ID,
  tokens: TokensSchema,
  usuario: UsuarioSchema,
});

export const rotasEmpresas: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, config, email, autenticacao } = opcoes;

  app.get(
    "/empresas",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Empresas"],
        summary: "Lista as empresas do usuário",
        security: [{ bearerAuth: [] }],
        response: { 200: ListaEmpresas, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const resultado = await banco.execute({
        sql: `SELECT e.id, e.nome, m.papel FROM membros m
              JOIN empresas e ON e.id = m.empresa_id
              WHERE m.usuario_id = ? AND m.excluido_em IS NULL AND e.excluido_em IS NULL
              ORDER BY e.nome`,
        args: [usuario.usuarioId],
      });
      return {
        itens: resultado.rows.map((linha) => ({
          id: String(linha.id),
          nome: String(linha.nome),
          papel: String(linha.papel) as Papel,
        })),
      };
    },
  );

  app.post(
    "/empresas",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Empresas"],
        summary: "Cria uma nova empresa",
        security: [{ bearerAuth: [] }],
        body: CriarEmpresaEntrada,
        response: { 201: EmpresaSchema, ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const entrada = request.body as { nome: string };
      const empresa = await criarEmpresa(banco, usuario.usuarioId, entrada.nome);
      await registrarAuditoria(banco, {
        empresaId: empresa.id,
        usuarioId: usuario.usuarioId,
        acao: "empresa.criada",
        entidade: "empresa",
        entidadeId: empresa.id,
        ip: obterIp(request),
      });
      return reply.code(201).send(empresa);
    },
  );

  app.get(
    "/empresas/:empresaId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Empresas"],
        summary: "Detalha uma empresa do usuário",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        response: { 200: EmpresaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      const resultado = await banco.execute({
        sql: "SELECT nome FROM empresas WHERE id = ? AND excluido_em IS NULL",
        args: [empresaId],
      });
      const linha = resultado.rows[0];
      if (!linha) throw new Error("Empresa inconsistente.");
      return { id: empresaId, nome: String(linha.nome), papel: membro.papel };
    },
  );

  app.patch(
    "/empresas/:empresaId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Empresas"],
        summary: "Renomeia a empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        body: RenomearEmpresaEntrada,
        response: { 200: EmpresaSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const entrada = request.body as { nome: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      const nome = entrada.nome.trim();
      await banco.execute({
        sql: "UPDATE empresas SET nome = ?, atualizado_em = ? WHERE id = ?",
        args: [nome, agora(), empresaId],
      });
      await registrarAuditoria(banco, {
        empresaId,
        usuarioId: usuario.usuarioId,
        acao: "empresa.renomeada",
        entidade: "empresa",
        entidadeId: empresaId,
        detalhes: { nome },
        ip: obterIp(request),
      });
      return { id: empresaId, nome, papel: membro.papel };
    },
  );

  app.get(
    "/empresas/:empresaId/membros",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Lista os membros da empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        response: { 200: ListaMembros, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      await exigirMembro(banco, empresaId, usuario.usuarioId);
      return { itens: await listarMembros(banco, empresaId) };
    },
  );

  app.patch(
    "/empresas/:empresaId/membros/:usuarioId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Altera o papel de um membro",
        security: [{ bearerAuth: [] }],
        params: MembroParams,
        body: AlterarPapelEntrada,
        response: { 200: MembroSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, usuarioId } = request.params as {
        empresaId: string;
        usuarioId: string;
      };
      const entrada = request.body as { papel: Papel };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      return alterarPapel(banco, { empresaId, usuarioId: usuario.usuarioId }, usuarioId, entrada.papel);
    },
  );

  app.delete(
    "/empresas/:empresaId/membros/:usuarioId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Remove o acesso de um membro",
        security: [{ bearerAuth: [] }],
        params: MembroParams,
        response: { 204: Type.Null(), ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId, usuarioId } = request.params as {
        empresaId: string;
        usuarioId: string;
      };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      await removerMembro(banco, { empresaId, usuarioId: usuario.usuarioId }, usuarioId);
      return reply.code(204).send();
    },
  );

  app.get(
    "/empresas/:empresaId/convites",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Lista os convites da empresa",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        response: { 200: ListaConvites, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      return { itens: await listarConvites(banco, empresaId) };
    },
  );

  app.post(
    "/empresas/:empresaId/convites",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Convida uma pessoa por e-mail",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        body: CriarConviteEntrada,
        response: { 201: ConviteCriadoResposta, ...respostasErro },
      },
      config: { rateLimit: { max: 10, timeWindow: "1 hour" } },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const entrada = request.body as {
        email: string;
        papel: "administrador" | "colaborador" | "leitor";
      };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      const resultado = await criarConvite(
        banco,
        config,
        email,
        { empresaId, usuarioId: usuario.usuarioId },
        entrada,
        (mensagem) => app.log.warn(mensagem),
      );
      return reply.code(201).send(resultado);
    },
  );

  app.delete(
    "/empresas/:empresaId/convites/:conviteId",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Equipe"],
        summary: "Cancela um convite pendente",
        security: [{ bearerAuth: [] }],
        params: ConviteParams,
        response: { 200: ConviteSchema, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId, conviteId } = request.params as {
        empresaId: string;
        conviteId: string;
      };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);
      return cancelarConvite(banco, { empresaId, usuarioId: usuario.usuarioId }, conviteId);
    },
  );

  app.get(
    "/convites/:token",
    {
      schema: {
        tags: ["Equipe"],
        summary: "Pré-visualiza um convite pelo token",
        params: TokenConviteParams,
        response: { 200: ConvitePreview, ...respostasErro },
      },
      config: { rateLimit: { max: 30, timeWindow: "1 minute" } },
    },
    async (request) => {
      const { token } = request.params as { token: string };
      return visualizarConvite(banco, token);
    },
  );

  app.post(
    "/convites/:token/aceitar",
    {
      preHandler: autenticacao.opcional,
      schema: {
        tags: ["Equipe"],
        summary: "Aceita um convite (com sessão existente ou criando a conta)",
        params: TokenConviteParams,
        body: AceitarConviteEntrada,
        response: { 200: ConviteAceito, ...respostasErro },
      },
      config: { rateLimit: { max: 20, timeWindow: "15 minutes" } },
    },
    async (request) => {
      const { token } = request.params as { token: string };
      const entrada = (request.body ?? {}) as { nome?: string; senha?: string };
      const resultado = await aceitarConvite(
        banco,
        token,
        { usuarioId: request.usuario?.usuarioId, ...entrada },
        obterIp(request),
        obterUserAgent(request),
      );
      const usuario = await banco.execute({
        sql: "SELECT id, nome, email, email_verificado_em FROM usuarios WHERE id = ?",
        args: [resultado.usuarioId],
      });
      const dados = usuario.rows[0]!;
      return {
        usuarioId: resultado.usuarioId,
        empresaId: resultado.empresaId,
        tokens: resultado.tokens,
        usuario: {
          id: String(dados.id),
          nome: String(dados.nome),
          email: String(dados.email),
          emailVerificado: dados.email_verificado_em !== null,
        },
      };
    },
  );
};
