# 构建与联调指南

协议 **v2**：TCP 47555（文本），UDP 47556（发现）。配对码不通过网络明文传输。

## 目录

```
pc/AirTypePC/          电脑端（C# / .NET 8 WPF）
mobile/android/        手机端（Kotlin，零第三方依赖）
docs/PROTOCOL.md       传输协议
docs/DESIGN.md         设计文档
```

---

## 1. 电脑端 AirTypePC

### 方式 A：本机已有 .NET 8 SDK

```powershell
dotnet build pc/AirTypePC/AirTypePC.csproj -c Release
# 产物：pc/AirTypePC/bin/Release/net8.0-windows/AirTypePC.exe
```

### 方式 B：自包含单文件（免装运行时，推荐分发）

```powershell
dotnet publish pc/AirTypePC/AirTypePC.csproj -c Release -r win-x64 `
  --self-contained true -p:PublishSingleFile=true -o dist/pc
# 产物：dist/pc/AirTypePC.exe
```

### 无界面协议自检

```powershell
AirTypePC.exe --selftest
# 结果写入 exe 同目录 selftest-result.txt，SELFTEST PASS 即协议链路正常
```

运行 `--uicheck` 可验证主界面 + 设置界面能正常构建（3 秒后自动退出）。

### 运行与常见问题

- 首次运行放行防火墙 TCP/UDP `47555` / `47556`：
  `netsh advfirewall firewall add rule name="AirTypePC TCP" dir=in action=allow protocol=TCP localport=47555`
- 关闭窗口 = 最小化到托盘；双击托盘图标恢复。
- 目标程序以管理员运行导致粘贴失败时，请以管理员身份运行 AirTypePC。
- **注意**：老版 PhmicPC 与新 AirTypePC 默认端口相同，请勿同时运行（如需，可在两台电脑/不同端口分别运行）。

---

## 2. 手机端 AirTypePhone（Android）

### 构建（本机已验证可离线编译）

```powershell
cd mobile/android
# 需要本机 JAVA_HOME + Android SDK（local.properties 指向 sdk.dir）
gradle :app:assembleDebug --offline
# 产物：mobile/android/app/build/outputs/apk/debug/app-debug.apk
```

也可直接安装：`dist/AirTypePhone-1.0.0-debug.apk`（约 850KB）。

> App **零第三方依赖**（仅框架 View + 内置 org.json），UI 与网络逻辑见 `app/src/main/java/com/airtype/phone/`。

### 使用

1. 电脑端先运行 AirTypePC，记住顶部大卡片的 4 位配对码。
2. 手机与电脑连同一 WiFi（或电脑连手机热点）。
3. 打开 App：自动列出发现电脑，点一下 → 输入配对码即连；发现失败用"扫描"或"手动连接"。
4. 已连接后：点输入框 → 用语音输入法说话 → 点"发送到电脑"。

---

## 3. 端到端验收清单

| # | 场景 | 操作 | 预期 |
|---|---|---|---|
| 1 | 同 WiFi 配对 | 两端同网，手机点发现的电脑 | 状态"已连接"，电脑日志显示配对成功 |
| 2 | 发送中文/emoji | 输入框语音/打字，点发送 | 文字出现在电脑光标窗口；手机输入框清空 |
| 3 | 错误配对码 | 输错码 | 提示"配对码错误"且不无限重连 |
| 4 | 手机热点 | 电脑连手机热点 | 可正常收发（IP 通常 192.168.43.x） |
| 5 | 剪贴板恢复 | 先复制一段文字再发送 | 粘贴后电脑剪贴板复原 |
| 6 | 断线重连 | 关闭电脑端再打开 | 手机提示重连并自动恢复 |
| 7 | 管理员窗口 | 管理员记事本 + 普通运行 | 若无法上屏，改以管理员运行或改逐字键入 |
