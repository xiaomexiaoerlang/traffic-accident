# 案件跟进 (CaseTracker)

交通事故与违法行为案件处理流程跟进 App，基于 Kotlin + Jetpack Compose + MVVM + Room 开发，UI 遵循 MIUI/HyperOS 设计规范，底部导航采用 **KernelSU 同款液态玻璃（Liquid Glass）** 光学效果。

## 功能特性

- **双业务线**：交通事故 / 违法行为案件全流程跟进
- **4 Tab 主导航**：进程（事故处理进程）、案件（案件列表）、法律法规（法规库检索）、设置
- **液态玻璃底栏**：对齐 KernelSU 管理器，全宽玻璃条 + 透明透镜液滴（blur + vibrancy + AGSL lens 折射），按压不染色、实时背板折射、液滴可拖拽切换
- **案件管理**：新建、编辑、删除、年度总览、表格导入导出
- **进度跟踪**：自动计算案件进度，进度条随时间和状态变化
- **状态流转**：待受理 → 调查中 → 处理中 → 审核中 → 已结案
- **法定时限**：根据案件类型和状态自动计算法定截止日期，超期预警高亮
- **案情智能解析**：NLP 识别案情描述，自动提取关键要素（当事人、事故类型、损失等）
- **流程模板**：事故/违法案件处理流程模板 + 清单 Checklist，逐项勾选跟进
- **时间线**：记录案件处理过程中的关键事件和里程碑
- **法律法规库**：内置法规全文数据，支持检索与卡片展示
- **悬浮按钮**：案件页「+」FAB 可手动拖拽位置并持久化
- **双主题**：Light/Dark 自动切换，支持 Material You 动态取色

## 液态玻璃底栏（v1.90+）

- 集成 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（backdrop 库，KernelSU 透镜同源，Apache 2.0）
- 管线：实时层捕获（GraphicsLayer）→ blur(4dp) → vibrancy → AGSL lens 折射/色散 → 高光 + 内阴影
- 液滴为透明透镜，无 accent 染色；按压放大 78/56，折射强度联动
- 兼容降级：API≥33 完整 AGSL；API 31–32 blur+vibrancy；API<31 半透明底色
- 全局未捕获异常捕获，崩溃堆栈写入应用外部目录 crash.log，启动时弹窗提示

## 技术栈

| 维度 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.10.00 / 1.7.4, Material 3) |
| 架构 | MVVM + ViewModel + StateFlow |
| 数据库 | Room 2.6.1 |
| 依赖注入 | Hilt 2.51.1 |
| 玻璃效果 | com.kyant0:backdrop（AGSL RenderRuntime） |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

## 项目结构

```
app/src/main/java/com/casetracker/
├── data/              # 数据层
│   ├── local/         # Room 数据库、Entity、DAO
│   ├── mapper/        # Entity ↔ Domain 映射
│   ├── repository/    # 数据仓库
│   └── importer/      # 表格导入导出
├── domain/            # 领域层
│   └── model/         # 业务模型、状态枚举、法定时限、NLP 解析、节假日
├── di/                # 依赖注入模块
├── theme/             # MIUI 主题（颜色、排版、形状、间距、Theme）
├── ui/
│   ├── components/    # 组件（液态玻璃底栏 V2、MiuiCard、滑动操作、触感等）
│   ├── accident/      # 进程页（事故处理进程）
│   ├── home/          # 案件列表页（含可拖拽 FAB）
│   ├── law/           # 法律法规库（全文数据 + 检索）
│   ├── detail/        # 案件详情（含清单抽屉）
│   ├── edit/          # 新建/编辑
│   ├── search/        # 搜索
│   ├── settings/      # 设置（版本日志）
│   ├── main/          # MainScaffold 主框架（Pager + 底栏）
│   └── navigation/    # 导航路由
├── com/kyant/backdrop/  # 集成的液态玻璃库源码（31 文件）
├── demo/              # 液态玻璃底栏独立 Demo（LiquidGlassDemo）
├── CaseTrackerApp.kt  # Application（全局崩溃捕获）
└── MainActivity.kt    # 入口 Activity
```

## 法定时限规则

- 交通事故认定书：现场调查之日起 **10 日**内
- 检验鉴定后：结论确定之日起 **5 日**内
- 复核申请：送达之日起 **3 日**内
- 复核结论：受理之日起 **30 日**内
- 违法一般程序：立案之日起 **30 日**内

## 构建运行

1. 使用 Android Studio Hedgehog (2023.1.1) 或更高版本打开项目
2. 等待 Gradle 同步完成（需 JDK 17+）
3. 连接 Android 设备或启动模拟器（API 26+）
4. 点击 Run 即可安装运行

## 设计规范

- 大圆角卡片（16dp）、无边框设计
- 顶部导航栏和底部栏毛玻璃/液态玻璃效果
- Spring 弹性动画过渡
- MiSans 字体（标题 Demibold，正文 Regular）
- 圆润线性图标（线宽 1.5dp，尺寸 24dp）
- 页面左右边距 16dp，卡片间距 12dp

## 版本记录

| 版本 | 说明 |
|------|------|
| v1.90.5 | 液滴拖拽跟手修复（Modifier.offset 命中区域跟随） |
| v1.90.4 | 底栏 V2 重构：对齐 KernelSU 三层结构，水滴按压不再变蓝 |
| v1.90.3 | 案件页 FAB 可拖拽 + 位置持久化 |
| v1.90.1–1.90.2 | 崩溃防御 + AGSL 管线拆级（lens 单独降级） |
| v1.90 | 集成 backdrop 库，液态玻璃底栏上线 |

完整变更记录见 `优化日志.md`。
