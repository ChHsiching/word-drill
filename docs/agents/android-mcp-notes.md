# Android Emulator MCP — 操作备忘

用 `android-emulator` MCP 辅助开发时反复踩的工具层坑，外加少数**跨文件、无法在单处代码注释承载**的代码层约定（见末节「代码层约定」）。其余代码层红线（别启用 Hilt Plugin、别升级 AndroidX 等）在各自代码注释里，不在此重复。

## 环境

- JDK 17：`C:\jdk17`，所有 `./gradlew` 命令带 `JAVA_HOME=C:/jdk17` 前缀。
- AVD：`Pixel_API_36`（serial `emulator-5554`，通常已启动）。
- adb：`C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe`（MCP 已封装，raw adb 需全路径）。

## 构建 / 运行

1. `android_preflight` 确认环境。
2. 构建：`JAVA_HOME=C:/jdk17 ./gradlew :app:assembleDebug`。
3. 装：`android_install_app`；启动：**`adb shell am start -n com.github.chsiching.worddrill/.MainActivity`**。
   - `android_launch_app` / `android_build_and_run` 都走 monkey，手动 Hilt（无 Gradle Plugin）会报 "No activities found to run, monkey aborted"。`am start` 直起 Activity，可靠。
4. 清数据做干净态验证：**先 `android_install_app` 再 `pm clear`**（反过来 `pm clear` 可能 "Failed"，因旧安装态失效）。
   - `pm clear` 直接调有时返回非零退出码但实际成功；包一层看退出码：`adb shell "pm clear com.github.chsiching.worddrill; echo done=$?"`，`done=0` 即成功。
   - connectedAndroidTest 跑完会卸载 app，下次手验前先 `adb shell pm list packages | grep word` 确认还在，不在就重新 `android_install_app`。

## 像素级验证（#9 起用）

`android_screenshot` 是肉眼真相，但要断言"深色主题生效"这种**颜色变化**，肉眼不够客观。**用 adb 抓 PNG + node_repl + pngjs 读像素 RGB**：

1. 抓屏：`adb exec-out screencap -p > /tmp/x.png`（MCP 的 `android_screenshot` 返回的是 MCP 内部路径，raw adb 更可控）。
2. 读像素：node_repl 里 `const { PNG } = await import('pngjs'); const png = PNG.sync.read(fs.readFileSync(path)); const [r,g,b] = px(x,y)`。
3. pngjs 已装在 `C:/Users/Administrator/AppData/Local/Temp/node_modules`，node_repl 先 `js_add_node_module_dir` 加该目录。
4. **采样点选纯背景区**（避开文字/icon）。深色主题内容区背景 ≈ `[18,19,24]`，浅色 ≈ `[250,248,255]`，对比即可断言主题切换生效。
5. ⚠️ `analyze_image` MCP 只支持远程 URL，本地截图用不了；像素法是替代。
6. ⚠️ `js_reset` 后 `js_add_node_module_dir` 的路径会丢，需重新加（`true`=新增，`false`=已存在，都正常）。


## 验证 UI

- `android_screenshot` 看真相；`android_ui_describe` 拿元素树坐标；`android_ui_resolve` 查具体元素。
- **`android_ui_describe` 有时返回滞后快照**（对话框已关它还显示开）。拿不准时以 `android_screenshot` 为准。
- 文档/行为问题用 **context7 查 Android 官方文档**（如 Compose Testing APIs、UIAutomator）。android-emulator MCP 本身无公开文档，别乱猜操作。

## IME 无法输入 Compose TextField

`android_ui_type_text`、`adb shell input text` 都**无法**往 Compose `TextField` / `OutlinedTextField` 输入文字。后端是 adb-input / UIAutomator，注入 KeyEvent；Compose 走 IME 通道，两者不兼容（context7 查 Compose Testing APIs 确认：正确路径是 `performSemanticsAction`，MCP 不暴露语义层）。

**应对**（输入流无法 MCP 自动化）：
1. 可测逻辑抽**纯函数 JVM 单测**（如 `validateBookName`）。
2. DAO 写入路径用 `WordDrillDatabaseTest`（内存 Room）覆盖。
3. 端到端落库用 **adb 直接验证**（配合下条 `wal_checkpoint`）。
4. UI 渲染用 MCP screenshot / describe 验证可见性，别指望驱动 TextField 输入。

## 查 Room 数据库

查刚写入的库可能因 WAL 返回空。**先 checkpoint 再查**：

```
adb shell "run-as com.github.chsiching.worddrill sqlite3 databases/worddrill.db 'PRAGMA wal_checkpoint(TRUNCATE); SELECT ...;'"
```

查 DataStore（看 `current_book_id` 的 varint 值）：

```
adb shell "run-as com.github.chsiching.worddrill sh -c 'cat files/datastore/worddrill.preferences.preferences_pb | od -An -tx1'"
```

## 测试

