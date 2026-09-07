using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Reflection;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using Drawing = System.Drawing;

namespace AirTypePC;

public partial class MainWindow : Window
{
    public AppConfig Config { get; } = AppConfig.Load();

    private Server? _server;
    private Beacon? _beacon;
    private SettingsWindow? _settings;
    private IDisposable? _logSub;
    private readonly ObservableCollection<string> _devices = new();
    private bool _exiting;
    internal bool _trayHidden;
    private Icon? _appIcon;

    public MainWindow()
    {
        InitializeComponent();
        DeviceList.ItemsSource = _devices;
        VerText.Text = "v" + (Assembly.GetExecutingAssembly().GetName().Version?.ToString(3) ?? "1.0.6");

        // 窗口图标（透明 logo）
        Icon = LoadLogoImage();
        _appIcon = BuildAppIcon();
        ((App)System.Windows.Application.Current).SetupTray(this);

        StartServices();
        Config.Save();

        SetCode(Config.PairCode);
        UpdateClientsUi();
        UpdateLinkInfo();
        AttachClickGlow(BtnRegen);
        AttachClickGlow(BtnDisconnect);

        _logSub = AppLog.Subscribe(AppendLog);
        foreach (var line in AppLog.Snapshot()) AppendLog(line);
    }

    public Drawing.Icon AppIcon => _appIcon!;

    private static BitmapImage LoadLogoImage()
    {
        var res = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Assets/logo-pc.png"));
        var img = new BitmapImage();
        img.BeginInit();
        img.CacheOption = BitmapCacheOption.OnLoad;
        img.StreamSource = res!.Stream;
        img.EndInit();
        img.Freeze();
        return img;
    }

    // ---------------- 服务 ----------------

