# 提交并推送当前改动到 GitHub。
#
# 用法：
#   .\scripts\push.ps1 "feat: 你的提交说明"
#
# 说明：
#   - 自动使用 Git for Windows 的 git（若不在 PATH 中）。
#   - 暂存所有改动、提交、并推送到 origin/main。
#   - 若远端有新提交，会先拉取（rebase）再推送。
#   - 建议同时在 CHANGELOG.md 中补一条记录。

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Message
)

$ErrorActionPreference = "Stop"

# 定位 git：优先 PATH，其次 Git for Windows 默认安装路径。
$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    $candidates = @(
        "C:\Program Files\Git\cmd\git.exe",
        "C:\Program Files (x86)\Git\cmd\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\cmd\git.exe"
    )
    $git = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $git) {
    throw "找不到 git。请先安装 Git for Windows。"
}

Write-Host "使用 git: $git"
& $git add -A
& $git commit -m $Message
& $git pull --rebase origin main
& $git push origin main
Write-Host "已推送：$Message"
