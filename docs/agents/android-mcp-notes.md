# Android Emulator MCP — 操作备忘

用 `android-emulator` MCP 辅助开发时反复踩的工具层坑。代码层的红线（别启用 Hilt Plugin、别升级 AndroidX 等）在各自代码注释里，不在此重复。

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