    private void StartServices()
    {
        try
        {
            _server = new Server(Config, AppLog.Write, PasteText, () => Dispatcher.BeginInvoke(UpdateClientsUi));
            _server.Start();
            _beacon = new Beacon(Config);
            _beacon.Start();
            AppLog.Write("AirTypePC 已启动，等待手机配对…");
        }
        catch (Exception ex)
        {
            AppLog.Write("启动失败（端口可能被占用）：" + ex.Message);
            StatusText.Text = "LINK · 错误";
            StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0x6B, 0x6B));
        }
    }

    /// <summary>在 UI 线程上执行上屏（剪贴板需要 STA）。</summary>
    private string? PasteText(string text)
    {
        if (!Dispatcher.CheckAccess())
            return Dispatcher.Invoke(() => Paster.Paste(text, Config.PasteMode));
        return Paster.Paste(text, Config.PasteMode);
    }

    // ---------------- UI 更新 ----------------

    internal void UpdateClientsUi()
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(UpdateClientsUi);
            return;
        }

        _devices.Clear();
        var list = _server?.ClientSummaries() ?? new List<string>();
        foreach (var d in list) _devices.Add(d);

        var count = _devices.Count;
        DeviceCountText.Text = $"{count} 台";
        DeviceEmpty.Visibility = count == 0 ? Visibility.Visible : Visibility.Collapsed;
        DeviceList.Visibility = count == 0 ? Visibility.Collapsed : Visibility.Visible;
        StatusText.Text = count == 0 ? "LINK · 等待连接" : $"LINK · 接收中 ({count})";
        StatusDot.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(
            (byte)(count == 0 ? 0xFF : 0x5E), (byte)(count == 0 ? 0xB4 : 0xE0), (byte)(count == 0 ? 0x43 : 0x7A)));
        BtnDisconnect.Visibility = count > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    internal void SetCode(string code)
    {
        var digits = (code ?? "").PadRight(4, '·');
        CodeT0.Text = digits.Length > 0 ? digits[0].ToString() : "·";
        CodeT1.Text = digits.Length > 1 ? digits[1].ToString() : "·";
        CodeT2.Text = digits.Length > 2 ? digits[2].ToString() : "·";
        CodeT3.Text = digits.Length > 3 ? digits[3].ToString() : "·";
    }

    private void UpdateLinkInfo()
    {
        var entries = NetUtil.GetIpv4Entries();
        var ip = entries.Count == 0 ? "IP —" : string.Join(" · ", entries.Select(e => $"{e.Address}"));
        var port = $"TCP {Config.TcpPort} / UDP {Config.UdpPort}";
        var mode = Config.PasteMode == "keys" ? "逐字" : "剪贴板";
        LinkInfoText.Text = $"{ip} · {port} · {mode}";
    }

    // ---------------- 事件 ----------------

    private void OnMinimize(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void OnClose(object sender, RoutedEventArgs e) => Close();

    private void OnRegenerate(object sender, RoutedEventArgs e)
    {
        Config.RegenerateCode();
        SetCode(Config.PairCode);
        AppLog.Write("已重新生成配对码：" + Config.PairCode);
    }

    private void OnDisconnect(object sender, RoutedEventArgs e)
    {
        _server?.DisconnectAllClients();
        AppLog.Write("已断开全部配对，回到未配对状态");
    }

    private void OnSettings(object sender, RoutedEventArgs e) => OpenSettings();

    internal void OpenSettings()
    {
        if (_settings is null)
        {
            _settings = new SettingsWindow(Config) { Owner = this };
            _settings.Closed += (_, _) => { _settings = null; };
        }
        _settings.Show();
        _settings.Activate();
        _settings.Topmost = true;
        _settings.Topmost = false;
    }

    public void BringToFront()
    {
        try
        {
            if (!IsVisible || WindowState == WindowState.Minimized)
            {
                Show();
                _trayHidden = false;
                WindowState = WindowState.Normal;
            }
            Activate();
            Topmost = true;
            Topmost = false;
        }
        catch
        {
            // 竞态下忽略
        }
    }

    protected override void OnKeyDown(System.Windows.Input.KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (Keyboard.Modifiers == ModifierKeys.Control && e.Key == Key.R)
        {
            OnRegenerate(this, new RoutedEventArgs());
            e.Handled = true;
        }
        else if (Keyboard.Modifiers == ModifierKeys.Control && e.Key == Key.O)
        {
            OpenSettings();
            e.Handled = true;
        }
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (!_exiting)
        {
            if (Config.MinimizeToTrayOnStart)
            {
                e.Cancel = true;
                Hide();
                _trayHidden = true;
                ((App)System.Windows.Application.Current).ShowTrayBalloon("AirType", "仍在后台运行。");
                return;
            }
            _exiting = true;
        }
        base.OnClosing(e);
    }

    public void ShutdownServices()
    {
        _logSub?.Dispose();
        _settings?.Close();
        _beacon?.Dispose();
        _server?.Dispose();
    }

    private void AppendLog(string line)
    {
        if (!Dispatcher.CheckAccess())
        {
            Dispatcher.BeginInvoke(() => AppendLog(line));
            return;
        }
        LogBox.AppendText(line + Environment.NewLine);
        if (LogBox.LineCount > 400)
            LogBox.Text = string.Join(Environment.NewLine, LogBox.Text.Split('\n').TakeLast(400));
        LogBox.ScrollToEnd();
    }

    private void AttachClickGlow(System.Windows.Controls.Button b)
    {
        if (b is null) return;
        b.MouseLeftButtonDown += (_, _) => SetGlow(b, 1.0);
        b.Click += (_, _) => { SetGlow(b, 0.6); FadeGlow(b); };
    }

    private static System.Windows.Controls.Border? Glow(System.Windows.Controls.Button b) =>
        b.Template?.FindName("Glow", b) as System.Windows.Controls.Border;

    private static void SetGlow(System.Windows.Controls.Button b, double o)
    {
        if (Glow(b) is { } g)
        {
            g.BeginAnimation(OpacityProperty, null);
            g.Opacity = o;
        }
    }

    private static void FadeGlow(System.Windows.Controls.Button b)
    {
        if (Glow(b) is not { } g) return;
        var anim = new DoubleAnimation(0.6, 0.0, TimeSpan.FromSeconds(2.0))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        g.BeginAnimation(OpacityProperty, anim);
    }

    protected override void OnContentRendered(EventArgs e)
    {
        base.OnContentRendered(e);
        if (ReadoutCaret is not null)
        {
            var blink = new DoubleAnimation(0.15, 1.0, TimeSpan.FromSeconds(0.55))
            {
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever
            };
            ReadoutCaret.BeginAnimation(OpacityProperty, blink);
        }
        if (_fadedIn) return;
        _fadedIn = true;
        Opacity = 0;
        var anim = new DoubleAnimation(0d, 1d, TimeSpan.FromMilliseconds(360))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        BeginAnimation(OpacityProperty, anim);
    }

    private bool _fadedIn;

    // ---------------- 图标 ----------------

    private static Drawing.Icon BuildAppIcon()
    {
        var res = System.Windows.Application.GetResourceStream(new Uri("pack://application:,,,/Assets/logo-pc.png"));
        using var bmp = new System.Drawing.Bitmap(res!.Stream);
        var hIcon = bmp.GetHicon();
        return Drawing.Icon.FromHandle(hIcon);
    }
}
