# Android Emulator MCP working notes

Recurring tool-layer pitfalls hit while developing with the `android-emulator` MCP, plus a few cross-file code conventions that do not fit in a single code comment (see "Code conventions" at the end). The remaining code-level rules, such as not enabling the Hilt Gradle plugin and not upgrading AndroidX, are documented in their own code comments and are not repeated here.

## Environment

- JDK 17 lives at `C:\jdk17`. Prefix every `./gradlew` command with `JAVA_HOME=C:/jdk17`.
- AVD: `Pixel_API_36`, serial `emulator-5554`, usually already running.
- adb: `C:/Users/Administrator/AppData/Local/Android/Sdk/platform-tools/adb.exe`. The MCP wraps it; raw adb needs the full path.

## Build / run

1. Run `android_preflight` to confirm the environment.
2. Build: `JAVA_HOME=C:/jdk17 ./gradlew :app:assembleDebug`.
3. Install with `android_install_app`; launch with `adb shell am start -n com.github.chsiching.worddrill/.MainActivity`.
   - Both `android_launch_app` and `android_build_and_run` go through monkey, which fails with "No activities found to run, monkey aborted" under manual Hilt (no Gradle plugin). `am start` launches the Activity directly and works reliably.
4. To verify a clean state, clear data. Run `android_install_app` first, then `pm clear`. The reverse order can fail with "Failed" because the previous install state is gone.
   - `pm clear` sometimes returns a non-zero exit code while actually succeeding. Wrap it and check the code: `adb shell "pm clear com.github.chsiching.worddrill; echo done=$?"`. `done=0` means success.
   - connectedAndroidTest uninstalls the app when it finishes. Before the next manual verification, check the app still exists with `adb shell pm list packages | grep word` and reinstall via `android_install_app` if needed.

## Pixel-level verification (used since #9)

`android_screenshot` shows what is on screen, but asserting a color change such as "dark theme took effect" needs pixel values. Capture a PNG with adb, then read pixel RGB values through node_repl with pngjs.

1. Capture: `adb exec-out screencap -p > /tmp/x.png`. The MCP's `android_screenshot` returns an internal MCP path; raw adb is more controllable.
2. Read pixels in node_repl: `const { PNG } = await import('pngjs'); const png = PNG.sync.read(fs.readFileSync(path)); const [r,g,b] = px(x,y)`.
3. pngjs is installed at `C:/Users/Administrator/AppData/Local/Temp/node_modules`. In node_repl, add that directory first with `js_add_node_module_dir`.
4. Sample pure background areas, away from text and icons. The dark-theme content background is about `[18,19,24]`, light about `[250,248,255]`. Comparing the two asserts theme switching.
5. The `analyze_image` MCP only accepts remote URLs, so local screenshots cannot use it. The pixel method replaces it.
6. After `js_reset`, the path added by `js_add_node_module_dir` is lost. Add it again. Both `true` (added) and `false` (already present) returns are normal.
7. A wrong `import()` in node_repl poisons the whole kernel. If the catch of `import('pkg')` returns null, for example `await import('/dev/null').catch(()=>({PNG:null}))`, the `PNG` binding becomes null and every later `PNG.sync.read(...)` throws "Cannot read properties of null". `const PNG` cannot be redeclared, so the only way out is `js_reset`, which loses all bindings including fs and the module path. Prevention: write the import correctly the first time, `const { PNG } = await import('pngjs')`, with no placeholder fallbacks. If a binding is already broken, run `js_reset` and start over instead of trying to overwrite it.

## Verifying UI

- `android_screenshot` shows the truth; `android_ui_describe` returns the element tree with coordinates; `android_ui_resolve` finds a specific element.
- `android_ui_describe` sometimes returns a stale snapshot, for example still showing a dialog that already closed. When in doubt, trust `android_screenshot`.
- For documentation or behavior questions, check the official Android docs via context7, such as Compose Testing APIs and UIAutomator. The android-emulator MCP itself has no public docs, so do not guess operations.

## IME cannot type into Compose TextField

