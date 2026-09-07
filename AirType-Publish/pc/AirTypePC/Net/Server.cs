using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.IO;
using System.Text.Json;

namespace AirTypePC;

/// <summary>
/// TCP 服务端（协议 v2）：监听 0.0.0.0，换行分隔 JSON 帧。
/// 配对挑战-应答：手机连接后发 pair -> 服务端下发随机 nonce ->
/// 手机回 SHA-256(code + nonce) -> 校验通过才进入已认证态，可收 text 帧。
/// 配对码全程不出现于网络报文。
/// </summary>
public sealed class Server : IDisposable
{
    private sealed class ClientSession : IDisposable
    {
        public required TcpClient Tcp { get; init; }
        public NetworkStream? Stream { get; set; }
        public StreamReader? Reader { get; set; }
        public StreamWriter? Writer { get; set; }
        public IPAddress? Remote { get; set; }
        public bool Paired { get; set; }
        public string DeviceName { get; set; } = "";
        public string? ChallengeNonce { get; set; }
        public DateTime ChallengeUtc { get; set; }
        public int BadFrames { get; set; }
        public DateTime LastActiveUtc { get; set; } = DateTime.UtcNow;
        public void Touch() => LastActiveUtc = DateTime.UtcNow;
        public void Dispose()
        {
            try { Tcp.Close(); } catch { }
            try { Stream?.Dispose(); } catch { }
        }
    }

    private readonly AppConfig _cfg;
    private readonly Action<string> _log;
    private readonly Func<string, string?> _paste;
    private readonly Action _pairedChanged;

    private readonly object _sync = new();
    private readonly List<ClientSession> _sessions = new();
    private readonly CancellationTokenSource _cts = new();
    private TcpListener? _listener;
    private bool _started;

    public Server(AppConfig cfg, Action<string> log, Func<string, string?> paste, Action pairedChanged)
    {
        _cfg = cfg;
        _log = log;
        _paste = paste;
        _pairedChanged = pairedChanged;
    }

    public bool Running { get; private set; }

    public List<string> ClientSummaries()
    {
        lock (_sync)
            return _sessions
                .Where(s => s.Paired)
                .Select(s => string.IsNullOrEmpty(s.DeviceName) ? s.Remote?.ToString() ?? "?" : $"{s.DeviceName} ({s.Remote})")
                .ToList();
    }

    public void DisconnectAllClients()
    {
        List<ClientSession> paired;
        lock (_sync) paired = _sessions.Where(s => s.Paired).ToList();
        foreach (var s in paired)
        {
            try { Reply(s, new { t = "bye", e = "unpaired-by-user" }); } catch { }
            s.Tcp.Close();
        }
        _pairedChanged();
    }

    public void Start()
    {
        _listener = new TcpListener(IPAddress.Any, _cfg.TcpPort);
        _listener.Start();
        Running = true;
        _started = true;
        _log($"服务已启动：监听 0.0.0.0:{_cfg.TcpPort}（配对码 {_cfg.PairCode}，仅显示在屏幕上）");
        _ = AcceptLoopAsync(_cts.Token);
        _ = IdleWatcherAsync(_cts.Token);
    }

    public void Stop()
    {
        _cts.Cancel();
        try { _listener?.Stop(); } catch { }
        List<ClientSession> snapshot;
        lock (_sync)
        {
            snapshot = _sessions.ToList();
            _sessions.Clear();
        }
        foreach (var s in snapshot)
        {
            try { Reply(s, new { t = "bye", e = "shutdown" }); } catch { }
            s.Dispose();
        }
        Running = false;
    }

