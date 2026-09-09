<img width="1280" height="640" alt="airtype-poster" src="https://github.com/user-attachments/assets/f38c286a-be22-43c6-aa3f-7dc374ad68c5" />
[README.md](https://github.com/user-attachments/files/32011663/README.md)
# AirType — 手机语音/文字一键上屏电脑光标处

> 这是对 **phmic-1**（老版本 Phmic）的现代化重构与新版本：手机端与电脑端处于同一局域网（含"电脑连手机热点"），
> 在手机输入框用**手机自带语音输入法**说完话，点发送后文字立即出现在电脑**当前光标处**。无需注册账号、无需服务器、语音不出手机。

**新版本亮点：**
- **手机端**：采用 `kuikly-ui-framework` skill 的**声明式 DSL 风格**（`Column / Row / Text / Button / Input / Card` + `attr / event` + `observable`），
  进入 **终端仪器（Telemetry Console）** 世界：近黑机体、信号绿 `#5EE07A`、等宽读数、发丝线、无投影。发现页（发射台：发现/扫描/手动连接）+ 已连接工作页（大输入区 + 最近发送重发）。
- **电脑端**：由老版 WinForms 升级为 **WPF (.NET 8)**，无边框窗口 + 终端面板 + 大"配对码"读数井（信号绿大号等宽 + 闪烁块光标）+ 已配对设备 + 链路信息 + 运行日志 + 托盘常驻。
- **协议 v2**：与老版 **完全互通**（TCP 47555 文本 / UDP 47556 发现，挑战-应答认证，配对码不进网络）。

## 目录

```
docs/            DESIGN.md / PROTOCOL.md / BUILD.md
pc/AirTypePC/    电脑端（C# / .NET 8 WPF）
mobile/android/  手机端（Kotlin，零第三方依赖，自研 Kuikly 风格声明式 DSL）
dist/            构建产物（APK / PC 发布）
```

## 快速开始

1. **电脑**：运行 `dist/AirTypePC.exe`（或 `pc/AirTypePC/bin/Release/net8.0-windows/AirTypePC.exe`），记住窗口顶部大卡片里的 **4 位配对码**。
2. **手机**：安装 `dist/AirTypePhone-1.0.6-debug.apk`，与电脑连同一 WiFi（或电脑连手机热点）。
3. 打开 App → 点发现的电脑（或"扫描 / 手动连接"）→ 输入电脑屏幕上的配对码 → 在输入框说话/打字 → 点"发送到电脑"。
4. 文字即出现在电脑当前光标所在窗口；点"最近发送"可一键重发。

> 若目标程序以管理员权限运行，请同样**以管理员身份**启动 AirTypePC；连不上请放行防火墙 TCP/UDP `47555` / `47556`。

## 详细文档

- [设计文档](docs/DESIGN.md)
- [传输协议 v2](docs/PROTOCOL.md)
- [构建与联调指南](docs/BUILD.md)

## 技术栈

| 端 | 技术 | 说明 |
|---|---|---|
| 手机 | Android 原生 Kotlin + 自研 Kuikly 风格声明式 DSL（渲染到框架 View） | minSdk 24 / target 34，**零第三方依赖**，可离线编译 |
| 电脑 | C# / .NET 8 **WPF**（无边框 + 终端仪器面板） | 剪贴板上屏 / 逐字键入双模式、托盘常驻 |

> 设计世界为 **终端仪器 Telemetry Console**：近黑机体 `#0B0E14` + 信号绿 `#5EE07A` + 等宽读数 + 发丝线 + 无投影 + 低圆角。
| 协议 | TCP + 换行分隔 JSON（UTF-8） | v2：`pair → challenge → auth(SHA-256) → text/ack` |

## 为什么手机端用"Kuikly 风格 DSL"而不是直接引入 Kuikly SDK

`kuikly-ui-framework` skill 的参考库与脚手架依赖 `github.com/Tencent-TDS/KuiklyUI` 及私有 Maven 构件；
本机网络无法访问 GitHub 且 Kuikly 构件不在 Maven Central。因此本实现**忠实遵循该 skill 的 API 与 DSL 语义**
（`observable` / `observableList` 响应式、Flex 布局、`attr`/`event`、卡片/组件概念），将其渲染到 Android 原生 View，
以保证**离线即可编译运行**，同时保持与 skill 同构的声明式写法，便于日后无缝迁移到真实 Kuikly SDK。
