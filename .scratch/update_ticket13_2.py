import subprocess, json

# Update Issue #13 — add lock ACs
result = subprocess.run(['gh', 'issue', 'view', '13', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

old_ac = '- [ ] 词书选中态改为浅灰背景（不再实心反色），暗色模式副标题可读'
new_acs = (
    '- [ ] 刷卡页顶部加锁定按钮（锁形图标），点击锁定\n'
    '- [ ] 锁定后导航栏 spring 动画滑出消失（opacity + translateY + scale），解锁后 spring 滑回\n'
    '- [ ] 锁定图标在锁定/解锁态间 spring 变化（背景色 + 图标切换）\n'
    '- [ ] 锁定时隐藏跳过按钮，纯沉浸\n'
    '- [ ] 切换 Tab 时自动解锁\n'
    '- [ ] 词书选中态改为浅灰背景（不再实心反色），暗色模式副标题可读'
)
body = body.replace(old_ac, new_acs)

with open('.scratch/issue13_update3.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
