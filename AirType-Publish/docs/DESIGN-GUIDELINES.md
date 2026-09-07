---
name: AirType
description: 手机语音/文字 → 电脑光标上屏（Windows WPF + Android，同局域网）。世界：终端仪器 Telemetry Console。
colors:
  bg: "#0B0E14"
  panel: "#0E121A"
  panel-2: "#121722"
  inset: "#090C12"
  line: "#1E2633"
  line-soft: "#161D28"
  text: "#D8EAE0"
  text-dim: "#86AE94"
  text-faint: "#52705F"
  signal: "#5EE07A"
  signal-deep: "#2FA85C"
  amber: "#FFB443"
  danger: "#FF6B6B"
  cta: "#5EE07A"
  cta-text: "#0B0E14"
typography:
  display:
    fontFamily: "Consolas / monospace"
    fontSize: "40-48px (WPF) / 40sp (Android)"
    fontWeight: 700
  label:
    fontFamily: "Consolas / monospace"
    fontSize: "10-11px (WPF) / 10-11sp (Android)"
    fontWeight: 400
    letterSpacing: "0.12em"
    textTransform: "uppercase"
  body:
    fontFamily: "Segoe UI / sans-serif"
    fontSize: "13px (WPF) / 13-15sp (Android)"
    fontWeight: 400
    lineHeight: "1.6"
rounded:
  window: "8px"
  panel: "2px"
  control: "2px"
spacing:
  xs: "8px"
  sm: "12px"
  md: "16-18px"
  lg: "20-24px"
  xl: "26-34px"
components:
  button-primary:
    backgroundColor: "{colors.cta}"
    textColor: "{colors.cta-text}"
    rounded: "{rounded.control}"
  button-outline:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.signal}"
    rounded: "{rounded.control}"
  panel:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.text}"
    rounded: "{rounded.panel}"
  readout-well:
    backgroundColor: "{colors.inset}"
    textColor: "{colors.signal}"
    rounded: "2px"
  tag-badge:
    backgroundColor: "{colors.panel-2}"
    textColor: "{colors.signal}"
---

# AirType 设计系统 · 终端仪器（Telemetry Console）

> 覆盖 PC（WPF/.NET 8）与手机（Android + Kuikly 风格 DSL）。模式：**Operate**（运营型工具 App）。世界：**方向 A·终端仪器**。

## Overview

**Creative North Star: "终端仪器 · 本地说出即键入"（The Telemetry Console, a Local Voice-to-Type Instrument）。**

AirType 是一部"本地打字机乐器"：手机是**键盘发射器**，电脑是**文字接收机**，两者之间是一条可信的直连链路。它看起来像一台安静的 KVM / 链路监视仪——近黑的机体、等宽的数据流、发丝般的结构线、一枚荧光信号灯。配对像"校准"，发送像"向链路写信"，连接状态像"信号灯"。

信息以**等宽读数**呈现：号码、状态、字段、日志都在同一个等宽声部里，保持仪器般的精确与克制。它不模仿营销落地页，不做毛玻璃、不做高饱和渐变；绿是唯一的"信号色"，其余都退后到暗色与灰绿里。

**Key Characteristics:**
- 近黑机体 `#0B0E14` + 分层暗面板 + 1px 发丝线 `#1E2633`。
- 信号绿 `#5EE07A` 是唯一高亮，其余为绿调灰阶（text / text-dim / text-faint）。
- 等宽（Consolas/monospace）贯穿读数、状态、字段、日志；长段中文用 system sans 缓解横排疲劳。
- 无投影、低圆角（面板 2px / 窗口 8px）；深度用"暗色分层 + 发丝"表达。
- 单列竖向；PC 窄窗，手机单列滚动；"配对码"是**显示井**里的大号等宽读数 + 闪烁块光标。
- 动效克制：启动淡入、按钮按压缩放、状态交叉；发送像"向链路写信"，接收在 PC 端逐行等宽落字。

## Colors

暗色单色 + 信号绿点色。颜色是稀缺资源。

### Primary
- **机体黑 `#0B0E14`**（bg）：画布。
- **面板 `#0E121A`**、**面板-2 `#121722`**：卡片层级；**井 `#090C12`**：读数显示区。
- **文本 `#D8EAE0`**（绿调白）：正文、读数；**dim `#86AE94`** 次级；**faint `#52705F`** 元信息/占位。

### Secondary
- **信号绿 `#5EE07A`**（signal）：唯一强调——配对码读数、状态灯、主按钮、链接/激活。
- **深信号绿 `#2FA85C`**（signal-deep）：次强调/悬停/更大面积。

