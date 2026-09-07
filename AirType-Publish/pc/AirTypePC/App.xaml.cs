using System.Windows;
using System.Windows.Forms;
using System.Windows.Threading;

namespace AirTypePC;

public partial class App : System.Windows.Application
{
    private const string MutexName = @"Local\AirTypePC_SingleInstance";
    private const string EventName = @"Local\AirTypePC_ShowEvent";

    private MainWindow? _main;
    private NotifyIcon? _tray;
    private System.Threading.Mutex? _mutex;
    private bool _exitRequested;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        if (e.Args.Length > 0 && e.Args[0] == "--selftest")
        {
            Shutdown(SelfTest.Run());
            return;
        }

        if (e.Args.Length > 0 && e.Args[0] == "--uicheck")
        {
            // 无头 UI 检查：构建并显示主界面+设置界面，3 秒后自动退出（exit 0 = 无异常）
            try
            {
                _main = new MainWindow();
                var settings = new SettingsWindow(_main.Config)
                {
                    Owner = _main,
                    WindowStartupLocation = WindowStartupLocation.CenterOwner
                };
                _main.Show();
                settings.Show();
                var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
                timer.Tick += (_, _) => { timer.Stop(); Shutdown(0); };
                timer.Start();
            }
            catch (Exception ex)
            {
                System.IO.File.WriteAllText(
                    System.IO.Path.Combine(AppContext.BaseDirectory, "uicheck-error.txt"), ex.ToString());
                Shutdown(1);
            }
            return;
        }

        // 单实例：第二次点击时通知已在运行的实例把窗口唤出后自行退出
        _mutex = new System.Threading.Mutex(true, MutexName, out var createdNew);
        if (!createdNew)
        {
            try
            {
                using var evt = EventWaitHandle.OpenExisting(EventName);
                evt.Set();
            }
            catch { }
            Shutdown(0);
            return;
        }

        _main = new MainWindow();
        _main.Closed += (_, _) =>
        {
            RequestExit();
        };
        _main.Show();

        // 后台线程监听“再次点击 exe”信号，收到后在 UI 线程唤出主窗口
        var watcher = new System.Threading.Thread(() =>
        {
            using var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, EventName);
            while (!_exitRequested && showEvent.WaitOne())
            {
                Dispatcher.Invoke(() => _main?.BringToFront());
            }
        });
        watcher.IsBackground = true;
        watcher.Start();
    }

    /// <summary>设置托盘图标。（在 MainWindow 创建时调用一次）</summary>
    public void SetupTray(MainWindow owner)
    {
        var menu = new ContextMenuStrip();
        var open = menu.Items.Add("打开 AirType 主界面");
        open.Click += (_, _) => owner.BringToFront();
        menu.Items.Add(new ToolStripSeparator());
        var exit = menu.Items.Add("退出 AirType");
        exit.Click += (_, _) => RequestExit();

        var icon = owner.AppIcon;
        _tray = new NotifyIcon
        {
            Icon = icon,
            Text = "AirType",
            ContextMenuStrip = menu,
            Visible = true,
        };
        _tray.DoubleClick += (_, _) => owner.BringToFront();
    }

    public void ShowTrayBalloon(string title, string text)
    {
        _tray?.ShowBalloonTip(1800, title, text, ToolTipIcon.Info);
    }

    public void RequestExit()
    {
        if (_exitRequested) return;
        _exitRequested = true;
        _main?.ShutdownServices();
        if (_tray is not null)
        {
            _tray.Visible = false;
            _tray.Dispose();
            _tray = null;
        }
        try { _mutex?.ReleaseMutex(); } catch { }
        Shutdown();
    }
}
