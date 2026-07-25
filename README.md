<p align="center">
  <img src="docs/worddrill-logo.svg" width="120" alt="WordDrill" />
</p>

<h1 align="center">WordDrill</h1>

<p align="center">极简英语学习闪卡 App · 纯离线 · 无账号 · 无后端</p>

---

## 这是什么

WordDrill 是一个靠**不断滑动浏览**做被动重复记忆的闪卡 App。不考试、不评分、不判断对错，纯粹一直刷中英文卡片来加深记忆。

预置 **CET-4 / CET-6 / 考研英语** 三个词书，内置 **~10 万词英汉词典**（ECDICT）自动查词性和释义。支持从 Excel / 文本 / PDF 文件批量导入自定义词书。所有数据本地存储，完全离线。

## 核心功能

- **全屏刷卡** — 同屏显示英文（含词性、音标）和中文释义，左右滑动切换
- **三个预置词书** — CET-4 / CET-6 / 考研英语，开箱即用
- **内置词典** — ~10 万词（ECDICT），添加词条时自动查词性+释义+音标
- **文件导入** — 支持 xlsx / txt / csv / pdf 批量导入词书
- **跳过 + 复习** — 不想再看的词一键跳过（隐藏），自动收入复习词书
- **数据统计** — 今日刷卡数 / 累计刷卡数 / 当前词书进度
- **回收站** — 删除的词书和词条进回收站，误删可恢复
- **数据迁移** — 整库导出/导入 JSON 文件，换机不丢数据
- **主题** — 深色 / 浅色 / 跟随系统 + circular reveal 切换动画
- **锁定模式** — 刷卡时锁定隐藏导航栏，纯沉浸
- **浮动胶囊导航** — iOS 18+ 风格，可切换底部栏
- **简约导航** — 隐藏导航栏文字标签

## 截图

<p align="center">
  <em>浅色刷卡页</em>
</p>

## 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 架构 | 单 Activity + MVVM + ViewModel/StateFlow |
| 数据库 | Room（SQLite） |
| 依赖注入 | Hilt |
| 预置词库 | assets JSON + 首启导入 |
| 内置词典 | ECDICT 完整版（~10 万词） |
| 文件解析 | 手写 OOXML（xlsx）+ PdfBox-Android（pdf）|
| 构建 | Gradle Kotlin DSL + Compose BOM |
| 最低 SDK | API 26（Android 8.0）|

## 构建

```bash
# Debug APK
./gradlew :app:assembleDebug

# 运行测试
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

需要 JDK 17+。

## 开发流程

本项目使用 issue tracker 驱动开发（见 `docs/agents/`）。每个功能从规格（Issue #1）拆分为 ticket，TDD 实现，code-review 后合并到 main。

## License

MIT
