修复 Issue #20 跳过功能的两个 bug:复习词书里没有「恢复」入口 + 在复习词书点跳过会永久丢词。

## 设计决策(已与你确认)
- 复习词书按钮文案改「恢复」,点击 = 该词在所有原词书 `skipped` 置 0 + 从复习词书 unlink
- 原词书词条列表不显示被跳过的词(保持当前行为),恢复入口只在复习词书

## 改什么

### 1. BookDao.kt — 加恢复用 DAO 方法
- `unskipWordEverywhere(wordId: Long)`: `UPDATE book_word SET skipped = 0 WHERE wordId = :wordId`。一次性把该词在所有词书(CET-4/CET-6…)的 skipped 标记全清,词回到所有原词书可刷。
- 复用已有的 `unlinkBookWord(bookId, wordId)` 从复习词书移除关联。

### 2. DrillViewModel.kt — 状态加标志 + 恢复方法
- `DrillUiState.Ready` 加 `isReviewBook: Boolean` 字段
- `setReady` 时按 `book.name == "复习"`(已有常量 `REVIEW_BOOK_NAME`)判断并传入
- 新增 `restoreCurrentWord(currentPage: Int)`:事务里 `unskipWordEverywhere(wordId)` + `unlinkBookWord(reviewBookId, wordId)` + 重载卡片。复习词书列表变短,被恢复的词回到所有原词书。
- `skipCurrentWord` 加守卫 `if (isReviewBook) return`(防误触,UI 不该显示跳过按钮但兜底)

### 3. DrillScreen.kt — 按钮按词书类型切换
- `DrillPager` 加 `isReviewBook: Boolean` + `onRestore: (Int) -> Unit` 参数
- `DrillTopBar` 按钮文案:复习词书用 `R.string.drill_restore` + 调 `onRestore`,普通词书用 `R.string.drill_skip` + 调 `onSkip`
- `DrillScreen` 把 `viewModel::restoreCurrentWord` 接上

### 4. strings.xml
- 加 `<string name="drill_restore">恢复</string>`

## 测试
- **BookDao 测试**(`WordDrillDatabaseTest`):`unskipWordEverywhere` 把 CET-4+CET-6 同词的 skipped 全清;复习词书 unlink 后词数减少
- **DrillScreenTest**:复习词书态显示「恢复」按钮 + 点击触发 onRestore 回调(而非 onSkip)
- **回归**:普通词书仍显示「跳过」,跳过/计数行为不变

## 验证
- 构建 + JVM 单测 + 关键 instrumented 测试(Migration34 不受影响,跑 DrillScreenTest + WordDrillDatabaseTest)
- 模拟器手验:复习词书显示「恢复」→ 点恢复 → 词从复习词书消失 + 回到 CET-4 可刷

## 不改
- 数据库 schema / migration(不动)
- 原词书词条列表(被跳过的词仍不显示,恢复入口只在复习词书)
- ReviewBookInitializer / 复习词书创建逻辑