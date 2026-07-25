## Parent

#1 (WordDrill 规格文档)

## What to build

按 `designs/worddrill-ui/` 设计稿，更新现有 App 的视觉层 + 补齐音标数据链路。**不改业务逻辑（刷卡计数、词书增删改、统计聚合、导出导入），但音标涉及数据模型变更。**

设计稿位置：
- 主设计稿：`designs/worddrill-ui/index.html`（三 Tab 完整原型）
- 设计 token 和动画规范：`designs/worddrill-ui/index.html` 的 style 块
- 组件实现：`designs/worddrill-ui/screens.jsx` + `icons.jsx` + `app.jsx`

### 改什么

**配色 token**（Color.kt / Theme.kt）：
- 浅色：底 #FAFAFA，文字 #1A1A1A，次级 #86868B，三级 #AEAEB2
- 深色：底纯黑 #000000，表面 #1C1C1E，文字 #FFFFFF
- 纯黑白灰，无任何彩色强调色

**字体**（Type.kt）：
- 单词：系统无衬线，600 字重，44px，负字距
- 音标：Charis SIL（衬线），17px，次级灰
- 词性：系统无衬线 + 斜体，15px，最淡灰
- 中文释义：设备原生中文字体，22px，400 字重

**音标数据链路**（数据模型变更，spec 已更新 #1）：
- word 表加 phonetic 字段（可为空）
- Room migration 处理升级
- 预置词库 JSON 补音标数据（IPA 格式），首启导入逻辑同步更新
- 卡片展示顺序：单词 → 音标（若有）→ 分割线 → 义项列表

**动画**（spring，Apple Design 规范）：
- 卡片切换：critically damped spring（damping 1.0, response 0.35s），translateX + scale
- 词书选中：spring 高亮（实心反色块渐现），damping 1.0
- 进度条：spring 宽度过渡
- Tab 切换：cross-fade

**排版**：
- 刷卡页：顶部 词书名 ←→ 跳过 对称布局 → 单词 → 音标 → 分割线 → 多义项（词性+释义同行）
- 词书列表：去掉 icon，纯文字列表；选中 = 实心反色块（黑底白字 + check），spring 动画，原地选中不跳转
- 我的页：统计卡片（大数字 + 进度条 + 累计），设置组（圆角卡片）

**底部导航**：毛玻璃/半透明效果，Tab 切换 spring 缩放

### 不包含（另开 ticket）

- "跳过"功能逻辑（加入复习词书）— 新功能，需更新 spec
- App icon / splash — 发布任务

## Acceptance criteria

- [ ] 配色改为纯黑白灰，深浅两套 token 正确
- [ ] 单词用系统无衬线 600 字重，词性用斜体，中文用设备原生字体
- [ ] word 表加 phonetic 字段，Room migration 正确
- [ ] 音标 UI 用 Charis SIL 或等效衬线字体
- [ ] 预置词库 JSON 补音标数据（IPA），首启导入逻辑同步更新
- [ ] 卡片展示顺序：单词 → 音标 → 分割线 → 义项列表
- [ ] 卡片切换有 spring 动画（critically damped）
- [ ] 词书列表选中态为实心反色块 + spring 动画，点选原地高亮不跳转
- [ ] 词书列表去掉 icon，纯文字
- [ ] 底部导航有毛玻璃/半透明效果，Tab 切换有 spring 缩放
- [ ] 我的页统计卡片排版更新
- [ ] 进度条有 spring 宽度过渡
- [ ] 深色模式所有元素颜色正确反转
- [ ] 底部导航改为浮动胶囊（iOS 18+ 风格），居中悬浮，选中项实心填充
- [ ] 设置新增：隐藏音标开关（开启后刷卡页不显示音标）
- [ ] 设置新增：极简模式开关（开启后刷卡页隐藏导航栏）
- [ ] 设置新增：导航栏风格选择（浮动胶囊 / 底部栏）
- [ ] 词书选中态改为浅灰背景（不再实心反色），暗色模式副标题可读
- [ ] 现有功能全部不受影响（刷卡、词书增删改、统计、导出导入）
- [ ] 现有测试全绿（允许更新 UI 断言以匹配新布局）

## Spec source

- 设计稿：designs/worddrill-ui/（HTML/JSX 原型，含完整设计 token 和组件实现）
- 动画规范：designs/worddrill-ui/index.html 的 spring CSS token
- 数据模型：Issue #1 已更新 word 表加 phonetic 字段
- 交互参考：用键盘左右键在刷卡页切换卡片

## Blocked by

- None — 可立即开始
