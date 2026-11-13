using System.Windows;
using ChaveiroCar.Windows.Services;

namespace ChaveiroCar.Windows;

public partial class CadastroWindow : Window
{
    private readonly ArmazenamentoLocal _local;

    public CadastroWindow(ArmazenamentoLocal local)
    {
        _local = local;
        InitializeComponent();
        Email.Focus();
    }

    private async void Criar_Click(object sender, RoutedEventArgs e)
    {
        Erro.Text = "";
        Criar.IsEnabled = false;
        try
        {
            using var api = new ClienteApi(_local.Estado.BaseUrl);
            var resultado = await api.CadastrarAsync(
                Nome.Text.Trim(), Email.Text.Trim(), Senha.Password, Empresa.Text.Trim());
            if (!resultado.Ok)
            {
                Erro.Text = "Não foi possível criar a conta. Confira os dados ou tente outro e-mail.";
                return;
            }

            _local.Estado.Acesso = api.Acesso;
            _local.Estado.Refresh = api.Refresh;
            _local.Estado.Email = resultado.Email ?? Email.Text.Trim();
            if (resultado.Empresas.Count > 0) _local.Estado.EmpresaId = resultado.Empresas[0].Id;
            _local.Salvar();

            MessageBox.Show(
                resultado.EmailEnviado
                    ? "Conta criada. Confirme o e-mail para proteger sua conta."
                    : "Conta criada, mas o serviço de e-mail não enviou a confirmação. A conta permanece em homologação.",
                "ChaveiroCar", MessageBoxButton.OK,
                resultado.EmailEnviado ? MessageBoxImage.Information : MessageBoxImage.Warning);
            DialogResult = true;
            Close();
        }
        catch (Exception ex)
        {
            Erro.Text = $"Falha ao criar conta: {ex.Message}";
        }
        finally
        {
            Criar.IsEnabled = true;
        }
    }
}
