$endpoints = @(
    @{ Name = "Trang chu (Root)"; Url = "http://localhost:8080/Tourma/" },
    @{ Name = "My Tournaments (Default)"; Url = "http://localhost:8080/Tourma/my-tournaments" },
    @{ Name = "My Tournaments (IN_PROGRESS)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=ALL&status=IN_PROGRESS" },
    @{ Name = "My Tournaments (COMPLETED)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=ALL&status=COMPLETED" },
    @{ Name = "My Tournaments (STANDALONE)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=STANDALONE" },
    @{ Name = "My Tournaments (SERIES)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=SERIES" },
    @{ Name = "My Tournaments (Scope S_43fed6c9)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=SERIES&seriesId=S_43fed6c9" },
    @{ Name = "My Tournaments (Scope S_5d2002f6)"; Url = "http://localhost:8080/Tourma/my-tournaments?kind=SERIES&seriesId=S_5d2002f6" },
    @{ Name = "My Tournaments (Search CC14)"; Url = "http://localhost:8080/Tourma/my-tournaments?search=CC14" },
    @{ Name = "My Series (Danh sach Series)"; Url = "http://localhost:8080/Tourma/my-series" },
    @{ Name = "Tao giai dau (Form)"; Url = "http://localhost:8080/Tourma/create-tournament" },
    @{ Name = "Tao Series (Form)"; Url = "http://localhost:8080/Tourma/create-series" },
    @{ Name = "Series Dashboard (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/dashboard?id=S_43fed6c9" },
    @{ Name = "Series Dashboard (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling/dashboard?id=S_5d2002f6" },
    @{ Name = "Series Dashboard Partners (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/dashboard?id=S_43fed6c9&tab=partners" },
    @{ Name = "Series Dashboard Tourneys (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/dashboard?id=S_43fed6c9&tab=tournaments" },
    @{ Name = "Rolling Standings (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/standings?seriesId=S_43fed6c9" },
    @{ Name = "Rolling Standings (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling/standings?seriesId=S_5d2002f6" },
    @{ Name = "Rolling HOF (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling-hof?seriesId=S_43fed6c9" },
    @{ Name = "Rolling HOF (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling-hof?seriesId=S_5d2002f6" },
    @{ Name = "Rolling Point Config (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/point-config?id=S_43fed6c9" },
    @{ Name = "Rolling Point Config (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling/point-config?id=S_5d2002f6" },
    @{ Name = "Rolling Team List (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/team-list?id=S_43fed6c9" },
    @{ Name = "Rolling Team List (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling/team-list?id=S_5d2002f6" },
    @{ Name = "Rolling Tournament List (S_43fed6c9)"; Url = "http://localhost:8080/Tourma/rolling/tournament-list?id=S_43fed6c9" },
    @{ Name = "Rolling Tournament List (S_5d2002f6)"; Url = "http://localhost:8080/Tourma/rolling/tournament-list?id=S_5d2002f6" },
    @{ Name = "Rolling Tournament Teams"; Url = "http://localhost:8080/Tourma/rolling/tournament-teams?id=TOURNEY_1794768923723" },
    @{ Name = "Team Profile (S_43 Shane)"; Url = "http://localhost:8080/Tourma/team-profile?seriesId=S_43fed6c9&teamName=Shane+Van+Boening" },
    @{ Name = "Team Profile (S_5d Shane)"; Url = "http://localhost:8080/Tourma/team-profile?seriesId=S_5d2002f6&teamName=Shane+Van+Boening" },
    @{ Name = "Team Profile (S_5d Filler)"; Url = "http://localhost:8080/Tourma/team-profile?seriesId=S_5d2002f6&teamName=Joshua+Filler" },
    @{ Name = "H2H API (Shane vs Filler in Tourney)"; Url = "http://localhost:8080/Tourma/api/h2h?id=TOURNEY_1794768923723&first=Shane+Van+Boening&second=Joshua+Filler" },
    @{ Name = "H2H API (Shane vs David Alcaide)"; Url = "http://localhost:8080/Tourma/api/h2h?id=TOURNEY_1792429956596&first=Shane+Van+Boening&second=David+Alcaide" },
    @{ Name = "Single Elimination"; Url = "http://localhost:8080/Tourma/single-elimination?id=TOURNEY_1792429956596" },
    @{ Name = "Double Elimination"; Url = "http://localhost:8080/Tourma/double-elimination?id=TOURNEY_1794768923723&stage=2" },
    @{ Name = "Round Robin"; Url = "http://localhost:8080/Tourma/round-robin?id=T_fb014660" },
    @{ Name = "Swiss Stage"; Url = "http://localhost:8080/Tourma/swiss-stage?id=TOURNEY_1792511195320" },
    @{ Name = "GSL Stage"; Url = "http://localhost:8080/Tourma/gsl?id=TOURNEY_1792431759476" },
    @{ Name = "Setup Copy Sources (Stand-alone Tourney)"; Url = "http://localhost:8080/Tourma/setup/copy-sources?id=TOURNEY_1792429956596" },
    @{ Name = "Setup Copy Sources (Series Tourney BB14)"; Url = "http://localhost:8080/Tourma/setup/copy-sources?id=TOURNEY_1794768923723" }
)

$results = @()
foreach ($ep in $endpoints) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $res = Invoke-WebRequest -Uri $ep.Url -UseBasicParsing -TimeoutSec 20
        $sw.Stop()
        $sizeKb = [Math]::Round(($res.RawContentLength / 1024), 1)
        $rating = if ($sw.ElapsedMilliseconds -lt 300) { "EXCELLENT" } elseif ($sw.ElapsedMilliseconds -lt 1000) { "GOOD" } elseif ($sw.ElapsedMilliseconds -lt 2500) { "ACCEPTABLE" } else { "SLOW" }
        $results += [PSCustomObject]@{
            Screen = $ep.Name
            Status = $res.StatusCode
            TimeMs = $sw.ElapsedMilliseconds
            SizeKb = $sizeKb
            Rating = $rating
        }
    } catch {
        $sw.Stop()
        $status = if ($_.Exception.Response) { $_.Exception.Response.StatusCode.value__ } else { "ERR" }
        $results += [PSCustomObject]@{
            Screen = $ep.Name
            Status = $status
            TimeMs = $sw.ElapsedMilliseconds
            SizeKb = 0
            Rating = "ERROR"
        }
    }
}

$results | Format-Table -AutoSize

