import { createHash, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { normalizar } from "./texto.js";

export function novoId(): string {
  return randomUUID();
}

export function novoSegredo(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

export function sha256(valor: string): string {
  return createHash("sha256").update(valor, "utf8").digest("hex");
}

export function sha256Bytes(bytes: Buffer): string {
  return createHash("sha256").update(bytes).digest("hex");
}

export function iguaisSeguro(a: string, b: string): boolean {
  const hashA = createHash("sha256").update(a).digest();
  const hashB = createHash("sha256").update(b).digest();
  return timingSafeEqual(hashA, hashB);
}

export function normalizarEmail(email: string): string {
  return email.trim().toLowerCase();
}

export function emailValido(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) && email.length <= 254;
}

export function normalizarPlaca(placa: string): string {
  return normalizar(placa).replace(/\s+/g, "").toUpperCase();
}
