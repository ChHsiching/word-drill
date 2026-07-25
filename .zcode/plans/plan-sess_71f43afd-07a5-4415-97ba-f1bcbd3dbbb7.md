# Ticket #24 实现计划：App Icon + 启动动画

## 设计稿来源
- 静态 icon：`designs/worddrill-icon/icon-03-compare.html`（方案 B：前实心后轮廓）
- 动画规范：`designs/worddrill-icon/icon-03b-animation.html`（5 阶段可播放原型）
- SVG 坐标系：viewBox `0 0 80 80`
  - 后卡轮廓：`rect x=14 y=22 w=37 h=50 rx=6`（fill=none, stroke, sw=5）
  - 前卡实心：`rect x=29 y=14 w=37 h=50 rx=6`（fill）
  - 线1：`(38,28)→(58,28)` · 线2：`(38,38)→(52,38)` · 线3：`(38,48)→(55,48)`（stroke sw=4, round cap）

## 关键决策（先说明，避免误解）

1. **不生成各密度 PNG mipmap**。`minSdk = 26`，100% 设备走 adaptive icon（`mipmap-anydpi-v26/ic_launcher.xml`），现有 XML 已覆盖全部密度。PNG fallback 在 minSdk≥23 项目里是无用资产（违反 AGENTS.md §2）。Ticket 列出"各分辨率 mipmap"是规范模板，我会以 adaptive icon 一个方案覆盖。
2. **不新增 instrumented test**。Compose 动画难以在 `createAndroidComposeRule` 下稳定断言时序；现有 `AppNavigationTest` 启动 MainActivity 后用 `waitForIdle()` 等动画完成，splash overlay 不渲染任何文案节点，不会让现有 3 个测试变红（只是每个 Activity 实例 +~3s）。JVM 单测层面，本特性无可纯函数化的逻辑（动画时序是平台副作用），按 §2 不写投机测试。
3. **Reduced motion 用系统信号**：`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`（开发者选项"移除动画"/无障碍）。这是 Android 平台事实标准，比新增 DataStore 偏好简单。

## 改动清单

### A. App Icon（静态）

1. **`app/src/main/res/drawable/ic_launcher_foreground.xml`** — 重写 VectorDrawable
   - viewport 108×108（adaptive icon 规范）
   - 用 `<group scale=0.825 translateX=21 translateY=21>` 把 80×80 设计稿内容映射到中心 66×66 安全区
   - 5 个 path/line：后卡轮廓（stroke #1A1A1A, sw≈6.6）+ 前卡实心（fill #1A1A1A）+ 3 条线（stroke #FAFAFA, sw≈5.3, strokeCap round）
   - 默认版本（浅色底）

2. **`app/src/main/res/drawable-night/ic_launcher_foreground.xml`** — 新建（深色版本）
   - 同结构，反色：后卡轮廓 stroke #FFFFFF、前卡实心 fill #FFFFFF、3 条线 stroke #000000

3. **`app/src/main/res/values/colors.xml`** — 改 `ic_launcher_background`
   - `#FAFAFA`（与浅色 `BgLight` 一致）

4. **`app/src/main/res/values-night/colors.xml`** — 新建
   - `ic_launcher_background = #000000`（与 `BgDark` 一致）

5. **`mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml`** — 加 `<monochrome>` 子元素
   - Android 13+ 主题图标支持，drawable 复用前景（系统会按壁纸染色，前后卡对比通过 alpha 区分）

### B. 启动动画

