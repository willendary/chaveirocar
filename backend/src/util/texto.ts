import { randomUUID } from "node:crypto";

/** Normaliza para busca: sem acento, minúsculo, só letras/números separados por espaço. */
export function normalizar(valor: string | null | undefined): string {
  if (!valor) return "";
  return valor
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function texto(valor: unknown, max = 5000): string {
  if (typeof valor !== "string") return "";
  return valor.trim().slice(0, max);
}

export function inteiro(valor: unknown, padrao = 0): number {
  if (typeof valor === "number" && Number.isFinite(valor)) return Math.trunc(valor);
  if (typeof valor === "string" && valor.trim() !== "") {
    const numero = Number(valor);
    if (Number.isFinite(numero)) return Math.trunc(numero);
  }
  return padrao;
}

export function booleano(valor: unknown, padrao = false): boolean {
  if (typeof valor === "boolean") return valor;
  if (typeof valor === "number") return valor !== 0;
  if (typeof valor === "string") return ["true", "1", "sim"].includes(valor.toLowerCase());
  return padrao;
}

export function pagina(valor: unknown, padrao = 1, max = 10_000): number {
  const numero = Number(valor);
  if (!Number.isFinite(numero) || numero < 1) return padrao;
  return Math.min(Math.trunc(numero), max);
}

export function tamanhoPagina(valor: unknown, padrao = 30, max = 100): number {
  const numero = Number(valor);
  if (!Number.isFinite(numero) || numero < 1) return padrao;
  return Math.min(Math.trunc(numero), max);
}

export function novoRequestId(): string {
  return randomUUID();
}
