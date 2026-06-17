<#
  Reads eval-results.json (produced by run-search-eval.ps1) and emits a compact
  eval-results.md (top-5 per query) + prints summary metrics. No re-querying.
  ASCII-only.
#>
[CmdletBinding()]
param(
    [string]$JsonPath,
    [string]$MdPath,
    [int]$Top = 5
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
if (-not $JsonPath) { $JsonPath = Join-Path $PSScriptRoot "eval-results.json" }
if (-not $MdPath)   { $MdPath   = Join-Path $PSScriptRoot "eval-results.md" }

$data = Get-Content $JsonPath -Raw -Encoding utf8 | ConvertFrom-Json
$rs = $data.results

$n = $rs.Count
$zero = @($rs | Where-Object { $_.zeroResult }).Count
$err  = @($rs | Where-Object { $_.status -ne "OK" }).Count
$latSum = 0; foreach ($r in $rs) { $latSum += [int]$r.latencyMs }
$avgLat = [math]::Round($latSum / [double]$n, 1)
$kwLats = @(); foreach ($r in $rs) { if ($r.query -and $r.query.Trim()) { $kwLats += [int]$r.latencyMs } }
$kwLatsSorted = $kwLats | Sort-Object
$p95idx = [int][math]::Floor($kwLatsSorted.Count * 0.95)
if ($p95idx -ge $kwLatsSorted.Count) { $p95idx = $kwLatsSorted.Count - 1 }
$p95 = $kwLatsSorted[$p95idx]
$kwAvg = [math]::Round((($kwLats | Measure-Object -Average).Average), 1)

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# Search eval results (raw, top-$Top)")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- endpoint: ``$($data.meta.endpoint)``  order=$($data.meta.order)  topK=$($data.meta.topK)  queries=$n")
[void]$sb.AppendLine("- zero-result: $zero / $n   error: $err")
[void]$sb.AppendLine("- latency (all): avg ${avgLat}ms")
[void]$sb.AppendLine("- latency (keyword only): avg ${kwAvg}ms  P95 ${p95}ms")
[void]$sb.AppendLine("")

# zero-result list
[void]$sb.AppendLine("## Zero-result queries")
foreach ($r in ($rs | Where-Object { $_.zeroResult })) {
    [void]$sb.AppendLine("- $($r.id) [$($r.form)] ``$($r.query)`` (total=$($r.totalElements))")
}
[void]$sb.AppendLine("")

foreach ($r in $rs) {
    [void]$sb.AppendLine("## $($r.id)  ``$($r.query)``")
    [void]$sb.AppendLine("intent=$($r.intent) / form=$($r.form) / total=$($r.totalElements) / lat=$($r.latencyMs)ms / zero=$($r.zeroResult)")
    if ($r.status -ne "OK") { [void]$sb.AppendLine("**ERROR**: $($r.error)") }
    $cnt = 0
    if ($r.topResults.Count -gt 0) {
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("| # | type | category | score | title |")
        [void]$sb.AppendLine("|---|------|----------|-------|-------|")
        foreach ($t in $r.topResults) {
            if ($cnt -ge $Top) { break }
            $cnt++
            $safeTitle = ([string]$t.title) -replace '\|', '/'
            if ($safeTitle.Length -gt 80) { $safeTitle = $safeTitle.Substring(0,80) + "..." }
            [void]$sb.AppendLine("| $($t.rank) | $($t.type) | $($t.category) | $($t.score) | $safeTitle |")
        }
    }
    [void]$sb.AppendLine("")
}
$sb.ToString() | Out-File $MdPath -Encoding utf8
Write-Host "MD -> $MdPath"
Write-Host "queries=$n zero=$zero err=$err avgLat=${avgLat}ms kwAvg=${kwAvg}ms kwP95=${p95}ms"
