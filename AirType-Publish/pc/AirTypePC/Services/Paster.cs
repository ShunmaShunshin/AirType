using System.Runtime.InteropServices;

namespace AirTypePC;

/// <summary>
/// 把文本"打"到当前焦点窗口的光标处。
/// 模式 clipboard：写入剪贴板 + 模拟 Ctrl+V，粘贴后恢复原剪贴板文本。
/// 模式 keys：SendInput KEYEVENTF_UNICODE 逐字键入（不碰剪贴板，\n 映射为回车）。
/// 本类所有方法必须在 STA 线程（UI 线程）上调用（剪贴板依赖 STA）。
/// </summary>
public static class Paster
{
    // ---- Win32 input (mode keys) ----
    private const uint InputKeyboard = 1;
    private const uint KeyEventfKeyUp = 0x0002;
    private const uint KeyEventfUnicode = 0x0004;
    private const ushort VkControl = 0x11;
    private const ushort VkReturn = 0x0D;

    [StructLayout(LayoutKind.Sequential)]
    private struct Input { public uint type; public InputUnion U; }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public KeybdInput ki;
        [FieldOffset(0)] public MouseInput mi;
        [FieldOffset(0)] public HardwareInput hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeybdInput
    {
        public ushort wVk; public ushort wScan; public uint dwFlags;
        public uint time; public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int dx; public int dy; public uint mouseData;
        public uint dwFlags; public uint time; public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct HardwareInput { public uint uMsg; public ushort wParamL; public ushort wParamH; }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, Input[] pInputs, int cbSize);

    /// <summary>执行上屏。返回 null 表示成功，否则返回错误描述。</summary>
    public static string? Paste(string text, string mode)
    {
        try
        {
            if (mode == "keys")
            {
                TypeByUnicode(text);
                return null;
            }
            return PasteViaClipboard(text);
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    private static string? PasteViaClipboard(string text)
    {
        string? old = null;
        var hadOld = false;
        try
        {
            if (Clipboard.ContainsText())
            {
                old = Clipboard.GetText();
                hadOld = true;
            }

            Clipboard.SetText(text);
            SendKeys.SendWait("^v");
            Thread.Sleep(180);
            if (Clipboard.ContainsText())
            {
                if (hadOld && old is not null) Clipboard.SetText(old);
                else Clipboard.Clear();
            }
            return null;
        }
        catch (Exception ex)
        {
            return ex.Message;
        }
    }

    private static void TypeByUnicode(string text)
    {
        foreach (var ch in text)
        {
            if (ch == '\n') TapKey(VkReturn);
            else if (ch == '\r') continue;
            else TapUnicode(ch);
        }
    }

    private static void TapUnicode(char ch)
    {
        SendInput(1, MakeKey(ch, KeyEventfUnicode), Marshal.SizeOf<Input>());
        SendInput(1, MakeKey(ch, KeyEventfUnicode | KeyEventfKeyUp), Marshal.SizeOf<Input>());
    }

    private static void TapKey(ushort vk)
    {
        SendInput(1, MakeVk(vk, 0), Marshal.SizeOf<Input>());
        SendInput(1, MakeVk(vk, KeyEventfKeyUp), Marshal.SizeOf<Input>());
    }

    private static Input[] MakeKey(char ch, uint flags)
    {
        var arr = new Input[1];
        arr[0].type = InputKeyboard;
        arr[0].U.ki.wScan = ch;
        arr[0].U.ki.dwFlags = flags;
        return arr;
    }

    private static Input[] MakeVk(ushort vk, uint flags)
    {
        var arr = new Input[1];
        arr[0].type = InputKeyboard;
        arr[0].U.ki.wVk = vk;
        arr[0].U.ki.dwFlags = flags;
        return arr;
    }
}