### Tertiary
- **琥珀 `#FFB443`**（amber）：搜索/未连接/弱状态灯。
- **信号红 `#FF6B6B`**（danger）：断开、错误、危险操作。

### Neutral
- **发丝线 `#1E2633`**（line）：面板/结构线；**柔 `#161D28`**（line-soft）：列表项/浅分隔。
- **主按钮绿底字 `#0B0E14`**（cta-text）：绿底上的深字（对比达标）。

### Named Rules
**The One-Signal Rule.** 信号绿只出现在"需要被看到且无威胁"的少数地方（配对码读数、状态灯、主按钮、链接标记）；它的稀缺即其意义。琥珀/红只负责"未就绪/错误"。

**The Ink-Over-Signal Rule.** 信号绿底（`#5EE07A`）上的文字一律用深色 `#0B0E14`；浅绿调不与浅绿调叠字，保证 ≥4.5:1。

**The No-Shadow Rule.** 深度只用"暗色分层 + 发丝线"，面板不加投影。

## Typography

**读数/终端字体：** Consolas（WPF）／ `monospace`（Android）——贯穿号码、状态、字段、日志、按钮。等宽是本机型的母语，不是"技术装束"。
**正文长段：** Segoe UI（WPF）／ `sans-serif`（Android）——仅用于超过一行的中间说明，避免等宽横排疲劳。
**显示：** Consolas/monospace Bold，超大等宽 —— 用于配对码读数等"锚点读数"。

### Hierarchy
- **Display**（Consolas Bold，40–48px / 40sp，行高 ~1.05）：配对码大读数、极少数锚点。
- **Label**（Consolas，10–11px，track 0.12em，大写）：字段名（IP / PORT / MODE）、状态（LINK）、区块标题。
- **Body**（system sans，13px / 13–15sp，行高 1.6）：多于一行的人工说明；单行优先。

### Named Rules
**The Mono Readout Rule.** 一切"仪器读数"（号码、状态、字段值、日志、连接信息、版本）用等宽；任何两行以上的人工文案用 system sans。二者不混用。

**The Uppercase-Label Rule.** 标签一律大写 + 字距 0.12em；中文长句保持正常大小写。

## Layout

**空间模型：** 单列竖向；PC 为窄窗（约 508px），手机为单列滚动。信息按"仪表台"自上而下：头部读数（配对码/状态）→ 设备 → 链路信息 → 日志。

**节奏：**
- 卡片间 18–22px；卡片内左右 22–24px、上下 18–20px；内容区左右边距约 22px。
- 读数井（配对码）用 `inset` 暗井 + 1px 发丝，放大号等宽；井内底部留一行"副读数"。
- 列表用发丝分隔线：行 = 状态点/编号 + 等宽项目名 + 右侧可点。
- 底部操作栏与内容用 `line-soft` 分隔，左右边距一致。

**布局规则：** 密度偏"仪表紧凑"，但阅读区留白充足；窄窗下一切归并为单列。

## Elevation & Depth

**无投影（Flat-by-Default）。** 层级靠暗色分层：bg → panel → panel-2 → inset，依次更暗更深，配合发丝线。读数井是最深的一层（inset），像仪表的凹陷显示屏。

- 面板：`panel` + 1px `line`。
- 顶层面板/导航：`panel-2` + 1px `line`。
- 读数井/日志：`inset` + 1px `line-soft`。

**唯一允许的"光"是活动读数的淡绿 inner 强调**（可选、极低强度），不作为阴影。

## Shapes

**形式语言：** 终端/仪表——近乎方正。面板圆角 2px、控件 2px、窗口 8px；读数井 2px。拒绝药丸、拒绝大圆角"卡片感"。

- 面板 2px；按钮 2px；输入框 2px；窗口 8px（仅 OS 窗口语义）。
- 读数井 2px，井深 1 层。
- 状态灯为 6–8px 圆点（方形亦可，视平台），同批一致。

### Named Rules
**The Terminal Edge Rule.** 圆角封顶 2px（窗口 8px）；**禁药丸/大圆角**作为卡片或主按钮。

## Components

### Buttons
- **Primary**：信号绿底 `#5EE07A` + 深字 `#0B0E14`，等宽、大写，圆角 2px。用于"连接 / 发送 / 设置 / 完成"等主行动。
- **Outline**：`panel` 底 + 1px `line` + 信号绿等宽字，圆角 2px。用于"重新生成 / 断开配对 / 扫描"等次要/可逆。
- **Ghost**（标题栏）：透明底、`text-faint` 等宽，hover 落 `panel-2`、字变 `text`。最小化/关闭。
- **States**：hover 主绿提亮（约 `#6FE98A`），Outline 落 `panel-2`；按下透明度 0.8；禁用 0.35。

