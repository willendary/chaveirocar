import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { exigirUsuario, obterIp, obterUserAgent } from "../auth/autenticacao.js";
import { criarEmpresa, criarUsuario, listarEmpresasDoUsuario, obterUsuarioPorEmail } from "../auth/contas.js";
import { conferirSenha, gerarHashSenha, senhaAceitavel } from "../auth/senha.js";
import { criarSessao, renovarSessao, revogarSessao, revogarSessaoDoRefresh, revogarTodasAsSessoes } from "../auth/sessoes.js";
import { consumirTokenConta, criarTokenConta } from "../auth/tokensConta.js";
import { linkRedefinicao, linkVerificacao } from "../email.js";
import { erros } from "../erros.js";
import {
  EmpresaSchema,
  ErroSchema,
  TokensSchema,
  UsuarioSchema,
} from "../esquemas.js";
import { registrarAuditoria } from "../permissoes.js";
import { agora } from "../banco.js";
import { normalizarEmail } from "../util/id.js";
import { texto } from "../util/texto.js";

const CadastroEntrada = Type.Object({
  nome: Type.String({ minLength: 1, maxLength: 120 }),
  email: Type.String({ maxLength: 254 }),
  senha: Type.String({ minLength: 8, maxLength: 128 }),
  empresa: Type.Optional(Type.String({ maxLength: 120 })),
});

const LoginEntrada = Type.Object({
  email: Type.String({ maxLength: 254 }),
  senha: Type.String({ maxLength: 128 }),
});

const RenovarEntrada = Type.Object({ refresh: Type.String({ minLength: 10 }) });

const SessaoResposta = Type.Object({
  usuario: UsuarioSchema,
  tokens: TokensSchema,
  empresas: Type.Array(EmpresaSchema),
  emailEnviado: Type.Optional(Type.Boolean()),
  verificacaoToken: Type.Optional(Type.String()),
});

const EuResposta = Type.Object({
  usuario: UsuarioSchema,
  empresas: Type.Array(EmpresaSchema),
});

const MensagemResposta = Type.Object({
  mensagem: Type.String(),
  emailEnviado: Type.Optional(Type.Boolean()),
  redefinicaoToken: Type.Optional(Type.String()),
});

const EsqueciSenhaEntrada = Type.Object({ email: Type.String({ maxLength: 254 }) });
const RedefinirSenhaEntrada = Type.Object({
  token: Type.String({ minLength: 10 }),
  senha: Type.String({ minLength: 8, maxLength: 128 }),
});
const TokenEntrada = Type.Object({ token: Type.String({ minLength: 10 }) });

const respostasErro = {
  400: ErroSchema,
  401: ErroSchema,
  403: ErroSchema,
  404: ErroSchema,
  409: ErroSchema,
  429: ErroSchema,
};

