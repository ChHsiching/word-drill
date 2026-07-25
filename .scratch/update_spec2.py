import subprocess, json

# Update Issue #1 — add lock user story + description
result = subprocess.run(['gh', 'issue', 'view', '1', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# Add user story #31 after #30
old = '30. 作为学习者，我想切换导航栏风格（浮动胶囊/底部栏），这样我能选择自己喜欢的导航样式。'
new = (
    '30. 作为学习者，我想切换导航栏风格（浮动胶囊/底部栏），这样我能选择自己喜欢的导航样式。\n'
    '31. 作为学习者，我想在刷卡页点锁定按钮隐藏导航栏，这样我能沉浸式刷词不被打扰，需要切换时再点解锁。'
)
body = body.replace(old, new)

# Add lock description in settings section
old_settings = '- 导航栏风格：可选浮动胶囊（iOS 18+ 风格，默认）或底部栏。'
new_settings = (
    '- 导航栏风格：可选浮动胶囊（iOS 18+ 风格，默认）或底部栏。\n'
    '- 锁定模式：刷卡页顶部锁形图标，点击锁定后导航栏 spring 动画滑出消失，再点击（图标变解锁态）恢复。锁定时跳过按钮也隐藏，纯沉浸。切 Tab 自动解锁。'
)
body = body.replace(old_settings, new_settings)

with open('.scratch/issue1_update3.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
