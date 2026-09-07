using System.Text.Json;
using System.IO;

namespace AirTypePC;

/// <summary>
/// 持久化设置，存储于 %APPDATA%\AirTypePC\config.json。
/// 配对码首启随机生成后持久化（跨重启不变），可手动重新生成。
/// </summary>
public sealed class AppConfig
{
    public string MachineName { get; set; } = Environment.MachineName;
    public int TcpPort { get; set; } = 47555;
    public int UdpPort { get; set; } = 47556;
    public string PairCode { get; set; } = "";

    /// <summary>"clipboard"（默认）或 "keys"（逐字键入）。</summary>
    public string PasteMode { get; set; } = "clipboard";

    public bool MinimizeToTrayOnStart { get; set; }
    public bool AutoOpenAtStartup { get; set; }

    /// <summary>专一性匹配：开启后同时只允许一台手机配对（已配对则拒绝新设备）。</summary>
    public bool ExclusivePairing { get; set; }

    private static readonly string Dir = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "AirTypePC");
    private static readonly string FilePath = Path.Combine(Dir, "config.json");

    public static AppConfig Load()
    {
        try
        {
            if (File.Exists(FilePath))
            {
                var cfg = JsonSerializer.Deserialize<AppConfig>(File.ReadAllText(FilePath));
                if (cfg is not null)
                {
                    if (string.IsNullOrEmpty(cfg.PairCode) || cfg.PairCode.Length != 4)
                        cfg.RegenerateCode();
                    if (cfg.TcpPort is < 1024 or > 65535) cfg.TcpPort = 47555;
                    if (cfg.UdpPort is < 1024 or > 65535) cfg.UdpPort = 47556;
                    if (string.IsNullOrWhiteSpace(cfg.MachineName)) cfg.MachineName = Environment.MachineName;
                    return cfg;
                }
            }
        }
        catch
        {
            // 设置读取失败时回退到默认值
        }

        var fresh = new AppConfig();
        fresh.RegenerateCode();
        return fresh;
    }

    public void RegenerateCode()
    {
        PairCode = Random.Shared.Next(0, 10000).ToString("D4");
        Save();
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Dir);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this,
                new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // 非致命：本次运行设置不持久化
        }
    }
}
