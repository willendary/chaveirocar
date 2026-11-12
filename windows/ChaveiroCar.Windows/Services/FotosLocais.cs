using System.IO;

namespace ChaveiroCar.Windows.Services;

public static class FotosLocais
{
    public static IReadOnlyList<ArmazenamentoLocal.FotoLocal> Copiar(
        IEnumerable<string> arquivos,
        string registroId,
        bool ficha,
        int quantidadeExistente,
        string categoria,
        string legenda)
    {
        var origens = arquivos.ToList();
        if (quantidadeExistente + origens.Count > 20)
            throw new InvalidDataException("Cada registro aceita no máximo 20 fotos.");
        foreach (var origem in origens)
        {
            var extensao = Path.GetExtension(origem).ToLowerInvariant();
            if (extensao is not (".jpg" or ".jpeg" or ".png" or ".webp") ||
                new FileInfo(origem).Length > 15 * 1024 * 1024)
                throw new InvalidDataException("Use JPEG, PNG ou WebP de até 15 MB.");
        }

        var pasta = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ChaveiroCar", "fotos");
        Directory.CreateDirectory(pasta);
        var criadas = new List<ArmazenamentoLocal.FotoLocal>();
        try
        {
            for (var indice = 0; indice < origens.Count; indice++)
            {
                var origem = origens[indice];
                var uuid = Guid.NewGuid().ToString();
                var caminho = Path.Combine(pasta, uuid + Path.GetExtension(origem).ToLowerInvariant());
                File.Copy(origem, caminho);
                criadas.Add(new ArmazenamentoLocal.FotoLocal
                {
                    Uuid = uuid,
                    AtendimentoUuid = ficha ? "" : registroId,
                    FichaUuid = ficha ? registroId : "",
                    Categoria = categoria,
                    Legenda = legenda.Trim() is { Length: > 0 } rotulo
                        ? rotulo
                        : Path.GetFileNameWithoutExtension(origem),
                    Ordem = quantidadeExistente + indice,
                    CaminhoLocal = caminho,
                    Pendente = true,
                });
            }
            return criadas;
        }
        catch
        {
            foreach (var foto in criadas) File.Delete(foto.CaminhoLocal);
            throw;
        }
    }
}
