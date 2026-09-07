using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace AirTypePC;

/// <summary>本机 IPv4 网卡枚举工具（展示用 + 广播目标计算用）。</summary>
public static class NetUtil
{
    /// <summary>返回 (IPv4 地址, 网卡名)，跳过回环与未启用网卡。</summary>
    public static List<(IPAddress Address, string Nic)> GetIpv4Entries()
    {
        var result = new List<(IPAddress, string)>();
        try
        {
            foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                IPInterfaceProperties props;
                try { props = nic.GetIPProperties(); } catch { continue; }

                foreach (var ua in props.UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (IPAddress.IsLoopback(ua.Address)) continue;
                    result.Add((ua.Address, nic.Name));
                }
            }
        }
        catch
        {
            // 枚举失败时返回空列表，UI 自行降级
        }
        return result;
    }

    /// <summary>计算应向哪些广播地址发送 beacon（各子网广播 + 全局广播，去重）。</summary>
    public static List<IPAddress> GetBroadcastAddresses()
    {
        var set = new HashSet<string>();
        var list = new List<IPAddress>();

        void Add(IPAddress ip)
        {
            var key = ip.ToString();
            if (set.Add(key)) list.Add(ip);
        }

        try
        {
            foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                IPInterfaceProperties props;
                try { props = nic.GetIPProperties(); } catch { continue; }

                foreach (var ua in props.UnicastAddresses)
                {
                    if (ua.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (IPAddress.IsLoopback(ua.Address)) continue;
                    var mask = ua.IPv4Mask;
                    if (mask is null) continue;
                    var addr = ua.Address.GetAddressBytes();
                    var m = mask.GetAddressBytes();
                    if (addr.Length != 4 || m.Length != 4) continue;
                    var b = new byte[4];
                    for (var i = 0; i < 4; i++) b[i] = (byte)(addr[i] | ~m[i]);
                    Add(new IPAddress(b));
                }
            }
        }
        catch
        {
            // 兜底：至少尝试全局广播
        }

        Add(IPAddress.Broadcast);
        return list;
    }
}
