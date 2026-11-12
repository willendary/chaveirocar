import { randomBytes, scrypt as scryptCallback, timingSafeEqual } from "node:crypto";

const CUSTO = 16_384;
const BLOCO = 8;
const PARALELISMO = 1;
const TAMANHO = 64;

function derivarSenha(
  senha: string,
  sal: Buffer,
  tamanho: number,
  opcoes: { N: number; r: number; p: number; maxmem: number },
): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    scryptCallback(senha, sal, tamanho, opcoes, (erro, derivada) => {
      if (erro) reject(erro);
      else resolve(derivada);
    });
  });
}

export async function gerarHashSenha(senha: string): Promise<string> {
  const sal = randomBytes(16);
  const derivada = await derivarSenha(senha, sal, TAMANHO, {
    N: CUSTO,
    r: BLOCO,
    p: PARALELISMO,
    maxmem: 64 * 1024 * 1024,
  });
  return ["scrypt", CUSTO, BLOCO, PARALELISMO, sal.toString("base64url"), derivada.toString("base64url")].join("$");
}

export async function conferirSenha(senha: string, hash: string): Promise<boolean> {
  const partes = hash.split("$");
  if (partes.length !== 6 || partes[0] !== "scrypt") return false;
  const [, custo, bloco, paralelismo, salBase64, hashBase64] = partes;
  if (!custo || !bloco || !paralelismo || !salBase64 || !hashBase64) return false;
  try {
    const sal = Buffer.from(salBase64, "base64url");
    const esperado = Buffer.from(hashBase64, "base64url");
    const derivada = await derivarSenha(senha, sal, esperado.length, {
      N: Number(custo),
      r: Number(bloco),
      p: Number(paralelismo),
      maxmem: 64 * 1024 * 1024,
    });
    return derivada.length === esperado.length && timingSafeEqual(derivada, esperado);
  } catch {
    return false;
  }
}

export function senhaAceitavel(senha: string): string | null {
  if (senha.length < 8) return "A senha deve ter pelo menos 8 caracteres.";
  if (senha.length > 128) return "A senha deve ter no máximo 128 caracteres.";
  if (!/[a-zA-Z]/.test(senha) || !/[0-9]/.test(senha)) {
    return "A senha deve misturar letras e números.";
  }
  return null;
}
