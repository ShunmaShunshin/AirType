using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;

namespace AirTypePC;

/// <summary>
/// UDP 发现（beacon）：每 1 秒向所有子网广播地址广播一帧发现包，供手机端列表展示。
/// 协议 v2：beacon 不含配对码，携带实例 ID 供手机识别 PC 重启。
/// </summary>
public sealed class Beacon : IDisposable
{
    private readonly AppConfig _cfg;
    private readonly CancellationTokenSource _cts = new();
    private readonly string _ver;
    private readonly string _instance;

    public Beacon(AppConfig cfg)
    {
        _cfg = cfg;
        _ver = typeof(Beacon).Assembly.GetName().Version?.ToString(3) ?? "1.0.0";
        _instance = Convert.ToHexString(RandomNumberGenerator.GetBytes(4)).ToLowerInvariant();
    }

    public string Instance => _instance;

    public void Start() => _ = LoopAsync(_cts.Token);

    private async Task LoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try { SendOnce(); } catch { }
            try { await Task.Delay(1000, ct); } catch (OperationCanceledException) { break; }
        }
    }

    /// <summary>立即广播一次；返回成功发送的目标数（供自检用）。</summary>
    public int SendOnce()
    {
        var payload = JsonSerializer.SerializeToUtf8Bytes(new
        {
            t = "beacon",
            v = 2,
            n = _cfg.MachineName,
            p = _cfg.TcpPort,
            i = _instance,
            ver = _ver
        });

        var sent = 0;
        using var udp = new UdpClient();
        udp.EnableBroadcast = true;
        foreach (var bcast in NetUtil.GetBroadcastAddresses())
        {
            try
            {
                udp.Send(payload, payload.Length, new IPEndPoint(bcast, _cfg.UdpPort));
                sent++;
            }
            catch { }
        }
        return sent;
    }

    public void Dispose()
    {
        _cts.Cancel();
        _cts.Dispose();
    }
}
