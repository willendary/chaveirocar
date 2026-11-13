import type { FastifyPluginAsync } from "fastify";
import { Type } from "@sinclair/typebox";
import type { ContextoRotas } from "./contexto.js";
import { respostasErro } from "./contexto.js";
import { exigirUsuario } from "../auth/autenticacao.js";
import { exigirGestao, exigirMembro, registrarAuditoria } from "../permissoes.js";
import { ID, ResultadoImportacaoSchema } from "../esquemas.js";
import { checksumDoBackup, importarBackupAndroid } from "../servicos/importacao.js";
import { erros } from "../erros.js";

const EmpresaParams = Type.Object({ empresaId: ID });

export const rotasImportacao: FastifyPluginAsync<ContextoRotas> = async (app, opcoes) => {
  const { banco, config, storage, autenticacao } = opcoes;

  app.post(
    "/empresas/:empresaId/importacoes/backup",
    {
      preHandler: autenticacao.exigir,
      schema: {
        tags: ["Migração"],
        summary: "Importa um backup ZIP do aplicativo Android (idempotente)",
        description:
          "Aceita o ZIP exportado pelo aplicativo (dados.json + fotos). Pode ser reenviado: " +
          "os registros já importados são ignorados por identificador de origem.",
        security: [{ bearerAuth: [] }],
        params: EmpresaParams,
        consumes: ["multipart/form-data"],
        response: { 200: ResultadoImportacaoSchema, ...respostasErro },
      },
      config: { rateLimit: { max: 10, timeWindow: "1 hour" } },
    },
    async (request) => {
      const usuario = exigirUsuario(request);
      const { empresaId } = request.params as { empresaId: string };
      const membro = await exigirMembro(banco, empresaId, usuario.usuarioId);
      exigirGestao(membro);

      const arquivo = await request.file({
        limits: { fileSize: 300 * 1024 * 1024, files: 1 },
      });
      if (!arquivo) throw erros.validacao("Envie o arquivo ZIP do backup no campo 'arquivo'.");
      const bytes = await arquivo.toBuffer();

      const resultado = await importarBackupAndroid(
        banco,
        storage,
        { empresaId, usuarioId: usuario.usuarioId },
        bytes,
        config.limites.fotosPorRegistro,
      );

      await registrarAuditoria(banco, {
        empresaId,
        usuarioId: usuario.usuarioId,
        acao: "backup.importado",
        entidade: "importacao",
        detalhes: {
          checksum: checksumDoBackup(bytes),
          bytes: bytes.length,
          atendimentos: resultado.atendimentosImportados,
          fichas: resultado.fichasImportadas,
          fotos: resultado.fotosImportadas,
          fotosAusentes: resultado.fotosAusentes,
        },
        ip: request.ip,
      });

      return resultado;
    },
  );
};
