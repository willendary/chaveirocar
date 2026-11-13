import type { FastifyReply, FastifyRequest, preHandlerHookHandler } from "fastify";
import type { Banco } from "../banco.js";
import { erros } from "../erros.js";
import { autenticar, type SessaoAutenticada } from "./sessoes.js";

declare module "fastify" {
  interface FastifyRequest {
    usuario?: SessaoAutenticada;
  }
}

function tokenDoCabecalho(request: FastifyRequest): string {
  const cabecalho = request.headers.authorization;
  if (!cabecalho) return "";
  const [esquema, valor] = cabecalho.split(" ");
  if (!esquema || esquema.toLowerCase() !== "bearer" || !valor) return "";
  return valor.trim();
}

export function criarAutenticacao(banco: Banco): {
  exigir: preHandlerHookHandler;
  opcional: preHandlerHookHandler;
} {
  const exigir: preHandlerHookHandler = async (request: FastifyRequest, _reply: FastifyReply) => {
    const token = tokenDoCabecalho(request);
    if (!token) throw erros.naoAutenticado();
    request.usuario = await autenticar(banco, token);
  };

  const opcional: preHandlerHookHandler = async (request: FastifyRequest, _reply: FastifyReply) => {
    const token = tokenDoCabecalho(request);
    if (!token) return;
    try {
      request.usuario = await autenticar(banco, token);
    } catch {
      request.usuario = undefined;
    }
  };

  return { exigir, opcional };
}

export function exigirUsuario(request: FastifyRequest): SessaoAutenticada {
  if (!request.usuario) throw erros.naoAutenticado();
  return request.usuario;
}

export function obterIp(request: FastifyRequest): string {
  return request.ip || "";
}

export function obterUserAgent(request: FastifyRequest): string {
  return request.headers["user-agent"] ?? "";
}