- `createComposeRule` / `createAndroidComposeRule` 有 v2 deprecation 警告，**别迁移**。保持 v1（与既有测试一致），warning 无害。v2 用 StandardTestDispatcher，行为不同，要重写同步逻辑。
- 带写副作用的 Flow `collect` 务必 `distinctUntilChanged`，否则重复 emit 重复写库（`HorizontalPager` 的 `settledPage`、列表刷新回调都会对同一值重复 emit）。
- **断言用 `Truth.assertThat`，别用 `kotlin.assert`**：Kotlin 的 `assert` 在 JVM 上靠 `-ea` flag，JUnit4 默认不设，所以 `assert(x)` 永远通过（no-op），测了等于没测。repo 约定是 `com.google.common.truth.Truth.assertThat`（见 `WordDrillDatabaseTest` / `BookNameValidationTest`）。
- **`onNodeWithText` 默认做子串匹配**（错误信息原文 "contains '...'"）。`onNodeWithText("统计")` 会同时命中"统计数据""今日统计"等，报 "found N nodes"。要么用更长更精确的文案，要么 `onNodeWithText("统计", substring = false)`。
- **`performTextInput` 是追加，不是替换**：对已有值的 TextField 调 `performTextInput("X")` 会把 "苹果" 变成 "苹果X"。要整段替换用 `performTextReplacement("X")`。
- `IconButton` 的点击入口在测试里要用 `onNodeWithContentDescription(...)`（匹配 `contentDescription`），不是 `onNodeWithText`（后者匹配显示文本，IconButton 通常只有 icon 没文本）。
- **`= runBlocking { }` 表达式体的测试方法若最后一条语句返回非 Unit，JUnit4 报 `InvalidTestClassError: Method should be void`**。典型陷阱：`fun foo() = runBlocking { ... onNodeWithText(x).assertIsDisplayed() }` —— `assertIsDisplayed()` 返回 `SemanticsNodeInteraction`，`runBlocking` 推断方法返回类型非 Unit，JUnit4 拒绝。改用**块体** `fun foo() { runBlocking { ... }; ... }`，或保证表达式体最后一条返回 Unit（`assertThat(...).isEqualTo()` 返回 Unit，安全）。
- **`someStateFlow.first()` 拿到的是 `initialValue`，不是解析后的值**。`stateIn(WhileSubscribed, initialValue = X)` 首次订阅先发 `X` 再异步解析上游；`.first()` 立即返回首条 = `X`。要等解析后的值，用带条件的 `.first { predicate }`（如 `.first { it.bookName.isNotEmpty() }`），或用 Turbine 收集多帧。
- **in-memory Room + 异步 Flow query 的 connectedAndroidTest 偶发 "connection pool has been closed"**（#9 重跑时撞到 #8 的 `progress_followsNewCurrentBook_afterSwitch`）。原因：`tearDown` 的 `db.close()` 在某测试的异步 query 还在跑时执行。**重跑即过，不是回归**。应对：测试里 `runBlocking` 同步等 query 完成再 close，或接受偶发重跑。
- **测试断言版本号会硬编码 `onNodeWithText("版本 0.1.0")`**：bump `versionName` 时同步改测试。曾尝试前缀 `onNodeWithText("版本 ")` 避免 bump 碎裂，但 Compose substring 匹配在 "版本 "（带尾空格）上失败，回退硬编码。
- **`onNodeWithText("X")` 在弹窗里 X 同时出现在标题和正文时风险**（substring=true 默认同时命中两者）。`AlertDialog` 的 title 语义节点处理特殊，实测可能只命中正文（第一版通过），但 code-review 会抓。稳妥：正文用 `substring=false` 精确匹配。


## 代码层约定

跨文件、无法在单处注释承载的反复踩坑（code-review Standards 轴抓出来的）。

- **所有用户可见文案走 `stringResource(R.string.*)`，别硬编码中文字面量**（含含变量的拼接，用格式资源 `%1$s` / `%1$d`）。repo 所有 Composable（`DrillScreen` / `LibraryScreen` / `WordListScreen` / `MeScreen`）都走 `stringResource`；本地拼接字符串（如 `"$bookName：$x / $y"`）看似省事，实则在 i18n、文案一致性检查、code-review 上反复翻车。含变量用格式资源：`<string name="me_progress_line">%1$s：%2$d / %3$d（%4$d%%）</string>` + `stringResource(R.string.me_progress_line, bookName, x, y, percent)`。
- **别给 Composable/函数加"便于测试/预览"的投机参数**（AGENTS.md §2 Simplicity First）。#9 给 `WordDrillTheme` 加了 `systemDarkTheme: Boolean = isSystemInDarkTheme()` 参数，KDoc 写"便于测试/预览"，但**无任何测试或 `@Preview` 用到它**，code-review 抓为 Speculative Generality 删掉。测试要注入就显式传真实依赖（如 ViewModel），别在生产 Composable 签名上开投机口子。
