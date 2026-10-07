param([int]$Port = 8765)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$projectSources = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -File -Filter '*.java' | Select-Object -ExpandProperty FullName
& javac -d (Join-Path $PSScriptRoot 'bin') $projectSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed. Check that a JDK 16 or newer is installed.' }
Write-Host "Open http://127.0.0.1:$Port in your browser."
& java -cp (Join-Path $PSScriptRoot 'bin') search.engine.web.LocalServer $Port
