# 忘记 MySQL root 密码时，用这个脚本为本项目建库建账号。需要"以管理员身份运行"的 PowerShell。
# 原理：让 MySQL57 服务启动时执行一次 init-file（以超级权限运行建库脚本），完成后恢复配置并重启。
# 不修改 root 密码，不开启免密登录（skip-grant-tables），my.ini 先备份、最后原样还原。
$ErrorActionPreference = 'Stop'

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw '请右键 PowerShell，选择"以管理员身份运行"后再执行本脚本。'
}

$service  = 'MySQL57'
$mysqlDir = 'C:\ProgramData\MySQL\MySQL Server 5.7'
$ini      = Join-Path $mysqlDir 'my.ini'
$backup   = Join-Path $mysqlDir 'my.ini.cga-backup'
$initFile = Join-Path $mysqlDir 'cga-init.sql'
$source   = Join-Path $PSScriptRoot 'create-app-user.local.sql'
$localYml = Join-Path $PSScriptRoot '..\src\main\resources\application-local.yml'
$mysqlExe = 'C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe'

if (-not (Test-Path $source)) { throw "找不到 $source" }
if (Test-Path $backup) { throw "发现上次留下的备份 $backup，请先确认 my.ini 正常后删除该备份再运行。" }

# init-file 要求一行一条语句、不能有注释
Get-Content $source -Encoding UTF8 |
    Where-Object { $_.Trim() -ne '' -and -not $_.TrimStart().StartsWith('--') } |
    Set-Content $initFile -Encoding ascii

Copy-Item $ini $backup
try {
    $lines = [IO.File]::ReadAllLines($ini)
    $idx = [Array]::FindIndex($lines, [Predicate[string]]{ param($l) $l.Trim() -eq '[mysqld]' })
    if ($idx -lt 0) { throw 'my.ini 中没有 [mysqld] 段' }
    $initPath = $initFile.Replace('\', '/')
    $patched = @($lines[0..$idx]) + "init-file=`"$initPath`"" + @($lines[($idx + 1)..($lines.Length - 1)])
    [IO.File]::WriteAllLines($ini, $patched, (New-Object Text.UTF8Encoding($false)))

    Write-Host '重启 MySQL57 并执行建库脚本...'
    Restart-Service $service -Force
    Start-Sleep -Seconds 5
}
finally {
    # 无论成功与否都还原配置、删除含密码的临时文件
    Copy-Item $backup $ini -Force
    Remove-Item $backup -Force
    Remove-Item $initFile -Force -ErrorAction SilentlyContinue
    Write-Host '已还原 my.ini，再次重启 MySQL57...'
    Restart-Service $service -Force
    Start-Sleep -Seconds 3
}

# 用新账号验证
$pw = (Select-String -Path $localYml -Pattern '^\s*password:\s*(.+)$').Matches[0].Groups[1].Value.Trim()
$env:MYSQL_PWD = $pw
try {
    $out = & $mysqlExe -u cga_app -N -e "SHOW DATABASES LIKE 'cga%';" 2>&1
} finally {
    Remove-Item Env:MYSQL_PWD
}
if ($LASTEXITCODE -eq 0 -and ($out -join ' ') -match 'cga_test') {
    Write-Host "完成：cga_app 账号可用，已有数据库 $($out -join ', ')" -ForegroundColor Green
} else {
    Write-Host "验证失败：$out" -ForegroundColor Red
    exit 1
}
