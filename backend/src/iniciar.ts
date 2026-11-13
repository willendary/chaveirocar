import { criarServidor } from "./servidor.js";

const { app, config } = await criarServidor();

try {
  await app.listen({ host: config.host, port: config.porta });
  app.log.info(`ChaveiroCar API em ${config.urlPublica} (ambiente ${config.ambiente})`);
} catch (erro) {
  app.log.error({ err: erro }, "falha ao iniciar");
  process.exit(1);
}

for (const sinal of ["SIGINT", "SIGTERM"] as const) {
  process.on(sinal, async () => {
    app.log.info(`recebido ${sinal}, encerrando`);
    await app.close();
    process.exit(0);
  });
}