### Panels / Cards
- **Corner** 2px；**Bg** `panel` 或 `panel-2`；**Border** 1px `line`；**Shadow** 无；**Padding** 22–24px。
- 每块面板头部：等宽大写标签（左）+ 状态灯/操作（右）。

### INPUTS / FIELDS
- **Style**：`inset` 或 `panel` 底 + 1px `line` 描边 + 2px 圆角；等宽。**Focus**：描边换 `signal`（约 1px）。**Error**：`danger` 描边。

### STATUS / READOUT（签名组件）
- 配对码 = `inset` 井 + 大号等宽信号绿数字 + 一个**闪烁块光标**（`▊`，CSS/平台闪烁约 1s 步进）。
- 状态 = `LINK ● SIGNAL`：绿点=已连、琥珀=搜索/未连、红=异常；伴随等宽状态词。

### LIST ROWS（已配对 / 已发现 / 最近发送）
- 行 = 等宽编号或 6–8px 状态点 + 等宽项目名（`text`）+ 副信息（`text-faint`）+ 右侧可点（`signal`）。
- 分隔：行下 1px `line-soft`；整行可点热区铺满行宽。

## Do's and Don'ts

### Do:
- **Do** 用近黑 `#0B0E14` + 分层暗面板 + 1px `line` 发丝。
- **Do** 用信号绿 `#5EE07A` 做配对码读数、状态灯、主按钮、链接标记。
- **Do** 用 Consolas/monospace 做读数、状态、字段、日志、按钮字。
- **Do** 用 `inset` 暗井承载配对码/日志等"显示屏"内容。
- **Do** 保持窄窗单列竖向、卡片 18–22px 间距、克制动效。

### Don't:
- **Don't** 给面板加投影，或用高饱和渐变/毛玻璃拟态。
- **Don't** 用大圆角药丸做主按钮/卡片；面板 2px、窗口 8px 封顶。
- **Don't** 大范围铺信号绿，或把琥珀/红当常规装饰。
- **Don't** 用除等宽外的字体做"读数"；也不要用等宽做长段中文正文。
- **Don't** 使用 emoji 当图标、加装饰性 meta 标签、做持续/循环动效。

## 移动端改进细则（Mobile Improvement Rules）

> Android 原生 + Kuikly 风格 DSL。以 **Material 3 为骨架、品牌经 Material token** 表达（本例主题=深色终端 + 信号绿）。

### 布局与结构
- **Edge-to-edge + windowInsets**（状态栏/导航栏/挖孔/IME）：发送键与输入不被键盘遮挡；`adjustResize` 生效。
- **系统返回始终可用**；「断开配对」保留，但系统返回也可回到发现页。
- 一屏一主行动：发送/连接为唯一主按钮（信号绿）；不叠 FAB。
- 深色终端为默认；顶部标题/状态用等宽小字。

### 触控
- 可点区 ≥48×48dp、间隔 ≥8dp；列表行、"连接/重发/断开"扩出整行热区。
- 主 CTA 放拇指舒适区（下 1/3），近全宽。

### 字体 / 颜色 / 主题
- **sp 单位**；Material 类型刻度；品牌展示字 = `monospace` 加粗（仅读数/标题）。
- Material 颜色角色注入 `signal` 作 primary；**深色主题一等**（本世界即深色）；可选 Material You 动态色 + 静态回退；色调层级表达浮起，无投影。

### 组件 / 动效
- Material 组件（filled/tonal/outlined/text、FAB、chips、snackbar、底弹窗、对话框、导航栏/抽屉），主题化为深色 + 信号绿。
- **Snackbar 做瞬时反馈**（发送/失败/恢复）；对话框仅用于打断决策（输入配对码）。
- Material 动效（container transform / shared-axis / fade-through）+ 标准缓动；**尊重"移除动画/减弱动态"**（交叉/瞬切）。

### 状态 / 可访问性 / 性能
- 空/错误/加载态齐全；可见焦点环；1.3× 字号不裁切；配对码数字键盘、错误红描边 + 震动 + snackbar。
- 真机/模拟器截图（adb screencap）覆盖手机 + 平板（若目标）；测深色与 1.3× 字号；列表可回收；滚动容器不模糊（GPU 红线）。
