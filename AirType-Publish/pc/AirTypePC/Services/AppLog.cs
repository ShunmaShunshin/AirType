namespace AirTypePC;

/// <summary>进程级日志缓冲：任何线程可写入；界面订阅以实时显示。</summary>
public static class AppLog
{
    private const int MaxLines = 600;
    private static readonly object Sync = new();
    private static readonly List<string> Lines = new();
    private static readonly List<Action<string>> Subs = new();

    public static void Write(string line)
    {
        lock (Sync)
        {
            Lines.Add($"[{DateTime.Now:HH:mm:ss}] {line}");
            if (Lines.Count > MaxLines) Lines.RemoveRange(0, Lines.Count - MaxLines);
            foreach (var s in Subs)
            {
                try { s(Lines[^1]); } catch { }
            }
        }
    }

    public static string[] Snapshot()
    {
        lock (Sync) return Lines.ToArray();
    }

    public static IDisposable Subscribe(Action<string> onLine)
    {
        lock (Sync)
        {
            Subs.Add(onLine);
            return new Subscription(onLine);
        }
    }

    private sealed class Subscription : IDisposable
    {
        private readonly Action<string> _cb;
        public Subscription(Action<string> cb) => _cb = cb;
        public void Dispose() { lock (Sync) Subs.Remove(_cb); }
    }
}
