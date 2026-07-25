import subprocess, json

result = subprocess.run(['gh', 'issue', 'view', '1', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# 1. Delete 极简模式 user story #29
body = body.replace(
    '29. 作为学习者，我想开启极简模式，这样刷卡时隐藏导航栏，全屏沉浸不被打扰。\n',
    ''
)

# 2. Delete 极简模式 from settings description
body = body.replace('- 极简模式：设置开关，开启后刷卡页隐藏导航栏，纯全屏沉浸。\n', '')

# 3. Add 简约导航 user story
body = body.replace(
    '30. 作为学习者，我想切换导航栏风格（浮动胶囊/底部栏），这样我能选择自己喜欢的导航样式。',
    '30. 作为学习者，我想切换导航栏风格（浮动胶囊/底部栏），这样我能选择自己喜欢的导航样式。\n'
    '32. 作为学习者，我想开启简约导航（隐藏导航栏文字标签），这样导航栏更紧凑不占空间。'
)

# 4. Update navigation settings description — add 简约导航, update lock + nav style
body = body.replace(
    '- 导航栏风格：可选浮动胶囊（iOS 18+ 风格，默认）或底部栏。',
    '- 导航栏风格：可选浮动胶囊（iOS 18+ 风格，默认）或底部栏。切换时交叉淡入淡出（非形态过渡）。\n'
    '- 简约导航：隐藏导航栏文字标签，导航栏收缩变扁。'
)

with open('.scratch/issue1_update4.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
