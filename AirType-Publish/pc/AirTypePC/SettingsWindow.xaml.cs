using System.Reflection;
using System.Windows;
using System.Windows.Media.Animation;

namespace AirTypePC;

public partial class SettingsWindow : Window
{
    private readonly AppConfig _cfg;
    private bool _loading = true;

    public SettingsWindow(AppConfig cfg)
    {
        _cfg = cfg;
        InitializeComponent();
        VerText.Text = "AirTypePC v" + (Assembly.GetExecutingAssembly().GetName().Version?.ToString(3) ?? "1.0.0");

        NameBox.Text = _cfg.MachineName;
        RbClip.IsChecked = _cfg.PasteMode != "keys";
        RbKeys.IsChecked = _cfg.PasteMode == "keys";
        ChkTray.IsChecked = _cfg.MinimizeToTrayOnStart;
        ChkExclusive.IsChecked = _cfg.ExclusivePairing;

        NameBox.TextChanged += NameBox_TextChanged;
        RbClip.Checked += RbClip_Checked;
        RbKeys.Checked += RbKeys_Checked;
        ChkTray.Checked += ChkTray_Checked;
        ChkTray.Unchecked += ChkTray_Unchecked;
        ChkExclusive.Checked += ChkExclusive_Checked;
        ChkExclusive.Unchecked += ChkExclusive_Unchecked;

        _loading = false;
    }

    private void OnDone(object sender, RoutedEventArgs e) => Close();

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    private void NameBox_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e) =>
        ApplyName();

    private void RbClip_Checked(object sender, RoutedEventArgs e) => ApplyMode();

    private void RbKeys_Checked(object sender, RoutedEventArgs e) => ApplyMode();

    private void ChkTray_Checked(object sender, RoutedEventArgs e) => ApplyTray();

    private void ChkTray_Unchecked(object sender, RoutedEventArgs e) => ApplyTray();

    private void ChkExclusive_Checked(object sender, RoutedEventArgs e) => ApplyExclusive();

    private void ChkExclusive_Unchecked(object sender, RoutedEventArgs e) => ApplyExclusive();

    // 用代码挂事件（XAML 无事件绑定，更稳妥）
    private void ApplyName()
    {
        if (_loading) return;
        var name = NameBox.Text.Trim();
        if (name.Length > 0)
        {
            _cfg.MachineName = name;
            _cfg.Save();
        }
    }

    private void ApplyMode()
    {
        if (_loading) return;
        _cfg.PasteMode = RbKeys.IsChecked == true ? "keys" : "clipboard";
        _cfg.Save();
    }

    private void ApplyTray()
    {
        if (_loading) return;
        _cfg.MinimizeToTrayOnStart = ChkTray.IsChecked == true;
        _cfg.Save();
    }

    private void ApplyExclusive()
    {
        if (_loading) return;
        _cfg.ExclusivePairing = ChkExclusive.IsChecked == true;
        _cfg.Save();
    }

    protected override void OnContentRendered(EventArgs e)
    {
        base.OnContentRendered(e);
        if (_fadedIn) return;
        _fadedIn = true;
        Opacity = 0;
        var anim = new DoubleAnimation(0d, 1d, TimeSpan.FromMilliseconds(300))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        BeginAnimation(OpacityProperty, anim);
    }

    private bool _fadedIn;

}
