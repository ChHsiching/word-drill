import subprocess, json

result = subprocess.run(['gh', 'issue', 'view', '1', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# 1. Add new user stories after #27
old_story = '27. 作为学习者，我想在「我的」Tab 查看关于页面，这样我能了解 App 版本等信息。'
new_stories = (
    '27. 作为学习者，我想在「我的」Tab 查看关于页面，这样我能了解 App 版本等信息。\n'
    '28. 作为学习者，我想在设置里隐藏音标，这样刷卡时只看单词和释义，更简洁。\n'
    '29. 作为学习者，我想开启极简模式，这样刷卡时隐藏导航栏，全屏沉浸不被打扰。\n'
    '30. 作为学习者，我想切换导航栏风格（浮动胶囊/底部栏），这样我能选择自己喜欢的导航样式。'
)
body = body.replace(old_story, new_stories)

# 2. Update theme section to add new settings
old_theme = '- 支持深色/浅色/跟随系统三种主题模式，设置项在「我的」Tab。跟随系统模式使用 Android 系统的深浅色配置，自动适配。'
new_theme = (
    '- 支持深色/浅色/跟随系统三种主题模式，设置项在「我的」Tab。跟随系统模式使用 Android 系统的深浅色配置，自动适配。\n'
    '- 隐藏音标：设置开关，开启后刷卡页不显示音标。\n'
    '- 极简模式：设置开关，开启后刷卡页隐藏导航栏，纯全屏沉浸。\n'
    '- 导航栏风格：可选浮动胶囊（iOS 18+ 风格，默认）或底部栏。'
)
body = body.replace(old_theme, new_theme)

with open('.scratch/issue1_update2.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
