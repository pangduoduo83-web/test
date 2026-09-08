# 多租户冒烟测试:开通临时租户 → 登录 → 数据/令牌/文件隔离 → 停用 → 注销(连库删除)
# 前提:后端以 dev profile 运行在本地 8080(允许 X-Tenant-Id 请求头,平台令牌为开发值)
$ErrorActionPreference = 'Stop'
$base = if ($env:IOEDU_SMOKE_BASE) { $env:IOEDU_SMOKE_BASE } else { 'http://localhost:8080' }
$platformToken = if ($env:IOEDU_PLATFORM_TOKEN) { $env:IOEDU_PLATFORM_TOKEN } else { 'dev-platform-token-change-me' }
$adminEmail = if ($env:IOEDU_ADMIN_EMAIL) { $env:IOEDU_ADMIN_EMAIL } else { 'admin@ioedu.cn' }
$adminPassword = if ($env:IOEDU_ADMIN_PASSWORD) { $env:IOEDU_ADMIN_PASSWORD } else { 'admin123' }
$ph = @{ Authorization = "Bearer $platformToken" }
$code = 'smoke' + (Get-Random -Minimum 1000 -Maximum 9999)
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
function StatusOf($block) {
  try { & $block | Out-Null; return 200 } catch { return $_.Exception.Response.StatusCode.value__ }
}
function TenantHeaders($tenant, $token) {
  $h = @{ 'X-Tenant-Id' = $tenant }
  if ($token) { $h.Authorization = "Bearer $token" }
  return $h
}

# 1. 平台接口鉴权
Step '平台接口拒绝无令牌' { if ((StatusOf { Invoke-RestMethod "$base/api/platform/tenants" }) -ne 401) { throw '应为 401' } }
Step '平台接口列表' { (Invoke-RestMethod "$base/api/platform/tenants" -Headers $ph).data }

# 2. 开通临时租户(随机管理员密码)
$body = @{ code = $code; name = "冒烟租户 $code"; adminEmail = "admin@$code.test"; seedDemo = $false } | ConvertTo-Json
$created = (Invoke-RestMethod "$base/api/platform/tenants" -Method Post -Headers $ph -ContentType 'application/json; charset=utf-8' `
  -Body ([System.Text.Encoding]::UTF8.GetBytes($body))).data
$pass += "开通租户 $code(库 $($created.dbName))"
$tenantPwd = $created.initialAdminPassword
Step '重复开通被拒' { if ((StatusOf { Invoke-RestMethod "$base/api/platform/tenants" -Method Post -Headers $ph -ContentType 'application/json' -Body $body }) -eq 200) {
    $r = Invoke-RestMethod "$base/api/platform/tenants" -Method Post -Headers $ph -ContentType 'application/json' -Body $body
    if ($r.code -ne 409) { throw "应返回 409,实际 $($r.code)" } } }

# 3. 新租户登录与数据隔离
$t2 = (Invoke-RestMethod "$base/api/auth/login" -Method Post -Headers (TenantHeaders $code) -ContentType 'application/json' `
  -Body (@{ email = "admin@$code.test"; password = $tenantPwd } | ConvertTo-Json)).data.token
$pass += '新租户管理员登录'
$t1 = (Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' `
  -Body (@{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json)).data.token
$pass += '默认租户管理员登录'
Step '新租户无项目、默认租户有项目' {
  $n2 = (Invoke-RestMethod "$base/api/projects" -Headers (TenantHeaders $code $t2)).data.Count
  $n1 = (Invoke-RestMethod "$base/api/projects" -Headers @{ Authorization = "Bearer $t1" }).data.Count
  if ($n2 -ne 0 -or $n1 -lt 1) { throw "新租户 $n2 个,默认 $n1 个" }
}
Step '新租户用户列表只有自己' {
  $users = (Invoke-RestMethod "$base/api/admin/users" -Headers (TenantHeaders $code $t2)).data
  if ($users.Count -ne 1) { throw "应为 1 个用户,实际 $($users.Count)" }
}
Step '默认租户令牌在新租户被拒' {
  if ((StatusOf { Invoke-RestMethod "$base/api/auth/me" -Headers (TenantHeaders $code $t1) }) -ne 401) { throw '应为 401' }
}
Step '新租户令牌在默认租户被拒' {
  if ((StatusOf { Invoke-RestMethod "$base/api/auth/me" -Headers @{ Authorization = "Bearer $t2" } }) -ne 401) { throw '应为 401' }
}
Step '默认租户账号无法登录新租户' {
  $r = Invoke-RestMethod "$base/api/auth/login" -Method Post -Headers (TenantHeaders $code) -ContentType 'application/json' `
    -Body (@{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json)
  if ($r.code -eq 0) { throw '不应登录成功' }
}
Step '未知租户返回 404' {
  if ((StatusOf { Invoke-RestMethod "$base/api/public/site-config" -Headers (TenantHeaders 'no-such-tenant') }) -ne 404) { throw '应为 404' }
}

# 4. 上传文件隔离
Step '上传文件仅本租户可见' {
  $png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==')
  $tmp = Join-Path $env:TEMP "smoke-$code.png"
  [IO.File]::WriteAllBytes($tmp, $png)
  $curl = "$env:SystemRoot\System32\curl.exe"
  $up = & $curl -s -H "X-Tenant-Id: $code" -H "Authorization: Bearer $t2" -F "file=@$tmp" "$base/api/upload" | ConvertFrom-Json
  $own = & $curl -s -o NUL -w "%{http_code}" -H "X-Tenant-Id: $code" "$base$($up.data.url)"
  $other = & $curl -s -o NUL -w "%{http_code}" "$base$($up.data.url)"
  Remove-Item $tmp -ErrorAction SilentlyContinue
  if ($own -ne '200' -or $other -ne '404') { throw "本租户 $own,默认租户 $other" }
}

# 5. 停用 / 恢复
Step '停用后访问返回 403' {
  Invoke-RestMethod "$base/api/platform/tenants/$code/status" -Method Put -Headers $ph -ContentType 'application/json' -Body '{"status":"SUSPENDED"}' | Out-Null
  if ((StatusOf { Invoke-RestMethod "$base/api/public/site-config" -Headers (TenantHeaders $code) }) -ne 403) { throw '应为 403' }
  Invoke-RestMethod "$base/api/platform/tenants/$code/status" -Method Put -Headers $ph -ContentType 'application/json' -Body '{"status":"ACTIVE"}' | Out-Null
}

# 6. 注销(删库删文件)
Step '注销需二次确认' {
  $r = Invoke-RestMethod "$base/api/platform/tenants/${code}?confirm=wrong&dropData=true" -Method Delete -Headers $ph
  if ($r.code -eq 0) { throw '确认码错误时不应成功' }
}
Step '注销并删除数据' {
  $r = Invoke-RestMethod "$base/api/platform/tenants/${code}?confirm=${code}&dropData=true" -Method Delete -Headers $ph
  if ($r.code -ne 0) { throw "注销失败: $($r.message)" }
  if ((StatusOf { Invoke-RestMethod "$base/api/public/site-config" -Headers (TenantHeaders $code) }) -ne 404) { throw '注销后应为 404' }
}

Write-Host "===== PASS ($($pass.Count)) ====="
$pass | ForEach-Object { Write-Host "  OK  $_" }
if ($fail.Count -gt 0) {
  Write-Host "===== FAIL ($($fail.Count)) ====="
  $fail | ForEach-Object { Write-Host "  XX  $_" }
  exit 1
}
Write-Host "ALL GREEN"