export const rotasAuth: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, config, email, autenticacao } = opcoes;

  function usuarioResposta(usuario: {
    id: string;
    nome: string;
    email: string;
    emailVerificadoEm: number | null;
  }) {
    return {
      id: usuario.id,
      nome: usuario.nome,
      email: usuario.email,
      emailVerificado: usuario.emailVerificadoEm !== null,
    };
  }

  async function enviarVerificacao(
    usuarioId: string,
    destino: string,
  ): Promise<{ token: string; emailEnviado: boolean }> {
    const token = await criarTokenConta(banco, usuarioId, "verificacao_email");
    let emailEnviado = email.disponivel;
    try {
      await email.enviar({
        para: destino,
        assunto: "Confirme seu e-mail no ChaveiroCar",
        texto: [
          "Confirme seu e-mail para proteger sua conta do ChaveiroCar.",
          "",
          `Abra este link: ${linkVerificacao(config, token)}`,
          "",
          "O link vale por 24 horas.",
        ].join("\n"),
      });
    } catch (erro) {
      emailEnviado = false;
      app.log.warn(`Falha ao enviar verificação para ${destino}: ${(erro as Error).message}`);
    }
    return { token, emailEnviado };
  }

  app.post(
    "/auth/cadastro",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Cria uma conta e a primeira empresa",
        body: CadastroEntrada,
        response: { 201: SessaoResposta, ...respostasErro },
      },
      config: { rateLimit: { max: 10, timeWindow: "1 minute" } },
    },
    async (request, reply) => {
      const entrada = request.body as {
        nome: string;
        email: string;
        senha: string;
        empresa?: string;
      };
      const usuario = await criarUsuario(banco, entrada);
      const empresa = await criarEmpresa(
        banco,
        usuario.id,
        entrada.empresa ?? "Minha empresa",
      );
      const tokens = await criarSessao(
        banco,
        usuario.id,
        obterIp(request),
        obterUserAgent(request),
      );
      const verificacao = await enviarVerificacao(usuario.id, usuario.email);
      await registrarAuditoria(banco, {
        empresaId: empresa.id,
        usuarioId: usuario.id,
        acao: "conta.criada",
        entidade: "usuario",
        entidadeId: usuario.id,
        ip: obterIp(request),
      });
      return reply.code(201).send({
        usuario: usuarioResposta(usuario),
        tokens,
        empresas: [empresa],
        emailEnviado: verificacao.emailEnviado,
        verificacaoToken: config.exporTokenDev ? verificacao.token : undefined,
      });
    },
  );

  app.post(
    "/auth/login",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Entra com e-mail e senha",
        body: LoginEntrada,
        response: { 200: SessaoResposta, ...respostasErro },
      },
      config: { rateLimit: { max: 10, timeWindow: "1 minute" } },
    },
    async (request) => {
      const entrada = request.body as { email: string; senha: string };
      const emailNormalizado = normalizarEmail(entrada.email);
      const resultado = await banco.execute({
        sql: `SELECT id, nome, email, senha_hash, email_verificado_em, criado_em
              FROM usuarios WHERE email = ? AND excluido_em IS NULL`,
        args: [emailNormalizado],
      });
      const linha = resultado.rows[0];
      if (!linha || !(await conferirSenha(entrada.senha, String(linha.senha_hash)))) {
        throw erros.credenciais();
      }
      const usuarioId = String(linha.id);
      const tokens = await criarSessao(
        banco,
        usuarioId,
        obterIp(request),
        obterUserAgent(request),
      );
      await registrarAuditoria(banco, {
        usuarioId,
        acao: "sessao.iniciada",
        entidade: "sessao",
        ip: obterIp(request),
        detalhes: { userAgent: obterUserAgent(request).slice(0, 200) },
      });
      return {
        usuario: {
          id: usuarioId,
          nome: String(linha.nome),
          email: String(linha.email),
          emailVerificado: linha.email_verificado_em !== null,
        },
        tokens,
        empresas: await listarEmpresasDoUsuario(banco, usuarioId),
      };
    },
  );

  app.post(
    "/auth/renovar",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Renova o token de acesso usando o refresh token",
        body: RenovarEntrada,
        response: { 200: TokensSchema, ...respostasErro },
      },
      config: { rateLimit: { max: 60, timeWindow: "1 minute" } },
    },
    async (request) => {
      const entrada = request.body as { refresh: string };
      return renovarSessao(banco, entrada.refresh);
    },
  );

  app.post(
    "/auth/sair",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Autenticação"],
        summary: "Encerra a sessão deste dispositivo",
        security: [{ bearerAuth: [] }],
        body: Type.Object({ refresh: Type.Optional(Type.String()) }),
        response: { 204: Type.Null(), ...respostasErro },
      },
    },
    async (request, reply) => {
      const usuario = exigirUsuario(request);
      const entrada = (request.body ?? {}) as { refresh?: string };
      await revogarSessao(banco, usuario.sessaoId, usuario.usuarioId);
      await revogarSessaoDoRefresh(banco, entrada.refresh ?? "", usuario.usuarioId);
      await registrarAuditoria(banco, {
        usuarioId: usuario.usuarioId,
        acao: "sessao.encerrada",
        entidade: "sessao",
        entidadeId: usuario.sessaoId,
        ip: obterIp(request),
      });
      return reply.code(204).send();
    },
  );

  app.get(
    "/eu",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Autenticação"],
        summary: "Retorna o usuário autenticado e suas empresas",
        security: [{ bearerAuth: [] }],
        response: { 200: EuResposta, ...respostasErro },
      },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const linha = await banco.execute({
        sql: "SELECT id, nome, email, email_verificado_em, criado_em FROM usuarios WHERE id = ?",
        args: [usuario.usuarioId],
      });
      const dados = linha.rows[0]!;
      return {
        usuario: {
          id: String(dados.id),
          nome: String(dados.nome),
          email: String(dados.email),
          emailVerificado: dados.email_verificado_em !== null,
        },
        empresas: await listarEmpresasDoUsuario(banco, usuario.usuarioId),
      };
    },
  );

  app.post(
    "/auth/esqueci-senha",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Envia um link de redefinição de senha",
        body: EsqueciSenhaEntrada,
        response: { 200: MensagemResposta, ...respostasErro },
      },
      config: { rateLimit: { max: 5, timeWindow: "15 minutes" } },
    },
    async (request) => {
      const entrada = request.body as { email: string };
      const destino = normalizarEmail(entrada.email);
      const usuario = await obterUsuarioPorEmail(banco, destino);
      let tokenDev: string | undefined;
      if (usuario) {
        const token = await criarTokenConta(banco, usuario.id, "redefinicao_senha");
        tokenDev = config.exporTokenDev ? token : undefined;
        try {
          await email.enviar({
            para: destino,
            assunto: "Redefinição de senha do ChaveiroCar",
            texto: [
              "Recebemos um pedido para redefinir sua senha do ChaveiroCar.",
              "",
              `Abra este link: ${linkRedefinicao(config, token)}`,
              "",
              "O link vale por 1 hora. Se não foi você, ignore esta mensagem.",
            ].join("\n"),
          });
        } catch (erro) {
          app.log.warn(`Falha ao enviar redefinição para ${destino}: ${(erro as Error).message}`);
        }
      }
      return {
        mensagem: "Se o e-mail estiver cadastrado, enviaremos as instruções.",
        redefinicaoToken: tokenDev,
      };
    },
  );

  app.post(
    "/auth/redefinir-senha",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Define uma nova senha a partir do token recebido",
        body: RedefinirSenhaEntrada,
        response: { 204: Type.Null(), ...respostasErro },
      },
      config: { rateLimit: { max: 10, timeWindow: "15 minutes" } },
    },
    async (request, reply) => {
      const entrada = request.body as { token: string; senha: string };
      const problema = senhaAceitavel(entrada.senha);
      if (problema) throw erros.validacao(problema);
      const usuarioId = await consumirTokenConta(banco, "redefinicao_senha", entrada.token);
      const hash = await gerarHashSenha(entrada.senha);
      await banco.execute({
        sql: `UPDATE usuarios SET senha_hash = ?, atualizado_em = ?,
                email_verificado_em = COALESCE(email_verificado_em, ?)
              WHERE id = ?`,
        args: [hash, agora(), agora(), usuarioId],
      });
      await revogarTodasAsSessoes(banco, usuarioId);
      await registrarAuditoria(banco, {
        usuarioId,
        acao: "senha.redefinida",
        entidade: "usuario",
        entidadeId: usuarioId,
        ip: obterIp(request),
      });
      return reply.code(204).send();
    },
  );

  app.post(
    "/auth/verificar-email",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Confirma o e-mail a partir do token recebido",
        body: TokenEntrada,
        response: { 204: Type.Null(), ...respostasErro },
      },
    },
    async (request, reply) => {
      const entrada = request.body as { token: string };
      const usuarioId = await consumirTokenConta(banco, "verificacao_email", entrada.token);
      await banco.execute({
        sql: "UPDATE usuarios SET email_verificado_em = COALESCE(email_verificado_em, ?), atualizado_em = ? WHERE id = ?",
        args: [agora(), agora(), usuarioId],
      });
      await registrarAuditoria(banco, {
        usuarioId,
        acao: "email.verificado",
        entidade: "usuario",
        entidadeId: usuarioId,
        ip: obterIp(request),
      });
      return reply.code(204).send();
    },
  );

  app.post(
    "/auth/reenviar-verificacao",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Autenticação"],
        summary: "Reenvia o e-mail de confirmação",
        security: [{ bearerAuth: [] }],
        response: { 200: MensagemResposta, ...respostasErro },
      },
      config: { rateLimit: { max: 3, timeWindow: "15 minutes" } },
    },
    async (request) => {
      const sessao = exigirUsuario(request);
      const linha = await banco.execute({
        sql: "SELECT email, email_verificado_em FROM usuarios WHERE id = ?",
        args: [sessao.usuarioId],
      });
      const dados = linha.rows[0];
      if (!dados) throw erros.naoEncontrado("Usuário não encontrado.");
      if (dados.email_verificado_em !== null) {
        throw erros.validacao("Seu e-mail já está confirmado.");
      }
      const verificacao = await enviarVerificacao(sessao.usuarioId, String(dados.email));
      return {
        mensagem: verificacao.emailEnviado
          ? "E-mail de confirmação enviado."
          : "Não foi possível enviar o e-mail de confirmação.",
        emailEnviado: verificacao.emailEnviado,
      };
    },
  );

  app.get(
    "/verificacao",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Página de confirmação de e-mail (link do e-mail)",
        querystring: Type.Object({ token: Type.String() }),
        response: { 200: Type.String(), ...respostasErro },
      },
    },
    async (request, reply) => {
      const { token } = request.query as { token: string };
      const usuarioId = await consumirTokenConta(banco, "verificacao_email", token);
      await banco.execute({
        sql: "UPDATE usuarios SET email_verificado_em = COALESCE(email_verificado_em, ?), atualizado_em = ? WHERE id = ?",
        args: [agora(), agora(), usuarioId],
      });
      return reply.type("text/html; charset=utf-8").send(
        "<!doctype html><meta charset=utf-8><title>ChaveiroCar</title><h1>E-mail confirmado</h1><p>Volte ao aplicativo e entre na sua conta.</p>",
      );
    },
  );

  app.get(
    "/redefinicao",
    {
      schema: {
        tags: ["Autenticação"],
        summary: "Página de redefinição de senha (link do e-mail)",
        querystring: Type.Object({ token: Type.String() }),
        response: { 200: Type.String(), ...respostasErro },
      },
    },
    async (request, reply) => {
      const { token } = request.query as { token: string };
      return reply.type("text/html; charset=utf-8").send(
        `<!doctype html><meta charset=utf-8><title>ChaveiroCar</title><h1>Redefinir senha</h1>` +
          `<p>Abra o aplicativo ChaveiroCar e use este código para definir uma nova senha:</p>` +
          `<pre style="white-space:pre-wrap">${texto(token, 200)}</pre>`,
      );
    },
  );
};
