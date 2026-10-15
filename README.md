# 案件跟进 (CaseTracker)

交通事故和违法行为案件处理流程跟进 App，基于 Kotlin + Jetpack Compose + MVVM + Room 开发，遵循 MIUI/HyperOS 设计规范。

## 功能特性

- **案件管理**：新建、编辑、删除交通事故/违法案件
- **进度跟踪**：自动计算案件进度，进度条随时间和状态变化
- **状态流转**：待受理 → 调查中 → 处理中 → 审核中 → 已结案
- **法定时限**：根据案件类型和状态自动计算法定截止日期
- **时间线**：记录案件处理过程中的关键事件和里程碑
- **超期预警**：临近截止和已超期案件高亮提醒
- **数据统计**：案件总数、状态分布、结案率、超期统计
- **双主题**：Light/Dark 模式自动切换，支持 Material You 动态取色

## 技术栈

| 维度 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3) |
| 架构 | MVVM + ViewModel + StateFlow |
| 数据库 | Room 2.6 |
| 依赖注入 | Hilt |
| 导航 | Navigation Compose |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

## 项目结构

```
app/src/main/java/com/casetracker/
├── data/              # 数据层
│   ├── local/         # Room 数据库、Entity、DAO
│   ├── mapper/        # Entity ↔ Domain 映射
│   └── repository/    # 数据仓库
├── domain/            # 领域层
│   └── model/         # 业务模型、状态枚举、法定时限计算
├── di/                # 依赖注入模块
├── theme/             # MIUI 主题（颜色、排版、Theme）
├── ui/
│   ├── components/    # 可复用组件（MiuiCard、进度条、时间线等）
│   ├── navigation/    # 导航路由
│   ├── home/          # 首页（案件列表）
│   ├── detail/        # 案件详情
│   ├── edit/          # 新建/编辑
│   └── stats/         # 统计页
├── CaseTrackerApp.kt  # Application
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
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器（API 26+）
4. 点击 Run 即可安装运行

## 设计规范

- 大圆角卡片（16dp）、无边框设计
- 顶部导航栏和底部栏毛玻璃效果
- Spring 弹性动画过渡
- MiSans 字体（标题 Demibold，正文 Regular）
- 圆润线性图标（线宽 1.5dp，尺寸 24dp）
- 页面左右边距 16dp，卡片间距 12dp