Neither `android_ui_type_text` nor `adb shell input text` can enter text into a Compose `TextField` or `OutlinedTextField`. The backends are adb-input and UIAutomator, which inject KeyEvents. Compose goes through the IME channel, and the two are incompatible. context7 on Compose Testing APIs confirms the right path is `performSemanticsAction`, which the MCP does not expose.

Workarounds, given that MCP cannot automate the input flow:

1. Extract testable logic into pure functions covered by JVM unit tests, such as `validateBookName`.
2. Cover DAO write paths with `WordDrillDatabaseTest` using in-memory Room.
3. Verify end-to-end persistence directly with adb, together with the `wal_checkpoint` trick below.
4. Verify UI rendering with MCP screenshot and describe. Do not expect to drive TextField input.

## Reading the Room database

A freshly written database can read empty because of WAL. Checkpoint first, then query:

```
adb shell "run-as com.github.chsiching.worddrill sqlite3 databases/worddrill.db 'PRAGMA wal_checkpoint(TRUNCATE); SELECT ...;'"
```

Reading DataStore, to see the varint value of `current_book_id`:

```
adb shell "run-as com.github.chsiching.worddrill sh -c 'cat files/datastore/worddrill.preferences.preferences_pb | od -An -tx1'"
```

## Tests

- `connectedDebugAndroidTest` hangs forever in `MeScreenTest.themeIcon_darkToLight_togglesBack` (pre-existing, no CI baseline; issue #29). Run the suite with `-Pandroid.testInstrumentationRunnerArguments.notClass=com.github.chsiching.worddrill.ui.me.MeScreenTest`; the remaining 155 tests finish in about a minute. Never run two Gradle test invocations against the same emulator: the loser leaves locked files under `app/build/outputs/androidTest-results`, and the fix is `./gradlew --stop`, force-stop both app processes, delete that directory, and rerun.
- `createComposeRule` / `createAndroidComposeRule` carry v2 deprecation warnings. Do not migrate. Stay on v1, matching the existing tests; the warnings are harmless. v2 uses StandardTestDispatcher, behaves differently, and would force a rewrite of the synchronization logic.
- Any Flow `collect` with write side effects must use `distinctUntilChanged`, or repeated emits write the same value to the database repeatedly. `HorizontalPager`'s `settledPage` and list-refresh callbacks both emit the same value more than once.
- Assert with `Truth.assertThat`, not `kotlin.assert`. Kotlin's `assert` on the JVM needs the `-ea` flag, which JUnit4 does not set by default, so `assert(x)` always passes and tests nothing. The repo convention is `com.google.common.truth.Truth.assertThat`; see `WordDrillDatabaseTest` and `BookNameValidationTest`.
- `onNodeWithText` does substring matching by default; the error message literally says "contains '...'". `onNodeWithText("统计")` also matches "统计数据" or "今日统计" and then fails with "found N nodes". Use a longer, more precise string, or pass `onNodeWithText("统计", substring = false)`.
- `performTextInput` appends, it does not replace. Calling `performTextInput("X")` on a TextField holding "苹果" produces "苹果X". To replace the whole value, use `performTextReplacement("X")`.
- In tests, target an `IconButton` click through `onNodeWithContentDescription(...)`, which matches `contentDescription`, not `onNodeWithText`, which matches displayed text. An IconButton usually has only an icon and no text.
- A test method written as `= runBlocking { }` whose last statement returns non-Unit makes JUnit4 reject the class with `InvalidTestClassError: Method should be void`. The classic trap is `fun foo() = runBlocking { ... onNodeWithText(x).assertIsDisplayed() }`. `assertIsDisplayed()` returns `SemanticsNodeInteraction`, `runBlocking` infers a non-Unit return type, and JUnit4 refuses. Use a block body, `fun foo() { runBlocking { ... }; ... }`, or make the expression body end in a Unit call. `assertThat(...).isEqualTo()` returns Unit and is safe.
- `someStateFlow.first()` returns the `initialValue`, not the resolved value. A `stateIn(WhileSubscribed, initialValue = X)` flow emits `X` first and resolves the upstream asynchronously afterwards, so `.first()` returns immediately with that first emission. To wait for the resolved value, use a conditional `.first { predicate }` such as `.first { it.bookName.isNotEmpty() }`, or collect several frames with Turbine.
- In-memory Room plus an asynchronous Flow query in connectedAndroidTest occasionally throws "connection pool has been closed". This was hit while rerunning #9, on #8's `progress_followsNewCurrentBook_afterSwitch`. The cause is `tearDown`'s `db.close()` running while another test's async query is still in flight. A rerun passes, so it is not a regression. Remedy: inside the test, wrap the query in runBlocking so it finishes before close, or accept the occasional rerun.
- Tests that assert the version number hardcode `onNodeWithText("版本 0.1.0")`. When bumping `versionName`, update the test too. A prefix match `onNodeWithText("版本 ")` was tried to survive bumps, but Compose substring matching failed on "版本 " with its trailing space, so the hardcode stayed.
- `onNodeWithText("X")` is risky inside a dialog where X appears in both the title and the body, since substring matching is on by default and hits both. `AlertDialog`'s title semantics node is special; in practice only the body matched and the first version passed, but code review flags the pattern. The safe form is an exact match on the body with `substring = false`.
- User-visible text living in a ViewModel must also go through `R.string` (caught by code review on #10). A VM is not a Composable and cannot call `stringResource`, but hardcoding Chinese or English inside the VM, such as `error("无法打开文件")` or `State("导出完成")`, puts that text on screen, which violates the convention that user-visible text goes through R.string. The correct pattern is a sealed status carrying a `@StringRes Int`. The VM passes only the resource id, as in `Done(R.string.me_export_done)` or `Failed(R.string.me_export_failed, e.message)`, and the Composable resolves it with `stringResource(status.messageRes)`. A bonus: the same sealed type with different ids separates export completion from import completion, avoiding the bug where an import shows "export finished".
- SAF file picking (Storage Access Framework) can be driven end to end with MCP (#10). Compose uses `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"))` or `OpenDocument()`. After confirming, the system opens DocumentsUI, package `com.google.android.documentsui`, which lives outside the app. MCP `android_ui_describe` reaches the SAVE button (`android:id/button1`), the filename input, and the CardView list items, so `android_ui_tap` can tap a file, hand the Uri to the VM, and write the database. To verify a full export, read the real JSON with `adb exec-out "cat /sdcard/Download/worddrill-backup.json"`. The pixel method does not apply to file contents.

## Code conventions

Cross-file pitfalls repeatedly caught by code review on the Standards axis, which do not fit in a single code comment.

- All user-visible text goes through `stringResource(R.string.*)`. Never hardcode Chinese literals, including concatenation with variables; use format resources with `%1$s` and `%1$d`. Every Composable in the repo, `DrillScreen`, `LibraryScreen`, `WordListScreen`, `MeScreen`, uses `stringResource`. Local concatenation such as `"$bookName：$x / $y"` looks cheaper but keeps breaking i18n, copy consistency checks, and code review. With variables, use a format resource: `<string name="me_progress_line">%1$s：%2$d / %3$d（%4$d%%）</string>` plus `stringResource(R.string.me_progress_line, bookName, x, y, percent)`.
- Do not add speculative parameters to Composables or functions "for tests or previews" (AGENTS.md §2 Simplicity First). On #9, `WordDrillTheme` gained a `systemDarkTheme: Boolean = isSystemInDarkTheme()` parameter whose KDoc said it existed for tests and previews, but no test or `@Preview` ever used it. Code review flagged it as Speculative Generality and it was deleted. When a test needs an injection, pass real dependencies explicitly, such as the ViewModel. Do not add speculative parameters to production Composable signatures.
- Import overwrite, meaning clearing the whole database and reinserting, must run inside a Room `@Transaction`, and must clear child tables before parents (#10). `sense` and `book_word` carry CASCADE foreign keys into `word` and `book`; `swipe_log` has no foreign key, so orphan logs stay for cumulative stats. Clear `deleteAllSenses`, `deleteAllLinks`, `deleteAll(swipe_log)` first, then `deleteAll(book)` and `deleteAll(word)`; reinsert in the reverse order. `BackupService.import` wraps everything in `db.withTransaction { ... }`, so a failure rolls back without leaving a half-imported state. Restore original primary keys, `@Insert(onConflict=REPLACE)` carrying the original ids, to keep cross-table references and orphan logs intact.
