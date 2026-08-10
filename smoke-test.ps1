# 端到端冒烟测试:跑通学生 + 管理员全部核心链路(对本地 8080)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api'
$pass = @()
$fail = @()

function Step($name, $block) {
  try {
    & $block | Out-Null
    $script:pass += $name
  } catch {
    $script:fail += "$name -> $($_.Exception.Message)"
  }
}

# 1. 学生登录
$s = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' `
  -Body '{"email":"zhang@stu.ioedu.cn","password":"123456"}'
$sh = @{ Authorization = "Bearer $($s.data.token)" }
$pass += '学生登录'

# 2. 管理员登录
$a = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' `
  -Body '{"email":"admin@ioedu.cn","password":"admin123"}'
$ah = @{ Authorization = "Bearer $($a.data.token)" }
$pass += '管理员登录'

# 3. 项目列表 / 详情 / 收藏切换(两次,恢复原状)
$projects = (Invoke-RestMethod "$base/projects?sort=popular" -Headers $sh).data
Step '项目列表' { if ($projects.Count -lt 1) { throw '空列表' } }
$pid1 = $projects[0].id
Step '项目详情' { Invoke-RestMethod "$base/projects/$pid1" -Headers $sh }
Step '收藏/取消收藏' {
  Invoke-RestMethod "$base/projects/$pid1/favorite" -Method Post -Headers $sh
  Invoke-RestMethod "$base/projects/$pid1/favorite" -Method Post -Headers $sh
}

# 4. 报名 + 推进进度(重复报名报错也算通过——说明校验生效)
Step '项目报名/防重复' {
  try {
    Invoke-RestMethod "$base/projects/$pid1/enroll" -Method Post -Headers $sh | Out-Null
  } catch {
    if ($_.ErrorDetails.Message -notmatch '已报名') { throw }
  }
}
Step '进度更新' {
  Invoke-RestMethod "$base/projects/$pid1/progress" -Method Put -Headers $sh `
    -ContentType 'application/json' -Body '{"progress":20,"currentTask":"冒烟测试推进"}'
}

# 5. 讨论发帖
Step '项目讨论发帖' {
  Invoke-RestMethod "$base/projects/$pid1/discussions" -Method Post -Headers $sh `
    -ContentType 'application/json' -Body '{"content":"接口冒烟测试帖"}'
}

# 6. 设备 + 借阅全状态机:申请→批准→归还申请→归还验收
$equip = (Invoke-RestMethod "$base/equipment" -Headers $sh).data
$target = $equip | Where-Object { $_.status -eq 'AVAILABLE' -and $_.availableCount -gt 0 } | Select-Object -First 1
$borrowBody = @{
  equipmentId = $target.id; quantity = 1; purpose = '课程实验'
  projectName = '冒烟测试'; startDate = (Get-Date).ToString('yyyy-MM-dd'); durationDays = 3; remark = 'API冒烟'
} | ConvertTo-Json
$borrow = (Invoke-RestMethod "$base/borrows" -Method Post -Headers $sh -ContentType 'application/json' -Body $borrowBody).data
$pass += "借阅申请($($borrow.requestNo))"
$bid = $borrow.id
Step '管理员批准(扣库存)' {
  Invoke-RestMethod "$base/admin/borrows/$bid/decide" -Method Post -Headers $ah `
    -ContentType 'application/json' -Body '{"action":"approve"}'
}
Step '学生申请归还' { Invoke-RestMethod "$base/borrows/$bid/return" -Method Post -Headers $sh }
Step '管理员归还验收(回补库存)' { Invoke-RestMethod "$base/admin/borrows/$bid/confirm-return" -Method Post -Headers $ah }
Step '库存回补校验' {
  $after = (Invoke-RestMethod "$base/equipment/$($target.id)" -Headers $sh).data
  if ($after.availableCount -ne $target.availableCount) { throw "库存不一致 $($after.availableCount) vs $($target.availableCount)" }
}

# 7. 技能测评
Step '技能测评提交' {
  Invoke-RestMethod "$base/skills/assess" -Method Post -Headers $sh -ContentType 'application/json' `
    -Body '{"scores":{"嵌入式开发":66,"PCB设计":58,"编程能力":46,"通信技术":52,"信号处理":39,"硬件调试":61}}'
}

# 8. 通知
Step '通知列表/全部已读' {
  Invoke-RestMethod "$base/notifications" -Headers $sh
  Invoke-RestMethod "$base/notifications/read-all" -Method Post -Headers $sh
}

# 9. 个人中心 / 公开统计 / 管理看板与报表
Step '个人中心汇总' { Invoke-RestMethod "$base/dashboard" -Headers $sh }
Step '公开统计' { Invoke-RestMethod "$base/public/stats" }
Step '管理看板统计' { Invoke-RestMethod "$base/admin/stats" -Headers $ah }
Step '管理报表趋势' { Invoke-RestMethod "$base/admin/trends" -Headers $ah }

# 10. 管理端设备 CRUD(建一台测试设备再删除,不留垃圾)
$newEq = @{
  name = '冒烟测试设备-可删'; model = 'TEST-1'; category = '工具'; location = 'A栋3楼'
  totalCount = 1; availableCount = 1; price = 1; manufacturer = 'TEST'; status = 'AVAILABLE'
  description = '接口测试临时设备'; specs = '[]'; tags = '[]'; docs = '[]'; suitableProjects = '[]'
} | ConvertTo-Json
$created = (Invoke-RestMethod "$base/admin/equipment" -Method Post -Headers $ah -ContentType 'application/json' -Body $newEq).data
$pass += '管理员新增设备'
Step '管理员编辑设备' {
  $upd = $newEq | ConvertFrom-Json; $upd | Add-Member id $created.id -Force; $upd.price = 2
  Invoke-RestMethod "$base/admin/equipment/$($created.id)" -Method Put -Headers $ah `
    -ContentType 'application/json' -Body ($upd | ConvertTo-Json)
}
Step '管理员删除设备' { Invoke-RestMethod "$base/admin/equipment/$($created.id)" -Method Delete -Headers $ah }

# 11. 用户管理:列表
Step '用户列表' { Invoke-RestMethod "$base/admin/users" -Headers $ah }

Write-Host "===== PASS ($($pass.Count)) ====="
$pass | ForEach-Object { Write-Host "  OK  $_" }
if ($fail.Count -gt 0) {
  Write-Host "===== FAIL ($($fail.Count)) ====="
  $fail | ForEach-Object { Write-Host "  XX  $_" }
  exit 1
}
Write-Host "ALL GREEN"
