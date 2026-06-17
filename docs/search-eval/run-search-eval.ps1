<#
.SYNOPSIS
  PostgreSQL unified search (/api/v2/search/detail) quality eval runner.
  Fires each query from query-bank.json at the running app endpoint and dumps
  top-K results + latency + zero-result into eval-results.json / eval-results.md.

.NOTES
  - Read-only. Never calls /sync.
  - App must be running at BaseUrl.
  - ASCII-only on purpose (PS 5.1 mis-decodes non-BOM UTF-8 script literals).

.EXAMPLE
  ./run-search-eval.ps1 -BankPath .\query-bank.json -OutDir .
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$BankPath,
    [string]$OutDir,
    [int]$TopK = 10,
    [string]$Order = "relevance"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $BankPath) { $BankPath = Join-Path $PSScriptRoot "query-bank.json" }
if (-not $OutDir)   { $OutDir   = $PSScriptRoot }
if (-not (Test-Path $BankPath)) { throw "query-bank.json not found: $BankPath" }

$bank = Get-Content $BankPath -Raw -Encoding utf8 | ConvertFrom-Json
$endpoint = "$BaseUrl/api/v2/search/detail"

Write-Host "Endpoint: $endpoint  (order=$Order, topK=$TopK)" -ForegroundColor Cyan

# PS 5.1 Invoke-RestMethod mis-decodes UTF-8 JSON (no charset header) as ISO-8859-1.
# Fetch raw bytes via Invoke-WebRequest and decode as UTF-8 explicitly.
function Invoke-JsonUtf8([string]$Uri) {
    $wr = Invoke-WebRequest -Uri $Uri -Method Get -TimeoutSec 30 -UseBasicParsing
    $bytes = $wr.RawContentStream.ToArray()
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    return ($text | ConvertFrom-Json)
}

try {
    Invoke-JsonUtf8 "$endpoint`?keyword=&page=0&size=1" | Out-Null
} catch {
    throw "Health check failed at $BaseUrl. Is bootRun up? Cause: $($_.Exception.Message)"
}

$results = New-Object System.Collections.ArrayList

foreach ($q in $bank.queries) {
    $kw = [uri]::EscapeDataString([string]$q.query)
    $url = "$endpoint`?keyword=$kw&order=$Order&page=0&size=$TopK"

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = "OK"; $err = $null; $total = 0
    $top = New-Object System.Collections.ArrayList
    try {
        $resp = Invoke-JsonUtf8 $url
        $sw.Stop()
        $content = $resp.data.content
        $total = [int]$resp.data.totalElements
        $rank = 0
        foreach ($item in $content) {
            $rank++
            $title = ([string]$item.highlightedTitle) -replace '</?em>', ''
            $snippet = ([string]$item.highlightedContent) -replace '</?em>', ''
            if ($snippet.Length -gt 120) { $snippet = $snippet.Substring(0, 120) }
            [void]$top.Add([ordered]@{
                rank     = $rank
                docId    = $item.id
                type     = $item.type
                category = $item.category
                score    = [math]::Round([double]$item.score, 5)
                date     = $item.date
                title    = $title
                snippet  = $snippet
            })
        }
    } catch {
        $sw.Stop()
        $status = "ERROR"
        $err = $_.Exception.Message
    }

    $zero = ($top.Count -eq 0)
    [void]$results.Add([ordered]@{
        id            = $q.id
        query         = $q.query
        intent        = $q.intent
        form          = $q.form
        expectType    = $q.expectType
        note          = $q.note
        status        = $status
        error         = $err
        latencyMs     = [int]$sw.ElapsedMilliseconds
        totalElements = $total
        zeroResult    = $zero
        topResults    = $top
    })

    $flag = if ($status -ne "OK") { "ERR" } elseif ($zero) { "ZERO" } else { "ok" }
    Write-Host ("  {0} [{1,4}] total={2,-5} lat={3}ms  {4}" -f $q.id, $flag, $total, $sw.ElapsedMilliseconds, $q.query)
}

$jsonOut = Join-Path $OutDir "eval-results.json"
$payload = [ordered]@{
    meta = [ordered]@{
        endpoint   = $endpoint
        order      = $Order
        topK       = $TopK
        queryCount = $results.Count
    }
    results = $results
}
$payload | ConvertTo-Json -Depth 8 | Out-File $jsonOut -Encoding utf8
Write-Host "`nJSON  -> $jsonOut" -ForegroundColor Green

$mdOut = Join-Path $OutDir "eval-results.md"
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# Search eval results (raw)")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- endpoint: ``$endpoint``")
[void]$sb.AppendLine("- order: $Order / topK: $TopK / queries: $($results.Count)")
$zeroCount = @($results | Where-Object { $_.zeroResult }).Count
$errCount = @($results | Where-Object { $_.status -ne "OK" }).Count
$latSum = 0; foreach ($x in $results) { $latSum += [int]$x.latencyMs }
$avgLat = [math]::Round($latSum / [double]$results.Count, 1)
[void]$sb.AppendLine("- zero-result: $zeroCount / error: $errCount / avg latency: ${avgLat}ms")
[void]$sb.AppendLine("")

foreach ($r in $results) {
    [void]$sb.AppendLine("## $($r.id)  ``$($r.query)``")
    [void]$sb.AppendLine("intent=$($r.intent) / form=$($r.form) / total=$($r.totalElements) / lat=$($r.latencyMs)ms / zero=$($r.zeroResult)")
    if ($r.status -ne "OK") { [void]$sb.AppendLine("**ERROR**: $($r.error)") }
    if ($r.topResults.Count -gt 0) {
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("| # | type | category | score | title |")
        [void]$sb.AppendLine("|---|------|----------|-------|-------|")
        foreach ($t in $r.topResults) {
            $safeTitle = ([string]$t.title) -replace '\|', '\|'
            [void]$sb.AppendLine("| $($t.rank) | $($t.type) | $($t.category) | $($t.score) | $safeTitle |")
        }
    }
    [void]$sb.AppendLine("")
}
$sb.ToString() | Out-File $mdOut -Encoding utf8
Write-Host "MD    -> $mdOut" -ForegroundColor Green
Write-Host "`nSummary: zero=$zeroCount err=$errCount avgLat=${avgLat}ms" -ForegroundColor Yellow