    private async Task AcceptLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _started)
        {
            TcpClient tcp;
            try { tcp = await _listener!.AcceptTcpClientAsync(ct); }
            catch (OperationCanceledException) { break; }
            catch (Exception ex)
            {
                _log($"接受连接失败：{ex.Message}");
                await Task.Delay(1000, CancellationToken.None);
                continue;
            }
            _ = HandleClientAsync(tcp, ct);
        }
    }

    private async Task HandleClientAsync(TcpClient tcp, CancellationToken ct)
    {
        var s = new ClientSession { Tcp = tcp };
        try
        {
            tcp.NoDelay = true;
            s.Stream = tcp.GetStream();
            s.Reader = new StreamReader(s.Stream, new UTF8Encoding(false));
            s.Writer = new StreamWriter(s.Stream, new UTF8Encoding(false)) { AutoFlush = true };
            s.Remote = tcp.Client.RemoteEndPoint is IPEndPoint ep ? ep.Address : null;

            lock (_sync) _sessions.Add(s);
            _log($"新连接：{s.Remote}");

            string? line;
            while (!ct.IsCancellationRequested && (line = await s.Reader.ReadLineAsync(ct)) is not null)
            {
                s.Touch();
                if (line.Length == 0) continue;
                ProcessLine(s, line);
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex) when (ex is IOException or SocketException or ObjectDisposedException) { }
        catch (Exception ex) { _log($"连接处理异常：{ex.Message}"); }
        finally
        {
            lock (_sync) _sessions.Remove(s);
            _log(s.Paired
                ? $"断开：{(string.IsNullOrEmpty(s.DeviceName) ? s.Remote?.ToString() ?? "?" : s.DeviceName)} ({s.Remote})"
                : $"断开（未认证）：{s.Remote}");
            s.Dispose();
            _pairedChanged();
        }
    }

    private void ProcessLine(ClientSession s, string line)
    {
        JsonDocument doc;
        try { doc = JsonDocument.Parse(line); }
        catch
        {
            s.BadFrames++;
            if (s.BadFrames >= 5) { _log($"坏数据过多，断开 {s.Remote}"); s.Tcp.Close(); }
            return;
        }

        using (doc)
        {
            var root = doc.RootElement;
            var t = root.TryGetProperty("t", out var tv) ? tv.GetString() : null;
            switch (t)
            {
                case "pair": HandlePairRequest(s, root); break;
                case "auth": HandleAuth(s, root); break;
                case "text": HandleText(s, root); break;
                case "ping": Reply(s, new { t = "pong" }); break;
                default:
                    Reply(s, new { t = "bye", e = "bad-message" });
                    s.Tcp.Close();
                    break;
            }
        }
    }

    private void HandlePairRequest(ClientSession s, JsonElement root)
    {
        _ = root;
        if (s.Paired)
        {
            Reply(s, new { t = "pair", ok = true, n = _cfg.MachineName });
            return;
        }
        var nonce = Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant();
        s.ChallengeNonce = nonce;
        s.ChallengeUtc = DateTime.UtcNow;
        Reply(s, new { t = "challenge", n = nonce });
    }

    private void HandleAuth(ClientSession s, JsonElement root)
    {
        if (s.Paired) return;

        if (_cfg.ExclusivePairing && PairedCount() >= 1)
        {
            _log($"拒绝新设备：专一性匹配已开启且已有设备在线（{s.Remote}）");
            Reply(s, new { t = "pair", ok = false, e = "busy" });
            s.Tcp.Close();
            _pairedChanged();
            return;
        }

        var nonce = s.ChallengeNonce;
        if (nonce is null || (DateTime.UtcNow - s.ChallengeUtc).TotalSeconds > 15)
        {
            Reply(s, new { t = "bye", e = "challenge-expired" });
            s.Tcp.Close();
            return;
        }
        var h = root.TryGetProperty("h", out var hv) ? hv.GetString() ?? "" : "";
        var d = root.TryGetProperty("d", out var dv) ? dv.GetString() ?? "" : "";
        var expected = HashCode(_cfg.PairCode, nonce);
        if (string.Equals(h, expected, StringComparison.Ordinal))
        {
            s.Paired = true;
            s.DeviceName = d;
            s.ChallengeNonce = null;
            _log($"认证成功：{(string.IsNullOrEmpty(d) ? s.Remote?.ToString() : d)} ({s.Remote})");
            Reply(s, new { t = "pair", ok = true, n = _cfg.MachineName });
            _pairedChanged();
        }
        else
        {
            _log($"认证失败（码错误）：{s.Remote}");
            Reply(s, new { t = "pair", ok = false, e = "bad-code" });
            s.Tcp.Close();
            _pairedChanged();
        }
    }

    private void HandleText(ClientSession s, JsonElement root)
    {
        if (!s.Paired)
        {
            Reply(s, new { t = "bye", e = "unauthenticated" });
            s.Tcp.Close();
            return;
        }
        var id = root.TryGetProperty("id", out var iv) && iv.TryGetInt64(out var idv) ? idv : 0;
        var text = root.TryGetProperty("s", out var sv) ? sv.GetString() ?? "" : "";
        if (text.Length == 0)
        {
            Reply(s, new { t = "ack", id, ok = true });
            return;
        }
        var shown = text.Length > 40 ? text[..40] + "…" : text;
        _log($"[{(string.IsNullOrEmpty(s.DeviceName) ? "?" : s.DeviceName)}] 收到 {text.Length} 字符：{shown.Replace("\n", "⏎")}");
        var err = _paste(text);
        Reply(s, new { t = "ack", id, ok = err is null, e = err ?? "" });
    }

    /// <summary>SHA-256(code + nonce) 的 64 位小写十六进制。</summary>
    public static string HashCode(string code, string nonce)
    {
        var input = Encoding.UTF8.GetBytes(code + nonce);
        return Convert.ToHexString(SHA256.HashData(input)).ToLowerInvariant();
    }

    private int PairedCount()
    {
        lock (_sync) return _sessions.Count(x => x.Paired);
    }

    private void Reply(ClientSession s, object payload)
    {
        try
        {
            var json = JsonSerializer.Serialize(payload);
            lock (s) { s.Writer?.WriteLine(json); }
        }
        catch
        {
            try { s.Tcp.Close(); } catch { }
        }
    }

    private async Task IdleWatcherAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try { await Task.Delay(5000, ct); }
            catch (OperationCanceledException) { break; }

            List<ClientSession> idle;
            lock (_sync)
            {
                var now = DateTime.UtcNow;
                idle = _sessions
                    .Where(s => (s.Paired && (now - s.LastActiveUtc).TotalSeconds > 45) ||
                                (!s.Paired && (now - s.LastActiveUtc).TotalSeconds > 20))
                    .ToList();
            }
            foreach (var s in idle)
            {
                _log($"心跳/认证超时，断开 {s.Remote}");
                s.Tcp.Close();
            }
        }
    }

    public void Dispose()
    {
        Stop();
        _cts.Dispose();
    }
}
