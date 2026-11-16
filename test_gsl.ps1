$sw = [System.Diagnostics.Stopwatch]::StartNew()
try {
    $res = Invoke-WebRequest -Uri 'http://localhost:8080/Tourma/gsl?id=TOURNEY_1792431759476' -UseBasicParsing -TimeoutSec 20
    $sw.Stop()
    Write-Output ("Status: $($res.StatusCode) Time: $($sw.ElapsedMilliseconds)ms Length: $($res.RawContentLength)")
} catch {
    $sw.Stop()
    Write-Output ("Error: $($_.Exception.Message) Time: $($sw.ElapsedMilliseconds)ms")
}

