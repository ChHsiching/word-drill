import subprocess, json

result = subprocess.run(['gh', 'issue', 'view', '13', '--json', 'body'], capture_output=True, text=True)
body = json.loads(result.stdout)['body']

# Add new ACs before "现有功能全部不受影响"
old_ac = '- [ ] 现有功能全部不受影响（刷卡、词书增删改、统计、导出导入）'
new_acs = (
    '- [ ] 底部导航改为浮动胶囊（iOS 18+ 风格），居中悬浮，选中项实心填充\n'
    '- [ ] 设置新增：隐藏音标开关（开启后刷卡页不显示音标）\n'
    '- [ ] 设置新增：极简模式开关（开启后刷卡页隐藏导航栏）\n'
    '- [ ] 设置新增：导航栏风格选择（浮动胶囊 / 底部栏）\n'
    '- [ ] 词书选中态改为浅灰背景（不再实心反色），暗色模式副标题可读\n'
    '- [ ] 现有功能全部不受影响（刷卡、词书增删改、统计、导出导入）'
)
body = body.replace(old_ac, new_acs)

with open('.scratch/issue13_update2.md', 'w', encoding='utf-8') as f:
    f.write(body)
print('WRITTEN')
