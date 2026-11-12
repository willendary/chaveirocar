import type { preHandlerHookHandler } from "fastify";
import type { Banco } from "../banco.js";
import type { Config } from "../config.js";
import type { ServicoEmail } from "../email.js";
import type { Armazenamento } from "../storage.js";
import { ErroSchema } from "../esquemas.js";

export interface ContextoRotas {
  banco: Banco;
  config: Config;
  email: ServicoEmail;
  storage: Armazenamento;
  autenticacao: {
    exigir: preHandlerHookHandler;
    opcional: preHandlerHookHandler;
  };
}

export const respostasErro = {
  400: ErroSchema,
  401: ErroSchema,
  403: ErroSchema,
  404: ErroSchema,
  409: ErroSchema,
  429: ErroSchema,
} as const;

