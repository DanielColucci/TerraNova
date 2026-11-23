# Limpia archivos generados antes de subir a git:  .\scripts\limpiar-proyecto.ps1 [-Git]
param([switch]$Git)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
foreach ($d in "target", ".idea", ".vscode", "out", "build") {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force; Write-Host "eliminada: $d" }
}
Get-ChildItem -Recurse -Force -Include *.iml,.DS_Store -File -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch "\\\.git\\" } | Remove-Item -Force
if ($Git -and (Test-Path ".git")) { git rm -r --cached --ignore-unmatch -q target .idea }
Write-Host "Listo."
