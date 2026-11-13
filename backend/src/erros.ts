export type CodigoErro =
  | "VALIDACAO"
  | "NAO_AUTENTICADO"
  | "SEM_PERMISSAO"
  | "NAO_ENCONTRADO"
  | "CONFLITO"
  | "LIMITE_EXCEDIDO"
  | "CREDENCIAIS_INVALIDAS"
  | "TOKEN_INVALIDO"
  | "INDISPONIVEL"
  | "ERRO_INTERNO";

export class ErroApi extends Error {
  constructor(
    readonly status: number,
    readonly codigo: CodigoErro,
    mensagem: string,
    readonly detalhes?: unknown,
  ) {
    super(mensagem);
    this.name = "ErroApi";
  }
}

export const erros = {
  validacao: (mensagem = "Dados inválidos.", detalhes?: unknown) =>
    new ErroApi(400, "VALIDACAO", mensagem, detalhes),
  naoAutenticado: (mensagem = "Sessão ausente ou expirada.") =>
    new ErroApi(401, "NAO_AUTENTICADO", mensagem),
  semPermissao: (mensagem = "Você não tem permissão para esta ação.") =>
    new ErroApi(403, "SEM_PERMISSAO", mensagem),
  naoEncontrado: (mensagem = "Registro não encontrado.") =>
    new ErroApi(404, "NAO_ENCONTRADO", mensagem),
  conflito: (mensagem = "O registro foi alterado por outra pessoa.", detalhes?: unknown) =>
    new ErroApi(409, "CONFLITO", mensagem, detalhes),
  limite: (mensagem = "Limite excedido.") =>
    new ErroApi(429, "LIMITE_EXCEDIDO", mensagem),
  credenciais: () =>
    new ErroApi(401, "CREDENCIAIS_INVALIDAS", "E-mail ou senha não conferem."),
  tokenInvalido: (mensagem = "Token inválido ou expirado.") =>
    new ErroApi(400, "TOKEN_INVALIDO", mensagem),
};
