import subprocess, json

result = subprocess.run(['gh', 'issue', 'view', '13', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# Replace the nav-related ACs with final accurate ones
old_nav_acs = (
    '- [ ] 底部导航改为浮动胶囊（iOS 18+ 风格），居中悬浮，选中项实心填充\n'
    '- [ ] 刷卡页顶部加锁定按钮（锁形图标），点击锁定\n'
)
new_nav_acs = (
    '- [ ] 浮动胶囊导航（iOS 18+）：居中悬浮，毛玻璃，选中项黑底指示器\n'
    '- [ ] 选中指示器在 Tab 间滑动（0.3s spring-smooth 曲线，快进慢出）\n'
    '- [ ] 底部栏风格：全宽贴底，选中项用文字亮度高亮（无黑块）\n'
    '- [ ] 导航风格切换：交叉淡入淡出（fading 只用 opacity，不用 transform），非形态过渡\n'
    '- [ ] 切换到胶囊时指示器直接在正确位置渐显（不飘移）\n'
    '- [ ] 刷卡页顶部锁定按钮（锁形图标），点击锁定\n'
)
body = body.replace(old_nav_acs, new_nav_acs)

# Remove 极简模式 AC if exists, add 简约导航 AC
body = body.replace('- [ ] 设置新增：极简模式开关（开启后刷卡页隐藏导航栏）\n', '')
body = body.replace(
    '- [ ] 设置新增：导航栏风格选择（浮动胶囊 / 底部栏）',
    '- [ ] 设置新增：导航栏风格选择（浮动胶囊 / 底部栏）\n'
    '- [ ] 设置新增：简约导航开关（隐藏导航栏文字标签，收缩变扁）'
)

with open('.scratch/issue13_update4.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
