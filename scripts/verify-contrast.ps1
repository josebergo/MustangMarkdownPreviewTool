# Check reader CSS text colors; normal text and code tokens must have at least 4.5:1 contrast against their backgrounds.
$ErrorActionPreference = 'Stop'
function Get-Luminance([string]$hex) {
    $channels = @(0,2,4) | ForEach-Object {
        $channel = [Convert]::ToInt32($hex.Substring($_,2),16) / 255.0
        if ($channel -le 0.04045) { $channel / 12.92 } else { [Math]::Pow(($channel + 0.055) / 1.055,2.4) }
    }
    0.2126 * $channels[0] + 0.7152 * $channels[1] + 0.0722 * $channels[2]
}
$pairs = @(
    @('Body text','E6E6E6','121212'), @('Secondary text','B3B3B3','121212'),
    @('Links','B69CFF','121212'), @('Quotes','C9C5D3','1B1921'),
    @('Inline code','DED2FF','24212D'), @('Default code','E6E6E6','1D1B23'),
    @('Keywords','C8B6FF','1D1B23'), @('Strings','A9D6A0','1D1B23'),
    @('Numbers','F3C58C','1D1B23'), @('Functions','9ED3FA','1D1B23'),
    @('Code comments','B0A9BA','1D1B23'), @('Code variables','EDBDD7','1D1B23'),
    @('Table headers','E9DDFF','25212E')
)
$css = Get-Content (Join-Path $PSScriptRoot '../app/src/main/assets/reader/reader.css') -Raw
foreach ($pair in $pairs) {
    foreach ($hex in $pair[1..2]) { if ($css -notmatch "(?i)#$hex") { throw "CSS is missing color $hex. Update the verification values." } }
    $foreground = Get-Luminance $pair[1]; $background = Get-Luminance $pair[2]
    $ratio = ([Math]::Max($foreground,$background)+0.05)/([Math]::Min($foreground,$background)+0.05)
    if ($ratio -lt 4.5) { throw "$($pair[0]) contrast check failed: $ratio" }
    '{0}: {1:N2}:1 PASS' -f $pair[0],$ratio
}
