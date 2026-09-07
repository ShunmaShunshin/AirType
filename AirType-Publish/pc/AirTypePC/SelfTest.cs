using System.Net.Sockets;
using System.Text;
using System.IO;
using System.Text.Json;

namespace AirTypePC;

/// <summary>
/// 无界面协议自检：真实启动 Server 并用本地 TCP 客户端走一遍 v2 握手与业务帧。
/// 运行：AirTypePC.exe --selftest
/// 结果写入 exe 同目录 selftest-result.txt，PASS 即协议链路正常。
/// </summary>
internal static class SelfTest
{
    private static readonly List<string> Lines = new();
    private static int _fails;

    public static int Run()
    {
        // 在后台线程执行：服务端的 async 延续必须拿到无同步上下文（WPF 捕获 UI 线程会导致此处同步阻塞死锁）
        return Task.Run(() => RunCore()).GetAwaiter().GetResult();
    }

    private static int RunCore()
    {
        var port = 47655;
        var code = "1234";
        var cfg = new AppConfig { TcpPort = port, UdpPort = 47656, PairCode = code, MachineName = "SELFTEST-PC" };
        var logs = new List<string>();
        var server = new Server(cfg, logs.Add, _ => null, () => { });

        Report("AirTypePC 协议自检 v2 开始");
        try
        {
            server.Start();
            TestChallengeGood(port, code);
            TestChallengeBad(port, code);
            TestTextFlow(port, code);
            TestBadMessage(port, code);
            TestBeaconPayloadNoCode();
        }
        catch (Exception ex)
        {
            Fail($"自检异常：{ex.Message}");
            foreach (var l in logs) Report("    [server] " + l);
        }
        finally
        {
            server.Dispose();
        }

        var ok = _fails == 0;
        Report(ok ? "SELFTEST PASS" : $"SELFTEST FAIL ({_fails})");
        var result = string.Join(Environment.NewLine, Lines);
        try
        {
            File.WriteAllText(Path.Combine(AppContext.BaseDirectory, "selftest-result.txt"), result);
        }
        catch { }
        Console.Out.WriteLine(result);
        return ok ? 0 : 1;
    }

    private static void TestChallengeGood(int port, string code)
    {
        using var c = Connect(port);
        c.Send(JsonSerializer.Serialize(new { t = "pair", d = "TestPhone" }));
        var challenge = c.Read();
        RegisterCheck(challenge.Contains("\"challenge\"") && challenge.Contains("\"n\""), "收到 challenge（含随机 nonce）");
        using var doc = JsonDocument.Parse(challenge);
        var nonce = doc.RootElement.GetProperty("n").GetString()!;
        c.Send(JsonSerializer.Serialize(new { t = "auth", d = "TestPhone", h = Server.HashCode(code, nonce) }));
        var pair = c.Read();
        RegisterCheck(pair.Contains("\"pair\"") && pair.Contains("\"ok\":true"), "正确配对码认证成功");
    }

    private static void TestChallengeBad(int port, string code)
    {
        using var c = Connect(port);
        c.Send(JsonSerializer.Serialize(new { t = "pair", d = "TestPhone" }));
        var challenge = c.Read();
        using var doc = JsonDocument.Parse(challenge);
        var nonce = doc.RootElement.GetProperty("n").GetString()!;
        c.Send(JsonSerializer.Serialize(new { t = "auth", d = "TestPhone", h = Server.HashCode("9999", nonce) }));
        var pair = c.Read();
        RegisterCheck(pair.Contains("\"ok\":false") && pair.Contains("bad-code"), "错误配对码认证失败并返回 bad-code");
    }

    private static void TestTextFlow(int port, string code)
    {
        using var c = AuthConnect(port, code);
        c.Send(JsonSerializer.Serialize(new { t = "text", id = 3, s = "你好 Hello 👋 多行\n第二行" }));
        var ack = c.Read();
        RegisterCheck(ack.Contains("\"ack\"") && ack.Contains("\"ok\":true"), "文本发送得到 ack ok（含中文/emoji/换行）");
        c.Send(JsonSerializer.Serialize(new { t = "text", id = 4, s = "" }));
        var ackEmpty = c.Read();
        RegisterCheck(ackEmpty.Contains("\"ok\":true"), "空文本也返回 ack ok");
    }

    private static void TestBadMessage(int port, string code)
    {
        using var c = AuthConnect(port, code);
        c.Send(JsonSerializer.Serialize(new { t = "unknown-frame" }));
        var bye = c.Read();
        RegisterCheck(bye.Contains("\"bye\"") && bye.Contains("bad-message"), "未知帧返回 bye 并断开");
    }

    private static void TestBeaconPayloadNoCode()
    {
        var payload = JsonSerializer.Serialize(new
        {
            t = "beacon", v = 2, n = "PC", p = 47555, i = "aabbccdd", ver = "1.0.0"
        });
        RegisterCheck(!payload.Contains("paircode", StringComparison.OrdinalIgnoreCase)
                      && !payload.Contains("\"code\"", StringComparison.OrdinalIgnoreCase),
                      "beacon 载荷不包含配对码/码字段");
    }

    private sealed class TestClient : IDisposable
    {
        private readonly TcpClient _tcp;
        private readonly StreamReader _reader;
        private readonly StreamWriter _writer;

        public TestClient(int port)
        {
            _tcp = new TcpClient();
            _tcp.NoDelay = true;
            _tcp.Connect("127.0.0.1", port);
            _tcp.ReceiveTimeout = 4000;
            _reader = new StreamReader(_tcp.GetStream(), new UTF8Encoding(false));
            _writer = new StreamWriter(_tcp.GetStream(), new UTF8Encoding(false)) { AutoFlush = true };
        }

        public void Send(string line) => _writer.WriteLine(line);
        public string Read() => _reader.ReadLine() ?? throw new IOException("read returned null");
        public void Dispose() => _tcp.Dispose();
    }

    private static TestClient Connect(int port) => new(port);

    private static TestClient AuthConnect(int port, string code)
    {
        var c = Connect(port);
        c.Send(JsonSerializer.Serialize(new { t = "pair", d = "TestPhone" }));
        var challenge = c.Read();
        using var doc = JsonDocument.Parse(challenge);
        var nonce = doc.RootElement.GetProperty("n").GetString()!;
        c.Send(JsonSerializer.Serialize(new { t = "auth", d = "TestPhone", h = Server.HashCode(code, nonce) }));
        _ = c.Read(); // pair ok
        return c;
    }

    private static void RegisterCheck(bool cond, string name)
    {
        Report((cond ? "  [PASS] " : "  [FAIL] ") + name);
        if (!cond) _fails++;
    }

    private static void Fail(string msg)
    {
        Report("  [FAIL] " + msg);
        _fails++;
    }

    private static void Report(string line)
    {
        Lines.Add(line);
        Console.Out.WriteLine(line);
    }
}
