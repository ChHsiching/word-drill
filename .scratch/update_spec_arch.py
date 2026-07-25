import subprocess, json

result = subprocess.run(['gh', 'issue', 'view', '1', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# Remove Windows CR artifacts
body = body.replace('\r\r\r', '')

lines = body.split('\n')

# 1. Add new user stories after the last one (#32)
last_story_idx = max(i for i, l in enumerate(lines) if l.strip().startswith('32.'))
new_stories = [
    '33. 作为学习者，我想在导入词书时支持 Excel/文本/PDF 文件，这样我能批量导入词书而不是手动输入。',
    '34. 作为学习者，我想导入时系统自动从内置词典查词性和释义，这样我不需要手动填写每个词的详细信息。',
    '35. 作为学习者，我想点击跳过后这个词不再出现在当前词书中（但可恢复），并自动加入复习词书，这样我能专注刷不熟的词。',
    '36. 作为学习者，我想有一个复习词书（自动收集所有被跳过的词），这样我能集中复习熟悉的词。',
    '37. 作为学习者，我想删除的词书和词条进入回收站而非永久删除，这样误删后可以恢复。',
    '38. 作为学习者，我想添加词条时词性从标准列表选择而非手动输入，这样不会输错词性。',
]
for s in reversed(new_stories):
    lines.insert(last_story_idx + 1, s)

# 2. Add dictionary table to schema
for i, l in enumerate(lines):
    if 'swipe_log' in l and 'log_id' in l:
        # Add skipped field to book_word and dictionary table after swipe_log
        lines[i] = l.rstrip() + '\n  - `book_word` 关联表新增 `skipped`（0/1，默认0，标记被跳过的词）\n  - `dictionary` 表（只读，预置 ~10 万词）：`dict_id`（主键）、`word`（单词）、`phonetic`（音标）、`pos`（词性）、`meaning`（释义）。独立于 word 词池，仅用于查词参考。\n  - `deleted` 软删除标记：`book` 表和 `word` 表加 `deleted`（0/1，默认0）。回收站 = deleted=1 的记录。'
        break

# 3. Add implementation decisions section for new features
impl_marker = '### 数据导出/导入'
new_impl = """### 内置词典
- ECDICT 完整版（~10 万词），打包为 `dictionary` 表（只读参考数据）。
- dictionary 独立于 word 词池：dictionary 只读查词，word 词池可写、词书实际引用。
- 用户加词/导入 → 先查 dictionary 自动填充词性+释义+音标 → 写入 word 词池 → book_word 引用。

### 词性缩写解析
- 固定正则缩写表：n. v. vt. vi. aux.v. adj. a. adv. ad. prep. conj. pron. art. num. int. interj. abbr.
- 解析文件第四列"词性+释义"文本时，按缩写分段切分。

### 文件导入词书
- 支持格式：xlsx（Fastexcel）/ txt+csv（OpenCSV）/ pdf（Tabula-java）。不支持 docx。
- 列结构：列1序号(忽略) / 列2单词(必须) / 列3音标(可选) / 列4词性释义(可选，格式如 "a.平坦的 n.公寓")。
- 单词处理：1)查dictionary→有则用词典数据 2)词典没有→用文件列3+列4 3)都空→跳过计"数据不完整"。
- 导入 = 创建新词书（先命名再导入）。去重静默处理。统计：成功X个/数据不完整Y个（去重不提示）。

### 跳过 → 复习词书
- book_word 加 skipped 标记（0/1）。
- 跳过 = 标记 skipped=1（隐藏不删，可恢复）+ 加到复习词书。
- 词书级隐藏（CET-4 跳过不影响 CET-6）。
- 刷卡只显示 skipped=0。
- 复习词书 = 预置词书（is_preset=true），默认空，收集所有被跳过的词（跨词书去重）。和普通词书一样刷卡。

### 回收站（软删除）
- book 表和 word 表加 deleted 标记（0/1）。
- 删除 = 标记 deleted=1（软删除）。回收站 = deleted=1 的记录。
- 覆盖：删词书、删词条。回收站可恢复。
- 不做完整 undo 栈或操作历史日志。

"""
body = '\n'.join(lines)
body = body.replace(impl_marker, new_impl + impl_marker)

with open('.scratch/issue1_arch.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