6. **`app/src/main/java/.../ui/splash/AnimatedSplash.kt`** — 新建
   - `@Composable AnimatedSplashOverlay(onFinished: () -> Unit)`
   - 实现：`Box(fillMaxSize)` 内 `Canvas` 绘制 5 元素（后卡/前卡/3 线）
   - 5 个 `Animatable<Float>`：每个元素独立的 `alpha` 和 `translateX`
   - `LaunchedEffect(Unit)` + `coroutineScope { launch { ... } }` 并行驱动：
     - Phase 1 (0ms)：后卡 alpha 0→1、translateX +60→0（spring）
     - Phase 2 (200ms)：前卡 alpha 0→1、translateX −60→0（spring）
     - Phase 3 (600/700/800ms)：线 1(+40)、线 2(−40)、线 3(+40) alpha 0→1 + translateX→0（spring）
     - Phase 4 (1100–1500ms)：隐式停顿（无动作，等 delay）
     - Phase 5 (1500ms 起)：流水线淡出，顺序 线1→线2→线3→后卡→前卡，每个 300ms tween、间隔 300ms（不重叠）
     - 全部完成后 `onFinished()`
   - Spring spec：`spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)`，对应 Apple damping 1.0 / response ≈ 0.45s（在 0.3-0.4s 容差内）
   - Reduced motion 分支：`rememberReduceMotion()` 返回 true 时立即 `onFinished()`，不渲染 overlay
   - `rememberReduceMotion()`：读 `Settings.Global.ANIMATOR_DURATION_SCALE`，== 0f 即 reduce motion

7. **`MainActivity.kt`** — 集成 splash overlay
   - `WordDrillTheme` 内、`ThemeRevealContent` 包裹的内容里，`WordDrillRoot()` 之上叠加 splash overlay
   - `var splashDone by remember { mutableStateOf(false) }`
   - `if (!splashDone) AnimatedSplashOverlay(onFinished = { splashDone = true })`
   - 主内容始终 composition（被 overlay 盖住），动画完移除 overlay 揭示

### C. 版本号 & 文档

8. **`app/build.gradle.kts`** — `versionCode 13→14`、`versionName "0.1.0-dev27"→"0.1.0-dev28"`
9. **`app/src/androidTest/.../MeScreenTest.kt`** — 同步版本号断言（android-mcp-notes 明确要求）

## 验证步骤（用 android-emulator MCP）

1. `android_preflight` 确认环境
2. `JAVA_HOME=C:/jdk17 ./gradlew :app:assembleDebug` 构建
3. `android_install_app` 装包
4. **Icon 验证**：
   - `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` 回桌面
   - `android_screenshot` 截桌面，肉眼确认 icon 形态（双卡交叠）
   - 切系统深色（`adb shell cmd uimode night yes`）再截，确认反色
5. **动画验证**：
   - `adb shell am start -n com.github.chsiching.worddrill/.MainActivity` 启动
   - 启动瞬间连续 `adb exec-out screencap -p > /tmp/frame-N.png` 抓多帧，肉眼对照 5 阶段
   - 预期：t≈0 看到后卡轮廓从右飞入、t≈600 三线交错、t≈1500 流水线淡出、t≈3000 揭示主内容
6. **Reduced motion**：
   - `adb shell settings put global animator_duration_scale 0`
   - 重启 app，截屏确认直接显示主内容（无动画）
   - `adb shell settings put global animator_duration_scale 1` 还原
7. **回归**：
   - `JAVA_HOME=C:/jdk17 ./gradlew :app:testDebugUnitTest`（含 ThemeTokensTest，确认颜色 token 没被改坏）
   - `JAVA_HOME=C:/jdk17 ./gradlew :app:connectedDebugAndroidTest`（AppNavigationTest 等仍绿）
   - `MeScreenTest` 版本号断言通过

## 不做（Out of scope）

- 不生成各密度 PNG mipmap（minSdk=26，adaptive XML 已覆盖）
- 不写 Compose 动画的 instrumented test（时序断言不稳，现有测试隐式覆盖）
- 不改 `ic_launcher.xml`/`ic_launcher_round.xml` 的整体结构（只补 `<monochrome>` 子元素）
- 不引入 Lottie 或第三方动画库（纯 Compose Canvas + Animatable）

## Commit 策略

实现完成后在 `main` 分支（当前分支）按既有 commit 风格提交，单 commit：
`feat: App icon + 启动动画 (#24)`

随后用 `/code-review` 复核。