# 交接文档 — Ticket #24 App Icon + 启动动画

**日期**: 2026-07-26
**Issue**: [#24](https://github.com/ChHsiching/word-drill/issues/24) (状态: OPEN，待主 agent 决定是否关闭)
**当前分支**: `main`
**HEAD**: `1489ed6` (Revert alias 方案)

## 本次会话做了什么

实现了 #24 的两部分：**App icon**（方案 B 双卡交叠）+ **启动动画**（5 阶段 Compose Canvas 动画）。过程中解决了一个系统 SplashScreen 的平台限制问题（最终接受限制，未强行修复）。

### 交付的功能（全部完成且验证通过）

**1. App icon**（`5c4c82e`）
- `drawable/ic_launcher_foreground.xml`：浅色 VectorDrawable（黑块黑线 #1A1A1A）
- `drawable-night/ic_launcher_foreground.xml`：深色反色（白块白线 #FFFFFF）
- `values/colors.xml` + `values-night/colors.xml`：icon 背景色跟随系统（#FAFAFA / #000000）
- adaptive icon 加 `<monochrome>` 支持 Android 13+ 主题图标

**2. 启动动画 overlay**（`5c4c82e`，文件 `app/src/main/java/.../ui/splash/AnimatedSplash.kt`）
- 5 阶段 Compose Canvas 动画：后卡/前卡 spring 飞入 → 三线交错飞入 → 停顿 → 交错淡出（stagger，非流水线）
- Spring 曲线：`Spring.DampingRatioNoBouncy` + `StiffnessMediumLow`（critically damped）
- Reduced motion：`Settings.Global.ANIMATOR_DURATION_SCALE == 0` 时跳过动画
- **关键实现细节**：Canvas 内用 `scale(s)` 作用域整体缩放设计坐标（path 坐标不能手算 ×s，否则不缩放——曾因此导致图标超小偏左上）

**3. 系统 SplashScreen 消除「先大 logo 后动画」**（`5c4c82e`）
- 引入 `androidx.core:core-splashscreen` 库
- `Theme.WordDrill.Splash`：`windowSplashScreenAnimatedIcon` 设为透明 1×1 drawable
- `setKeepOnScreenCondition`：冷启动期间系统 splash 保持纯背景色，覆盖 Compose 首帧组合的 ~1-2s 空白期
- `onReady` 回调：overlay 的 `LaunchedEffect` 进入时释放系统 splash

**4. overlay 跟随应用主题偏好**（`c09c4ed`）
- splash overlay 接收 `darkTheme: Boolean` 参数（来自 MainActivity 的 `darkBg`，跟随 `renderedTheme`）
- 确保用户设 DARK 时 overlay 也是深色，而非跟随系统

### 失败的尝试（已 revert）

**alias 方案**（`90ccb1d` → 已 revert 在 `1489ed6`）

**问题**：系统浅色 + 应用偏好 DARK 时，开屏系统 splash（~1-2s）显示白色，然后才切深色应用。

**尝试的方案**：3 个 `activity-alias`（LauncherSystem/Light/Dark），用户切主题时 `PackageManager.setComponentEnabledSetting` 切换启用的 alias，让系统 splash 读对应 alias 的 theme。

**为什么失败**（已彻底验证）：
1. alias 切换本身成功（`enabledComponents` 确认 LauncherDark 启用）
2. 但即使通过 `am start -n .../.LauncherDark` 显式启动，系统 splash 第一帧仍是白色
3. 根因：Android 12+ 的 `StartingWindow` 机制在 alias theme 解析前就用默认 theme 创建了 splash 窗口
4. `setTheme()` 在 `Application.onCreate` / `Activity.onCreate` 都无效（窗口已建好）

**还试过但不工作的**：
- `Application.onCreate` 里 `setTheme()` — 系统 splash 读的是 Activity theme 不是 Application theme
- `Activity.onCreate` 里 `setTheme()`（在 `super.onCreate` / `installSplashScreen` 之前）— 同样无效

**最终决定**：接受平台限制。系统 splash 跟随系统主题，overlay 跟随应用偏好。如果用户觉得白闪不可接受，唯一彻底解法是去掉独立 DARK 偏好、只跟随系统主题。

## 验证状态

| 项目 | 状态 |
|------|------|
| Icon 浅色（黑线黑块在 #FAFAFA） | ✓ 验证通过 |
| Icon 深色（白线白线在 #000000） | ✓ 验证通过 |
| 动画 5 阶段时序 | ✓ 验证通过 |
| overlay LIGHT/DARK/SYSTEM 三模式 | ✓ 验证通过（像素采样确认背景色+图标色） |
| Reduced motion 跳过 | ✓ 验证通过 |
| JVM 单测 | ✓ 全绿 |
| connectedAndroidTest | ⚠️ 2 个失败（均非 #24 引入，见下） |

### 预存在的测试失败（与本 ticket 无关）

1. **`Migration34Test.roomOpensAfterMigration_schemaValidationPasses`**：#22 把 DB 升到 v5 但测试只注册了 migration 3→4，缺少 4→5。需要补 `WordDrillDatabase.MIGRATION_4_5` 到测试的 `addMigrations(...)` 列表。
2. **`MeScreenTest.progress_followsNewCurrentBook_afterSwitch`**：偶发 "connection pool has been closed"（`android-mcp-notes.md` 文档记录的已知 flaky，重跑即过）。

## 版本号

- `versionCode`: 13 → 14
- `versionName`: `0.1.0-dev27` → `0.1.0-dev28`
- `MeScreenTest` 版本号断言已同步

## 关键文件

- `app/src/main/java/com/github/chsiching/worddrill/ui/splash/AnimatedSplash.kt` — 动画核心（269 行）
- `app/src/main/java/com/github/chsiching/worddrill/MainActivity.kt` — `installSplashScreen` + overlay 集成 + `setKeepOnScreenCondition`
- `app/src/main/res/drawable/ic_launcher_foreground.xml` + `drawable-night/` — icon 前景
- `app/src/main/res/drawable/splash_icon_invisible.xml` — 透明 splash icon
- `app/src/main/res/values/themes.xml` — `Theme.WordDrill.Splash`
- 设计稿参考：`designs/worddrill-icon/icon-03b-animation.html`（动画原型）、`icon-03-compare.html`（静态 icon）

## 待主 agent 决定的事项

1. **#24 是否关闭**：核心功能（icon + 动画 + 三模式正确）全部完成。系统 splash 白闪是平台限制，acceptance criteria 里「动画完成后揭示主内容」「Reduced motion」等都满足。
2. **预存在的 `Migration34Test` 失败**：是否单独开 ticket 修（#22 的遗留）。
3. **系统 splash 白闪**：是否需要做 UX 文案说明（如 onboarding 提示用户「跟随系统主题体验最佳」），或考虑去掉独立 DARK 偏好。

## 参考的文档/资料

- [Android 官方 SplashScreen 文档](https://developer.android.com/develop/ui/views/launch/splash-screen) — `setKeepOnScreenCondition`、`installSplashScreen`、`windowSplashScreenAnimatedIcon`
- `designs/worddrill-icon/icon-03b-animation.html` — 5 阶段动画的可播放原型（phase 时序、spring 曲线、坐标）
- `docs/agents/android-mcp-notes.md` — 像素验证方法、connectedAndroidTest 已知 flaky

## 建议的 skills

- `/implement` — 如果主 agent 决定继续修 Migration34Test 或其他 ticket
- `/code-review` — 如果想对 #24 的最终代码再做一次 Standards 轴复核（spec 轴已做过）
- `/triage` — 如果要处理 Migration34Test 失败的开 ticket 决策
