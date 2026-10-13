param([string]$Path)
[xml]$doc = Get-Content $Path
$nodes = $doc.SelectNodes('//*[@clickable="true" or @text!=""]')
foreach ($n in $nodes) {
    $text = $n.text
    if ($text.Length -gt 60) { $text = $text.Substring(0, 60) }
    $click = if ($n.clickable -eq 'true') { 'CLICKABLE' } else { 'static' }
    Write-Output ($click + ' | ' + $text + ' | ' + $n.bounds)
}
