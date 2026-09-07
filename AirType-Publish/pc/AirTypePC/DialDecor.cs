using System;
using System.Windows;
using System.Windows.Media;
using Point = System.Windows.Point;
using Pen = System.Windows.Media.Pen;
using Color = System.Windows.Media.Color;
using Size = System.Windows.Size;

namespace AirTypePC;

/// <summary>
/// 参考采样机（VAULTS）线条：同心刻度环 + 指针弧 + 十字注册标记 + 细分刻度。
/// 作为背景装饰层，弱透明、非交互。
/// </summary>
public sealed class DialDecor : FrameworkElement
{
    public DialDecor() { IsHitTestVisible = false; }

    protected override void OnRender(DrawingContext dc)
    {
        var w = ActualWidth; var h = ActualHeight;
        if (w < 12 || h < 12) return;
        var cx = w / 2; var cy = h / 2;
        var r = Math.Min(w, h) / 2 * 0.92;

        var g = Color.FromArgb(0xFF, 0x5E, 0xE0, 0x7A);
        var faint = new Pen(new SolidColorBrush(Color.FromArgb(40, g.R, g.G, g.B)), 1);
        var mid = new Pen(new SolidColorBrush(Color.FromArgb(64, g.R, g.G, g.B)), 1);
        var strong = new Pen(new SolidColorBrush(Color.FromArgb(110, g.R, g.G, g.B)), 1.4);

        // 十字线
        dc.DrawLine(faint, new Point(0, cy), new Point(w, cy));
        dc.DrawLine(faint, new Point(cx, 0), new Point(cx, h));

        // 同心环
        foreach (var f in new[] { 1.0, 0.78, 0.55, 0.32 })
            dc.DrawEllipse(null, f == 1.0 ? mid : faint, new Point(cx, cy), r * f, r * f);

        // 细分刻度（每 5° 一小格，每 30° 一大格）
        for (int i = 0; i < 72; i++)
        {
            var a = i * Math.PI * 2 / 72;
            var len = (i % 6 == 0) ? 14 : 6;
            var p1 = new Point(cx + Math.Cos(a) * r, cy + Math.Sin(a) * r);
            var p2 = new Point(cx + Math.Cos(a) * (r - len), cy + Math.Sin(a) * (r - len));
            dc.DrawLine(faint, p1, p2);
        }

        // 指针弧（约 210°→330° 的亮弧）
        var arc = new StreamGeometry();
        using (var geo = arc.Open())
        {
            geo.BeginFigure(Angle(cx, cy, r * 1.03, 210), false, false);
            geo.ArcTo(Angle(cx, cy, r * 1.03, 330), new Size(r * 1.03, r * 1.03),
                0, false, SweepDirection.Clockwise, true, true);
        }
        arc.Freeze();
        dc.DrawGeometry(null, strong, arc);

        // 角落十字注册标记
        DrawCross(dc, strong, new Point(12, 12));
        DrawCross(dc, strong, new Point(w - 12, 12));
        DrawCross(dc, strong, new Point(12, h - 12));
        DrawCross(dc, strong, new Point(w - 12, h - 12));
    }

    private static Point Angle(double cx, double cy, double radius, double deg)
    {
        var a = deg * Math.PI / 180;
        return new Point(cx + Math.Cos(a) * radius, cy + Math.Sin(a) * radius);
    }

    private static void DrawCross(DrawingContext dc, Pen pen, Point p)
    {
        dc.DrawLine(pen, new Point(p.X - 8, p.Y), new Point(p.X + 8, p.Y));
        dc.DrawLine(pen, new Point(p.X, p.Y - 8), new Point(p.X, p.Y + 8));
    }
}
