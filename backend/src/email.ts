import nodemailer, { type Transporter } from "nodemailer";
import type { Config } from "./config.js";

export interface Mensagem {
  para: string;
  assunto: string;
  texto: string;
  html?: string;
}

export interface ServicoEmail {
  enviar(mensagem: Mensagem): Promise<void>;
  disponivel: boolean;
}

/**
 * Sem SMTP configurado (desenvolvimento/teste), a mensagem é registrada no log.
 * Em produção a configuração exige SMTP; ver `config.ts`.
 */
export function criarEmail(config: Config, log: (mensagem: string) => void): ServicoEmail {
  let transporte: Transporter | null = null;
  if (config.email.smtpUrl) {
    const endereco = new URL(config.email.smtpUrl);
    const seguro = endereco.protocol === "smtps:";
    transporte = nodemailer.createTransport({
      host: endereco.hostname,
      port: Number(endereco.port || (seguro ? 465 : 587)),
      secure: seguro,
      auth: {
        user: decodeURIComponent(endereco.username),
        pass: decodeURIComponent(endereco.password),
      },
      connectionTimeout: 8_000,
      greetingTimeout: 8_000,
      socketTimeout: 10_000,
    });
  }

  return {
    disponivel: transporte !== null,
    async enviar(mensagem) {
      if (!transporte) {
        log(`[email:dev] para=${mensagem.para} assunto=${mensagem.assunto}\n${mensagem.texto}`);
        return;
      }
      await transporte.sendMail({
        from: config.email.remetente,
        to: mensagem.para,
        subject: mensagem.assunto,
        text: mensagem.texto,
        html: mensagem.html,
      });
    },
  };
}

export function linkConvite(config: Config, token: string): string {
  return `${config.urlPublica}/api/v1/convites/${encodeURIComponent(token)}/pagina`;
}

export function linkRedefinicao(config: Config, token: string): string {
  return `${config.urlPublica}/api/v1/redefinicao?token=${encodeURIComponent(token)}`;
}

export function linkVerificacao(config: Config, token: string): string {
  return `${config.urlPublica}/api/v1/verificacao?token=${encodeURIComponent(token)}`;
}
